package com.example.insuranceclaims.claim;

public class ClaimPaymentConflictException extends ClaimStateConflictException {

    public ClaimPaymentConflictException(String message) {
        super(message);
    }
}
