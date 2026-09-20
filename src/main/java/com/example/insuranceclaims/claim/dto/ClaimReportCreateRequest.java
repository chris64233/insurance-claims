package com.example.insuranceclaims.claim.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ClaimReportCreateRequest {

    @NotBlank(message = "保单号不能为空")
    @Size(max = 32, message = "保单号长度不能超过 32 个字符")
    private String policyNumber;

    @NotBlank(message = "被保险人姓名不能为空")
    @Size(max = 64, message = "被保险人姓名长度不能超过 64 个字符")
    private String insuredName;

    @NotBlank(message = "联系方式不能为空")
    @Size(max = 64, message = "联系方式长度不能超过 64 个字符")
    private String contactInfo;

    @NotBlank(message = "事故类型不能为空")
    @Size(max = 32, message = "事故类型长度不能超过 32 个字符")
    private String accidentType;

    @NotNull(message = "事故日期不能为空")
    @PastOrPresent(message = "事故日期不能晚于当前日期")
    private LocalDate accidentDate;

    @NotNull(message = "申请金额不能为空")
    @Positive(message = "申请金额必须大于 0")
    private BigDecimal claimAmount;

    @Size(max = 1000, message = "事故说明长度不能超过 1000 个字符")
    private String description;

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
}
