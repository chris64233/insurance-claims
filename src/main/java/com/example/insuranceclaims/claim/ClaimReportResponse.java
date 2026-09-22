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
        BigDecimal finalAmount,
        String settledBy,
        LocalDateTime settledAt,
        String settleRemark,
        ClaimPaymentInfo payment
) {
    public record ClaimPaymentInfo(
            Long id,
            BigDecimal paidAmount,
            String paidBy,
            String voucherNo,
            LocalDateTime paidAt
    ) {
        static ClaimPaymentInfo from(ClaimPayment payment) {
            return new ClaimPaymentInfo(
                    payment.getId(),
                    payment.getPaidAmount(),
                    payment.getPaidBy(),
                    payment.getVoucherNo(),
                    payment.getPaidAt()
            );
        }
    }

    public static ClaimReportResponse from(ClaimReport report) {
        return from(report, null);
    }

    public static ClaimReportResponse from(ClaimReport report, ClaimPayment payment) {
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
                report.getFinalAmount(),
                report.getSettledBy(),
                report.getSettledAt(),
                report.getSettleRemark(),
                payment == null ? null : ClaimPaymentInfo.from(payment)
        );
    }
}
