package com.example.insuranceclaims.claim;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClaimPayApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClaimPaymentRepository paymentRepository;

    private long createClaim(double claimAmount) throws Exception {
        String body = """
                {
                  "policyNo": "P-%s",
                  "insuredName": "张三",
                  "contactPhone": "13800000000",
                  "accidentType": "车辆碰撞",
                  "accidentDate": "2026-09-01",
                  "claimAmount": %s,
                  "description": "雨天路滑追尾前车，后备箱受损"
                }
                """.formatted(UUID.randomUUID(), claimAmount);
        MvcResult result = mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private long acceptedClaim(double claimAmount) throws Exception {
        long id = createClaim(claimAmount);
        mockMvc.perform(post("/api/claims/{id}/accept", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"李理赔\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private long settledClaim(double claimAmount, double finalAmount) throws Exception {
        long id = acceptedClaim(claimAmount);
        mockMvc.perform(post("/api/claims/{id}/settle", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settledBy\": \"王结案\", \"finalAmount\": " + finalAmount
                                + ", \"settleRemark\": \"核定车辆维修费用后按合同约定结案\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private String payBody(String paidBy, String paidAmount, String voucherNo) {
        return """
                {"paidBy": "%s", "paidAmount": %s, "voucherNo": "%s"}"""
                .formatted(paidBy, paidAmount, voucherNo);
    }

    private String performPay(long id, String paidBy, String paidAmount, String voucherNo)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/claims/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(paidBy, paidAmount, voucherNo)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    @Test
    void payClaimSucceedsAndPersistsResult() throws Exception {
        long id = settledClaim(5000.00, 4500.50);
        long beforeCount = paymentRepository.count();

        mockMvc.perform(post("/api/claims/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("  周付款  ", "4500.50", "PAY-SUCCESS-001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("已赔付")))
                .andExpect(jsonPath("$.finalAmount", is(4500.50)))
                .andExpect(jsonPath("$.settledBy", is("王结案")))
                .andExpect(jsonPath("$.settleRemark", is("核定车辆维修费用后按合同约定结案")))
                .andExpect(jsonPath("$.payment.paidAmount", is(4500.50)))
                .andExpect(jsonPath("$.payment.paidBy", is("周付款")))
                .andExpect(jsonPath("$.payment.voucherNo", is("PAY-SUCCESS-001")))
                .andExpect(jsonPath("$.payment.paidAt", not(blankOrNullString())));

        org.junit.jupiter.api.Assertions.assertEquals(beforeCount + 1, paymentRepository.count());

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].status",
                        is(java.util.List.of("已赔付"))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].finalAmount",
                        is(java.util.List.of(4500.50))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].payment.voucherNo",
                        is(java.util.List.of("PAY-SUCCESS-001"))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].payment.paidAmount",
                        is(java.util.List.of(4500.50))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].payment.paidBy",
                        is(java.util.List.of("周付款"))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].payment.paidAt",
                        not(contains(nullValue()))));
    }

    @Test
    void payClaimRejectsInvalidParams() throws Exception {
        long blankHandlerId = settledClaim(5000.00, 100.00);
        mockMvc.perform(post("/api/claims/{id}/pay", blankHandlerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("   ", "100.00", "PAY-V-001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("赔付信息校验失败")))
                .andExpect(jsonPath("$.errors.paidBy", is("赔付办理人不能为空")));

        long shortHandlerId = settledClaim(5000.00, 100.00);
        mockMvc.perform(post("/api/claims/{id}/pay", shortHandlerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(" 周 ", "100.00", "PAY-V-002")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.paidBy", is("赔付办理人长度必须为2～30个字符")));

        long zeroAmountId = settledClaim(5000.00, 100.00);
        mockMvc.perform(post("/api/claims/{id}/pay", zeroAmountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "0", "PAY-V-003")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.paidAmount", is("实际赔付金额必须大于0")));

        long threeDecimalId = settledClaim(5000.00, 100.00);
        mockMvc.perform(post("/api/claims/{id}/pay", threeDecimalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "100.001", "PAY-V-004")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.paidAmount", is("实际赔付金额最多保留两位小数")));

        long badVoucherId = settledClaim(5000.00, 100.00);
        mockMvc.perform(post("/api/claims/{id}/pay", badVoucherId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "100.00", "pay_voucher!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.voucherNo",
                        is("赔付凭证号只允许包含英文字母、数字和连字符")));

        long shortVoucherId = settledClaim(5000.00, 100.00);
        mockMvc.perform(post("/api/claims/{id}/pay", shortVoucherId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "100.00", "  abc  ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.voucherNo", is("赔付凭证号长度必须为6～40个字符")));

        long missingId = settledClaim(5000.00, 100.00);
        mockMvc.perform(post("/api/claims/{id}/pay", missingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("赔付信息校验失败")))
                .andExpect(jsonPath("$.errors.paidBy").exists())
                .andExpect(jsonPath("$.errors.paidAmount").exists())
                .andExpect(jsonPath("$.errors.voucherNo").exists());

        long wrongTypeId = settledClaim(5000.00, 100.00);
        mockMvc.perform(post("/api/claims/{id}/pay", wrongTypeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "\"abc\"", "PAY-V-005")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("请求体格式不正确或字段类型不匹配")));
    }

    @Test
    void payClaimNormalizesVoucherAndReplaysIdempotently() throws Exception {
        long id = settledClaim(5000.00, 4500.50);
        long beforeCount = paymentRepository.count();

        String first = performPay(id, "  周付款  ", "4500.50",
                "  pay-" + UUID.randomUUID().toString().substring(0, 8) + "  ");
        String normalizedVoucher = JsonPath.read(first, "$.payment.voucherNo");
        Object paymentId = JsonPath.read(first, "$.payment.id");
        String paidAt = JsonPath.read(first, "$.payment.paidAt");
        org.junit.jupiter.api.Assertions.assertTrue(
                normalizedVoucher.startsWith("PAY-"));

        MvcResult replayResult = mockMvc.perform(post("/api/claims/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "4500.50", normalizedVoucher.toLowerCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("已赔付")))
                .andExpect(jsonPath("$.payment.id", is(paymentId)))
                .andExpect(jsonPath("$.payment.voucherNo", is(normalizedVoucher)))
                .andExpect(jsonPath("$.payment.paidBy", is("周付款")))
                .andExpect(jsonPath("$.payment.paidAmount", is(4500.50)))
                .andReturn();

        String replayPaidAt = JsonPath.read(replayResult.getResponse().getContentAsString(),
                "$.payment.paidAt");
        org.junit.jupiter.api.Assertions.assertEquals(paidAt, replayPaidAt);
        org.junit.jupiter.api.Assertions.assertEquals(beforeCount + 1, paymentRepository.count());
    }

    @Test
    void payClaimReturnsNotFoundForMissingClaim() throws Exception {
        mockMvc.perform(post("/api/claims/{id}/pay", 999999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "100.00", "PAY-NOT-FOUND-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("报案不存在，ID：999999999")));
    }

    @Test
    void payClaimRejectsIllegalStatus() throws Exception {
        long pendingId = createClaim(5000.00);
        mockMvc.perform(post("/api/claims/{id}/pay", pendingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "5000.00", "PAY-STATUS-001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("报案当前状态为「待受理」，仅「已结案」状态的报案可以执行赔付")));

        long acceptedId = acceptedClaim(5000.00);
        mockMvc.perform(post("/api/claims/{id}/pay", acceptedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "5000.00", "PAY-STATUS-002")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("报案当前状态为「处理中」，仅「已结案」状态的报案可以执行赔付")));

        long rejectedId = createClaim(5000.00);
        mockMvc.perform(post("/api/claims/{id}/reject", rejectedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"赵审核\", \"rejectReason\": \"保单已过期，不在保障范围内\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/claims/{id}/pay", rejectedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "5000.00", "PAY-STATUS-003")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("报案当前状态为「已驳回」，仅「已结案」状态的报案可以执行赔付")));
    }

    @Test
    void payClaimRejectsAmountMismatchWithFinalAmount() throws Exception {
        long id = settledClaim(5000.00, 4500.50);

        mockMvc.perform(post("/api/claims/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "4500.00", "PAY-MISMATCH-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message",
                        is("实际赔付金额必须等于最终结案金额（结案金额：4500.50元）")));

        mockMvc.perform(get("/api/claims"))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].status",
                        is(java.util.List.of("已结案"))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].payment",
                        contains(nullValue())));
    }

    @Test
    void payClaimRejectsVoucherUsedByAnotherClaim() throws Exception {
        String voucher = "CR-" + UUID.randomUUID().toString().substring(0, 8);
        long firstId = settledClaim(5000.00, 4500.00);
        performPay(firstId, "周付款", "4500.00", voucher);

        long secondId = settledClaim(8000.00, 8000.00);
        mockMvc.perform(post("/api/claims/{id}/pay", secondId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "8000.00", voucher.toLowerCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("赔付凭证号「" + voucher.toUpperCase() + "」已用于其他报案，不能重复使用")));

        mockMvc.perform(get("/api/claims"))
                .andExpect(jsonPath("$[?(@.id == " + secondId + ")].status",
                        is(java.util.List.of("已结案"))))
                .andExpect(jsonPath("$[?(@.id == " + secondId + ")].payment",
                        contains(nullValue())));
    }

    @Test
    void payClaimRejectsIdempotentReplayWithDifferentContent() throws Exception {
        String voucher = "RP-" + UUID.randomUUID().toString().substring(0, 8);
        long id = settledClaim(5000.00, 4500.50);
        long beforeCount = paymentRepository.count();
        performPay(id, "周付款", "4500.50", voucher);

        mockMvc.perform(post("/api/claims/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("郑复核", "4500.50", voucher)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("赔付凭证号「" + voucher.toUpperCase()
                                + "」已有赔付记录，赔付金额或办理人与原记录不一致")));

        mockMvc.perform(post("/api/claims/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "4500.50", "PAY-REPLAY-OTHER-001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("该报案已使用凭证号「" + voucher.toUpperCase()
                                + "」完成赔付，不能重复赔付")));

        org.junit.jupiter.api.Assertions.assertEquals(beforeCount + 1, paymentRepository.count());
    }

    @Test
    void listReturnsPaymentResultWithSettlementInfo() throws Exception {
        long paidId = settledClaim(6000.00, 5200.00);
        performPay(paidId, "周付款", "5200.00",
                "LI-" + UUID.randomUUID().toString().substring(0, 8));

        long settledId = settledClaim(3000.00, 3000.00);

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + paidId + ")].status",
                        is(java.util.List.of("已赔付"))))
                .andExpect(jsonPath("$[?(@.id == " + paidId + ")].finalAmount",
                        is(java.util.List.of(5200.00))))
                .andExpect(jsonPath("$[?(@.id == " + paidId + ")].settledBy",
                        is(java.util.List.of("王结案"))))
                .andExpect(jsonPath("$[?(@.id == " + paidId + ")].settledAt",
                        not(contains(nullValue()))))
                .andExpect(jsonPath("$[?(@.id == " + paidId + ")].payment.paidAmount",
                        is(java.util.List.of(5200.00))))
                .andExpect(jsonPath("$[?(@.id == " + paidId + ")].payment.paidBy",
                        is(java.util.List.of("周付款"))))
                .andExpect(jsonPath("$[?(@.id == " + settledId + ")].status",
                        is(java.util.List.of("已结案"))))
                .andExpect(jsonPath("$[?(@.id == " + settledId + ")].finalAmount",
                        is(java.util.List.of(3000.00))))
                .andExpect(jsonPath("$[?(@.id == " + settledId + ")].payment",
                        contains(nullValue())));
    }
}
