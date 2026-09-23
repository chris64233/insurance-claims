package com.example.insuranceclaims.claim;

import java.time.LocalDateTime;

public record ClaimPaymentReversalResponse(
        Long id,
        Long paymentId,
        String reversedBy,
        String reverseReason,
        String reversalVoucherNo,
        LocalDateTime reversedAt
) {
    public static ClaimPaymentReversalResponse from(ClaimPaymentReversal reversal) {
        return new ClaimPaymentReversalResponse(
                reversal.getId(),
                reversal.getPaymentId(),
                reversal.getReversedBy(),
                reversal.getReverseReason(),
                reversal.getReversalVoucherNo(),
                reversal.getReversedAt()
        );
    }
}
