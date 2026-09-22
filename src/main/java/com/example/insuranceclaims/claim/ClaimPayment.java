package com.example.insuranceclaims.claim;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "claim_payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_claim_payments_voucher_no", columnNames = "voucher_no"),
                @UniqueConstraint(name = "uk_claim_payments_claim_id", columnNames = "claim_id")
        })
public class ClaimPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private ClaimReport claim;

    @Column(name = "paid_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "paid_by", nullable = false, length = 30)
    private String paidBy;

    @Column(name = "voucher_no", nullable = false, length = 40)
    private String voucherNo;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    public Long getId() {
        return id;
    }

    public ClaimReport getClaim() {
        return claim;
    }

    public void setClaim(ClaimReport claim) {
        this.claim = claim;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public String getPaidBy() {
        return paidBy;
    }

    public void setPaidBy(String paidBy) {
        this.paidBy = paidBy;
    }

    public String getVoucherNo() {
        return voucherNo;
    }

    public void setVoucherNo(String voucherNo) {
        this.voucherNo = voucherNo;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }
}
