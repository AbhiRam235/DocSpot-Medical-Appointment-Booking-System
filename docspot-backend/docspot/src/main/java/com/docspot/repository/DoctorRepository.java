package com.docspot.repository;

import com.docspot.entity.Doctor;
import com.docspot.enums.DoctorStatus;
import com.docspot.enums.Gender;
import com.docspot.enums.Speciality;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long>, JpaSpecificationExecutor<Doctor> {

    Optional<Doctor> findByUser_Id(Long userId);
    Optional<Doctor> findByUser_Email(String email);
    Page<Doctor> findByStatus(DoctorStatus status, Pageable pageable);
    Page<Doctor> findByUser_DeletedFalse(Pageable pageable);
    long countByStatus(DoctorStatus status);

    // ── Admin: filter by status / speciality / city ──────────────────────────
    @Query("""
        SELECT d FROM Doctor d JOIN d.user u
        WHERE u.deleted = false
          AND (:status     IS NULL OR d.status     = :status)
          AND (:speciality IS NULL OR d.speciality = :speciality)
          AND (:city       IS NULL OR LOWER(d.city) LIKE LOWER(CONCAT('%', :city, '%')))
    """)
    Page<Doctor> findAllByFilters(
            @Param("status")     DoctorStatus status,
            @Param("speciality") Speciality speciality,
            @Param("city")       String city,
            Pageable pageable
    );

    // ── Patient: search approved doctors with all filters ────────────────────
    @Query("""
        SELECT d FROM Doctor d JOIN d.user u
        WHERE d.status = com.docspot.enums.DoctorStatus.APPROVED
          AND u.deleted = false
          AND (:speciality IS NULL OR d.speciality     = :speciality)
          AND (:gender     IS NULL OR d.gender         = :gender)
          AND (:city       IS NULL OR LOWER(d.city) LIKE LOWER(CONCAT('%', :city, '%')))
          AND (:minExp     IS NULL OR d.experienceYears >= :minExp)
          AND (:maxExp     IS NULL OR d.experienceYears <= :maxExp)
          AND (:minFee     IS NULL OR d.consultationFee >= :minFee)
          AND (:maxFee     IS NULL OR d.consultationFee <= :maxFee)
    """)
    Page<Doctor> searchDoctors(
            @Param("speciality") Speciality speciality,
            @Param("gender")     Gender gender,
            @Param("city")       String city,
            @Param("minExp")     Integer minExp,
            @Param("maxExp")     Integer maxExp,
            @Param("minFee")     Double minFee,
            @Param("maxFee")     Double maxFee,
            Pageable pageable
    );
}
