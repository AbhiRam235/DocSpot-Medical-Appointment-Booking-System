package com.docspot.repository;

import com.docspot.entity.DoctorAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;

@Repository
public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailability, Long> {

    // All active windows for a doctor, across all days — used when displaying
    // the full weekly schedule in the doctor's own availability screen.
    List<DoctorAvailability> findByDoctor_IdAndActiveTrue(Long doctorId);

    // Active windows for one specific day — a doctor can have MORE THAN ONE
    // window on the same day (e.g. a morning window + a separate evening
    // window), so this returns a List, not an Optional.
    List<DoctorAvailability> findByDoctor_IdAndDayOfWeekAndActiveTrue(Long doctorId, DayOfWeek dayOfWeek);
}
