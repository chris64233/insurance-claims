package com.example.insuranceclaims.claim;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ClaimReportRepository extends JpaRepository<ClaimReport, Long> {

    boolean existsByClaimNo(String claimNo);

    boolean existsByPolicyNoAndAccidentDateAndAccidentType(String policyNo, LocalDate accidentDate, String accidentType);

    List<ClaimReport> findAllByOrderByCreatedAtDesc();
}
