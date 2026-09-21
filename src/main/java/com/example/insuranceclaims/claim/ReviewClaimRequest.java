package com.example.insuranceclaims.claim;

public record ReviewClaimRequest(
        String action,
        String handler,
        String rejectReason
) {
}
