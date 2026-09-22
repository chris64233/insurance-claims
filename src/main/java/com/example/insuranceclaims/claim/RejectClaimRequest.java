package com.example.insuranceclaims.claim;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectClaimRequest(
        @NotBlank(message = "办理人不能为空")
        @Size(min = 2, max = 30, message = "办理人长度必须为2～30个字符")
        String handler,
        @NotBlank(message = "驳回原因不能为空")
        @Size(min = 5, max = 200, message = "驳回原因长度必须为5～200个字符")
        String rejectReason
) {
    public RejectClaimRequest {
        handler = handler == null ? null : handler.trim();
        handler = handler != null && handler.isEmpty() ? null : handler;
        rejectReason = rejectReason == null ? null : rejectReason.trim();
        rejectReason = rejectReason != null && rejectReason.isEmpty() ? null : rejectReason;
    }
}
