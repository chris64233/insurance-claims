package com.example.insuranceclaims.claim;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record ReverseClaimPaymentRequest(
        @NotBlank(message = "撤销办理人不能为空")
        @Size(min = 2, max = 30, message = "撤销办理人长度必须为2～30个字符")
        String reversedBy,

        @NotBlank(message = "撤销原因不能为空")
        @Size(min = 5, max = 200, message = "撤销原因长度必须为5～200个字符")
        String reverseReason,

        @NotBlank(message = "撤销凭证号不能为空")
        @Size(min = 6, max = 40, message = "撤销凭证号长度必须为6～40个字符")
        @Pattern(regexp = "[A-Z0-9-]+", message = "撤销凭证号只允许包含英文字母、数字和连字符")
        String reversalVoucherNo
) {
    public ReverseClaimPaymentRequest {
        reversedBy = normalize(reversedBy);
        reverseReason = normalize(reverseReason);
        if (reversalVoucherNo != null) {
            reversalVoucherNo = reversalVoucherNo.trim().toUpperCase(Locale.ROOT);
            if (reversalVoucherNo.isEmpty()) {
                reversalVoucherNo = null;
            }
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
