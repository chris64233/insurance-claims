package com.example.insuranceclaims.claim;

public class InvalidSettleAmountException extends RuntimeException {

    public InvalidSettleAmountException(String message) {
        super(message);
    }
}
