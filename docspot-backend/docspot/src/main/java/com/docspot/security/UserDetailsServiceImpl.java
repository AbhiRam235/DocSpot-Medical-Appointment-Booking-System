package com.docspot.security;

import com.docspot.entity.User;
import com.docspot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Spring Security calls this on every authenticated request.
     * Username here is the email (that's what we store as JWT subject).
     *
     * We:
     *  1. Look up by email, excluding soft-deleted accounts
     *  2. Build a GrantedAuthority with ROLE_ prefix (e.g. ROLE_DOCTOR)
     *  3. Return Spring's UserDetails — password checked by DaoAuthenticationProvider on login
     */
    /**
//    What UserDetails actually requires
//    It's an interface with a small, fixed contract:
//        String getUsername();
//        String getPassword();
//        Collection<? extends GrantedAuthority> getAuthorities();
//        boolean isAccountNonExpired();
//        boolean isAccountNonLocked();
//        boolean isCredentialsNonExpired();
//        boolean isEnabled(); **/
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> {
                    log.warn("No active user found with email: {}", email);
                    return new UsernameNotFoundException("No active account found with email: " + email);
                });

        // Spring Security requires ROLE_ prefix for hasRole() checks in SecurityConfig
        SimpleGrantedAuthority authority =
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name());

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                List.of(authority)
        );
    }
}