package com.example.insuranceclaims.claim;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateClaimRequest(
        @NotBlank(message = "保单号不能为空")
        @Size(max = 64, message = "保单号长度不能超过64个字符")
        String policyNo,

        @NotBlank(message = "被保险人姓名不能为空")
        @Size(max = 64, message = "被保险人姓名长度不能超过64个字符")
        String insuredName,

        @NotBlank(message = "联系方式不能为空")
        @Size(max = 32, message = "联系方式长度不能超过32个字符")
        String contactPhone,

        @NotBlank(message = "事故类型不能为空")
        @Size(max = 32, message = "事故类型长度不能超过32个字符")
        String accidentType,

        @NotNull(message = "事故日期不能为空")
        @PastOrPresent(message = "事故日期不能晚于当前日期")
        LocalDate accidentDate,

        @NotNull(message = "申请金额不能为空")
        @DecimalMin(value = "0", inclusive = false, message = "申请金额必须大于0")
        BigDecimal claimAmount,

        @NotBlank(message = "事故说明不能为空")
        @Size(max = 1000, message = "事故说明长度不能超过1000个字符")
        String description
) {
}
