package com.docspot.security;

import com.docspot.entity.User;
import com.docspot.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Spring Security calls loadUserByUsername on EVERY authenticated request
 * (the JWT filter) and also on login (DaoAuthenticationProvider).
 *
 * Before: every call = @Transactional (several round trips to remote MySQL)
 *         + 1 query, holding a Hikari connection the whole time.
 * Now:    hot path is served from an in-process Caffeine cache (microseconds,
 *         no network, no DB connection). Only a cache miss touches MySQL, and
 *         it runs as a single autocommit query (no transaction wrapper).
 *
 * Why Caffeine and not Redis for this: it is a per-request lookup, so an
 * in-process cache is the fastest option and the password hash never leaves
 * the JVM. This app runs as a single instance, so there is no cross-instance
 * invalidation problem. If you ever run 2+ instances, move this to Redis or
 * keep the TTL short.
 *
 * IMPORTANT: we cache a plain record, NOT the UserDetails object. Spring's
 * ProviderManager calls eraseCredentials() on the principal after a login,
 * which would null the password inside a shared cached UserDetails and break
 * the next login. A fresh UserDetails is built on every call instead.
 */
@Service
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    private record CachedUser(String email, String passwordHash, String authority) {}

    private final UserRepository userRepository;
    private final Cache<String, CachedUser> userCache;

    public UserDetailsServiceImpl(
            UserRepository userRepository,
            @Value("${app.security.user-cache-ttl-seconds:60}") long ttlSeconds,
            @Value("${app.security.user-cache-max-size:10000}") long maxSize) {
        this.userRepository = userRepository;
        this.userCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .build();
    }

    // NOTE: no @Transactional here on purpose. A transaction would add extra
    // round trips to the remote DB. A single derived query runs fine in autocommit.
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException("Email must not be empty");
        }
        // Caffeine computes at most once per key at a time (no stampede), and an
        // exception from the loader is NOT cached, so unknown emails are never stored.
        CachedUser cached = userCache.get(normalize(email), key -> loadFromDatabase(email));
        return toUserDetails(cached);
    }

    private CachedUser loadFromDatabase(String email) {
        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> {
                    log.warn("No active user found with email: {}", email);
                    return new UsernameNotFoundException("No active account found with email: " + email);
                });
        // Spring Security requires the ROLE_ prefix for hasRole() checks
        return new CachedUser(user.getEmail(), user.getPassword(), "ROLE_" + user.getRole().name());
    }

    private UserDetails toUserDetails(CachedUser c) {
        return new org.springframework.security.core.userdetails.User(
                c.email(),
                c.passwordHash(),
                List.of(new SimpleGrantedAuthority(c.authority()))
        );
    }

    /**
     * Call this whenever a user's password, role, email or deleted flag changes
     * (change password, reset password, role update, soft delete, email change
     * - evict the OLD email too). Otherwise the old data lives until the TTL ends.
     */
    public void evictUser(String email) {
        if (email != null) {
            userCache.invalidate(normalize(email));
        }
    }

    public void evictAll() {
        userCache.invalidateAll();
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}