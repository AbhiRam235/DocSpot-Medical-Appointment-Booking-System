package com.docspot.entity;

import com.docspot.enums.DoctorStatus;
import com.docspot.enums.Gender;
import com.docspot.enums.Speciality;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "doctors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Speciality speciality;

    @Column(nullable = false)
    private String qualification;

    @Column(nullable = false)
    private Integer experienceYears;

    @Column(nullable = false)
    private Double consultationFee;

    @Column(nullable = false)
    private String city;

    @Column(columnDefinition = "TEXT")
    private String bio;

    // Not truly verified — admin reviews documents and approves manually
    @Column(nullable = false)
    private String licenseNumber;

    private String profilePhotoPath;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DoctorStatus status = DoctorStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    private String adminNote;

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DoctorDocument> documents;

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DoctorAvailability> availabilities;
}