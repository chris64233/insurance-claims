package com.example.insuranceclaims.claim;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "claim_report",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_claim_report_duplicate",
                columnNames = {"policy_number", "accident_date", "accident_type"}
        )
)
public class ClaimReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_no", nullable = false, unique = true, length = 32)
    private String claimNo;

    @Column(name = "policy_number", nullable = false, length = 32)
    private String policyNumber;

    @Column(name = "insured_name", nullable = false, length = 64)
    private String insuredName;

    @Column(name = "contact_info", nullable = false, length = 64)
    private String contactInfo;

    @Column(name = "accident_type", nullable = false, length = 32)
    private String accidentType;

    @Column(name = "accident_date", nullable = false)
    private LocalDate accidentDate;

    @Column(name = "claim_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal claimAmount;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClaimNo() {
        return claimNo;
    }

    public void setClaimNo(String claimNo) {
        this.claimNo = claimNo;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public void setPolicyNumber(String policyNumber) {
        this.policyNumber = policyNumber;
    }

    public String getInsuredName() {
        return insuredName;
    }

    public void setInsuredName(String insuredName) {
        this.insuredName = insuredName;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public void setContactInfo(String contactInfo) {
        this.contactInfo = contactInfo;
    }

    public String getAccidentType() {
        return accidentType;
    }

    public void setAccidentType(String accidentType) {
        this.accidentType = accidentType;
    }

    public LocalDate getAccidentDate() {
        return accidentDate;
    }

    public void setAccidentDate(LocalDate accidentDate) {
        this.accidentDate = accidentDate;
    }

    public BigDecimal getClaimAmount() {
        return claimAmount;
    }

    public void setClaimAmount(BigDecimal claimAmount) {
        this.claimAmount = claimAmount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
