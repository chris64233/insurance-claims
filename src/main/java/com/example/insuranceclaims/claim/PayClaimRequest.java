package com.example.insuranceclaims.claim;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Locale;

public record PayClaimRequest(
        @NotBlank(message = "赔付办理人不能为空")
        @Size(min = 2, max = 30, message = "赔付办理人长度必须为2～30个字符")
        String paidBy,

        @NotNull(message = "实际赔付金额不能为空")
        @DecimalMin(value = "0", inclusive = false, message = "实际赔付金额必须大于0")
        @Digits(integer = 12, fraction = 2, message = "实际赔付金额最多保留两位小数")
        BigDecimal paidAmount,

        @NotBlank(message = "赔付凭证号不能为空")
        @Size(min = 6, max = 40, message = "赔付凭证号长度必须为6～40个字符")
        @Pattern(regexp = "[A-Z0-9-]+", message = "赔付凭证号只允许包含英文字母、数字和连字符")
        String voucherNo
) {
    public PayClaimRequest {
        paidBy = paidBy == null ? null : paidBy.trim();
        if (paidBy != null && paidBy.isEmpty()) {
            paidBy = null;
        }
        if (voucherNo != null) {
            voucherNo = voucherNo.trim().toUpperCase(Locale.ROOT);
            if (voucherNo.isEmpty()) {
                voucherNo = null;
            }
        }
    }
}
