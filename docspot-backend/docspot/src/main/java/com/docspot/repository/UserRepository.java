package com.docspot.repository;

import com.docspot.entity.User;
import com.docspot.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailAndDeletedFalse(String email);
    boolean existsByEmail(String email);
    long countByRole(Role role);
}
