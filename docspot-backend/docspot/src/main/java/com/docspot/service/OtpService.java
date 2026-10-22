package com.docspot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    @Value("${otp.ttl-minutes:1}")
    private long ttlMinutes;

    @Value("${otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${otp.resend-cooldown-seconds:60}")
    private long cooldownSeconds;

    @Value("${otp.hmac-secret}")
    private String hmacSecret;

    private String otpKey(String email)      { return "otp:reset:" + email; }
    private String attemptsKey(String email) { return "otp:attempts:" + email; }
    private String cooldownKey(String email) { return "otp:cooldown:" + email; }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }

    /**
     * Issues a new OTP. Uses atomic SETNX for rate-limiting/cooldown.
     * Overwrites any previous OTP so only the latest remains valid.
     */
    public Optional<String> issue(String rawEmail) {
        String email = normalize(rawEmail);

        // Atomic rate-limit check: Only one caller can acquire the cooldown slot
        Boolean allowed = redis.opsForValue()
                .setIfAbsent(cooldownKey(email), "1", Duration.ofSeconds(cooldownSeconds));

        if (!Boolean.TRUE.equals(allowed)) {
            return Optional.empty(); // Too soon to resend
        }

        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        Duration ttl = Duration.ofMinutes(ttlMinutes);

        // Store keyed HMAC hash (never plain text) and initialize attempt counter
        redis.opsForValue().set(otpKey(email), hash(email, otp), ttl);
        redis.opsForValue().set(attemptsKey(email), "0", ttl);

        return Optional.of(otp);
    }

    /**
     * Verifies the submitted OTP. Counts attempts atomically.
     * Invalidates and blocks further attempts if max attempts are exceeded.
     */
    public boolean verify(String rawEmail, String submittedOtp) {
        String email = normalize(rawEmail);

        String storedHash = redis.opsForValue().get(otpKey(email));
        if (storedHash == null) {
            return false; // Expired or never issued
        }

        // Increment attempts atomically BEFORE comparing
        Long attempts = redis.opsForValue().increment(attemptsKey(email));
        if (attempts != null && attempts > maxAttempts) {
            cleanup(email);
            return false;
        }

        // Constant-time comparison to prevent timing side-channel attacks
        boolean matches = MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                hash(email, submittedOtp).getBytes(StandardCharsets.UTF_8));

        if (!matches) {
            return false;
        }

        // Successful verification: consume/delete the OTP so it cannot be reused
        cleanup(email);
        return true;
    }

    private void cleanup(String email) {
        redis.delete(otpKey(email));
        redis.delete(attemptsKey(email));
    }

    private String hash(String email, String otp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal((email + ":" + otp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new IllegalStateException("OTP hashing failed", e);
        }
    }
}