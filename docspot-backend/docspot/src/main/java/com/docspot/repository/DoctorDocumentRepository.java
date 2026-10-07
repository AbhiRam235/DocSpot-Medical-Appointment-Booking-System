package com.docspot.repository;

import com.docspot.entity.DoctorDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DoctorDocumentRepository extends JpaRepository<DoctorDocument, Long> {
    List<DoctorDocument> findByDoctor_Id(Long doctorId);
}
