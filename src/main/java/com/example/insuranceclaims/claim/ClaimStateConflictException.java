package com.example.insuranceclaims.claim;

public class ClaimStateConflictException extends RuntimeException {

    public ClaimStateConflictException(String message) {
        super(message);
    }
}
