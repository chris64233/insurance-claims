package com.example.insuranceclaims.claim;

public class ClaimAlreadyProcessedException extends RuntimeException {

    public ClaimAlreadyProcessedException(String message) {
        super(message);
    }
}
