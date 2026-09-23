package com.example.insuranceclaims.claim;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "claim_payment_reversals")
public class ClaimPaymentReversal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long claimId;

    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false, length = 30)
    private String reversedBy;

    @Column(nullable = false, length = 200)
    private String reverseReason;

    @Column(nullable = false, unique = true, length = 40)
    private String reversalVoucherNo;

    @Column(nullable = false)
    private LocalDateTime reversedAt;

    public Long getId() {
        return id;
    }

    public Long getClaimId() {
        return claimId;
    }

    public void setClaimId(Long claimId) {
        this.claimId = claimId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public String getReversedBy() {
        return reversedBy;
    }

    public void setReversedBy(String reversedBy) {
        this.reversedBy = reversedBy;
    }

    public String getReverseReason() {
        return reverseReason;
    }

    public void setReverseReason(String reverseReason) {
        this.reverseReason = reverseReason;
    }

    public String getReversalVoucherNo() {
        return reversalVoucherNo;
    }

    public void setReversalVoucherNo(String reversalVoucherNo) {
        this.reversalVoucherNo = reversalVoucherNo;
    }

    public LocalDateTime getReversedAt() {
        return reversedAt;
    }

    public void setReversedAt(LocalDateTime reversedAt) {
        this.reversedAt = reversedAt;
    }
}
