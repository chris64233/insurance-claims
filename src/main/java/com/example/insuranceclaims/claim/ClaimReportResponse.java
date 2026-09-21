package com.example.insuranceclaims.claim;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ClaimReportResponse(
        Long id,
        String claimNo,
        String policyNo,
        String insuredName,
        String contactPhone,
        String accidentType,
        LocalDate accidentDate,
        BigDecimal claimAmount,
        String description,
        String status,
        LocalDateTime createdAt,
        String handledBy,
        LocalDateTime handledAt,
        String rejectReason,
        BigDecimal settleAmount,
        String settledBy,
        LocalDateTime settledAt,
        String settleNote
) {
    public static ClaimReportResponse from(ClaimReport report) {
        return new ClaimReportResponse(
                report.getId(),
                report.getClaimNo(),
                report.getPolicyNo(),
                report.getInsuredName(),
                report.getContactPhone(),
                report.getAccidentType(),
                report.getAccidentDate(),
                report.getClaimAmount(),
                report.getDescription(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getHandledBy(),
                report.getHandledAt(),
                report.getRejectReason(),
                report.getSettleAmount(),
                report.getSettledBy(),
                report.getSettledAt(),
                report.getSettleNote()
        );
    }
}
