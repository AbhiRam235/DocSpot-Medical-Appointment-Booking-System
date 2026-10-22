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

    private String otpKey(String email)          { return "otp:reset:" + email; }
    private String attemptsKey(String email)     { return "otp:attempts:" + email; }
    private String cooldownKey(String email)     { return "otp:cooldown:" + email; }
    private String ipRateLimitKey(String ip)     { return "ratelimit:ip:" + ip; }
    private String verifyIpRateLimitKey(String ip) { return "ratelimit:verify:ip:" + ip; }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }

    /**
     * Checks and increments IP-based rate limit for requesting OTPs. Max 10 per minute.
     */
    public boolean allowIpRequest(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return true;
        }
        String key = ipRateLimitKey(clientIp);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, Duration.ofMinutes(1));
        } else if (count != null && count > 10) {
            redis.expire(key, Duration.ofMinutes(15)); // Extend lockout on abuse
            return false;
        }
        return count == null || count <= 10;
    }

    /**
     * Checks and increments IP-based rate limit for verifying OTPs. Max 20 attempts per 5 mins.
     */
    public boolean allowVerificationAttempt(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return true;
        }
        String key = verifyIpRateLimitKey(clientIp);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, Duration.ofMinutes(5));
        } else if (count != null && count > 20) {
            redis.expire(key, Duration.ofMinutes(15)); // Extend lockout on brute force
            return false;
        }
        return count == null || count <= 20;
    }

    /**
     * Issues a new OTP. Uses atomic SETNX for email cooldown rate-limiting.
     */
    public Optional<String> issue(String rawEmail) {
        String email = normalize(rawEmail);

        Boolean allowed = redis.opsForValue()
                .setIfAbsent(cooldownKey(email), "1", Duration.ofSeconds(cooldownSeconds));

        if (!Boolean.TRUE.equals(allowed)) {
            return Optional.empty();
        }

        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        Duration ttl = Duration.ofMinutes(ttlMinutes);

        redis.opsForValue().set(otpKey(email), hash(email, otp), ttl);
        redis.opsForValue().set(attemptsKey(email), "0", ttl);

        return Optional.of(otp);
    }

    /**
     * Verifies the submitted OTP. Counts attempts atomically.
     */
    public boolean verify(String rawEmail, String submittedOtp) {
        String email = normalize(rawEmail);

        String storedHash = redis.opsForValue().get(otpKey(email));
        if (storedHash == null) {
            return false;
        }

        Long attempts = redis.opsForValue().increment(attemptsKey(email));
        if (attempts != null && attempts > maxAttempts) {
            cleanup(email);
            return false;
        }

        boolean matches = MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                hash(email, submittedOtp).getBytes(StandardCharsets.UTF_8));

        if (!matches) {
            return false;
        }

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