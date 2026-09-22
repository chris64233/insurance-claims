package com.example.insuranceclaims.claim;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ClaimPaymentResponse(
        Long id,
        BigDecimal paidAmount,
        String paidBy,
        String voucherNo,
        LocalDateTime paidAt
) {
    public static ClaimPaymentResponse from(ClaimPayment payment) {
        return new ClaimPaymentResponse(
                payment.getId(),
                payment.getPaidAmount(),
                payment.getPaidBy(),
                payment.getVoucherNo(),
                payment.getPaidAt()
        );
    }
}
