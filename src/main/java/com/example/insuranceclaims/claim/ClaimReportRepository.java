package com.example.insuranceclaims.claim;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ClaimReportRepository extends JpaRepository<ClaimReport, Long> {

    boolean existsByPolicyNumberAndAccidentDateAndAccidentType(
            String policyNumber, LocalDate accidentDate, String accidentType);

    boolean existsByClaimNo(String claimNo);

    List<ClaimReport> findAllByOrderByCreatedAtDesc();
}
