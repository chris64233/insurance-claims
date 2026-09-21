package com.example.insuranceclaims.claim;

public class InvalidReviewRequestException extends RuntimeException {

    public InvalidReviewRequestException(String message) {
        super(message);
    }
}
