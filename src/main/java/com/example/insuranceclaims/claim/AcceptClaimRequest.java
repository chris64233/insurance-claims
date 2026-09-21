package com.example.insuranceclaims.claim;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptClaimRequest(
        @NotBlank(message = "办理人不能为空")
        @Size(min = 2, max = 30, message = "办理人长度必须为2～30个字符")
        String handler
) {
    public AcceptClaimRequest {
        handler = handler == null ? null : handler.trim();
    }
}
