package com.docspot.repository;

import com.docspot.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {
    Optional<Patient> findByUser_Id(Long userId);
    Optional<Patient> findByUser_Email(String email);
    Page<Patient> findByUser_DeletedFalse(Pageable pageable);
}
