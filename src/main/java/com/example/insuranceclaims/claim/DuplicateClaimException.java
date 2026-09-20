package com.example.insuranceclaims.claim;

public class DuplicateClaimException extends RuntimeException {

    public DuplicateClaimException(String message) {
        super(message);
    }
}
