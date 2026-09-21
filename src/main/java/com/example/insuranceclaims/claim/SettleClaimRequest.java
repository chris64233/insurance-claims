package com.example.insuranceclaims.claim;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SettleClaimRequest(
        @NotBlank(message = "结案办理人不能为空")
        @Size(min = 2, max = 30, message = "结案办理人长度必须为2～30个字符")
        String handler,

        @NotNull(message = "最终赔付金额不能为空")
        @DecimalMin(value = "0", inclusive = false, message = "最终赔付金额必须大于0")
        @Digits(integer = 12, fraction = 2, message = "最终赔付金额最多保留两位小数")
        BigDecimal finalAmount,

        @NotBlank(message = "结案说明不能为空")
        @Size(min = 5, max = 300, message = "结案说明长度必须为5～300个字符")
        String settleNote
) {
    public SettleClaimRequest {
        handler = handler == null ? null : handler.trim();
        settleNote = settleNote == null ? null : settleNote.trim();
    }
}
