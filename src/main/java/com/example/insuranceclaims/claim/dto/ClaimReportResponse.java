package com.example.insuranceclaims.claim.dto;

import com.example.insuranceclaims.claim.ClaimReport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ClaimReportResponse(
        Long id,
        String claimNo,
        String policyNumber,
        String insuredName,
        String contactInfo,
        String accidentType,
        LocalDate accidentDate,
        BigDecimal claimAmount,
        String description,
        String status,
        LocalDateTime createdAt
) {

    public static ClaimReportResponse from(ClaimReport report) {
        return new ClaimReportResponse(
                report.getId(),
                report.getClaimNo(),
                report.getPolicyNumber(),
                report.getInsuredName(),
                report.getContactInfo(),
                report.getAccidentType(),
                report.getAccidentDate(),
                report.getClaimAmount(),
                report.getDescription(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }
}
