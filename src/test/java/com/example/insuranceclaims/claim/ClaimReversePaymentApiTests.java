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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClaimReversePaymentApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClaimReportRepository claimRepository;

    @Autowired
    private ClaimPaymentRepository paymentRepository;

    @Autowired
    private ClaimPaymentReversalRepository reversalRepository;

    private static final String REVERSE_REASON = "发现赔付金额核定有误，申请撤销本次赔付";

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

    private long paidClaim(double claimAmount, double finalAmount, String voucher)
            throws Exception {
        long id = settledClaim(claimAmount, finalAmount);
        mockMvc.perform(post("/api/claims/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidBy": "周付款", "paidAmount": %s, "voucherNo": "%s"}"""
                                .formatted(finalAmount, voucher)))
                .andExpect(status().isOk());
        return id;
    }

    private long paidClaim(double claimAmount, double finalAmount) throws Exception {
        return paidClaim(claimAmount, finalAmount,
                "PAY-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private String reverseBody(String reversedBy, String reverseReason, String voucherNo) {
        return """
                {"reversedBy": "%s", "reverseReason": "%s", "reversalVoucherNo": "%s"}"""
                .formatted(reversedBy, reverseReason, voucherNo);
    }

    private String performReverse(long id, String reversedBy, String reverseReason,
                                  String voucherNo) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/claims/{id}/reverse-payment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody(reversedBy, reverseReason, voucherNo)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    @Test
    void reversePaymentSucceedsAndPersistsResult() throws Exception {
        String paymentVoucher =
                "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long id = paidClaim(5000.00, 4500.50, paymentVoucher);
        long paymentId = paymentRepository.findByClaimId(id).orElseThrow().getId();
        long beforeCount = reversalRepository.count();

        mockMvc.perform(post("/api/claims/{id}/reverse-payment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("  陈撤销  ", "  " + REVERSE_REASON + "  ",
                                "  rv-success-001  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("赔付已撤销")))
                .andExpect(jsonPath("$.finalAmount", is(4500.50)))
                .andExpect(jsonPath("$.settledBy", is("王结案")))
                .andExpect(jsonPath("$.settleRemark", is("核定车辆维修费用后按合同约定结案")))
                .andExpect(jsonPath("$.payment.id", is((int) paymentId)))
                .andExpect(jsonPath("$.payment.paidAmount", is(4500.50)))
                .andExpect(jsonPath("$.payment.paidBy", is("周付款")))
                .andExpect(jsonPath("$.payment.voucherNo", is(paymentVoucher)))
                .andExpect(jsonPath("$.reversal.id", not(blankOrNullString())))
                .andExpect(jsonPath("$.reversal.paymentId", is((int) paymentId)))
                .andExpect(jsonPath("$.reversal.reversedBy", is("陈撤销")))
                .andExpect(jsonPath("$.reversal.reverseReason", is(REVERSE_REASON)))
                .andExpect(jsonPath("$.reversal.reversalVoucherNo", is("RV-SUCCESS-001")))
                .andExpect(jsonPath("$.reversal.reversedAt", not(blankOrNullString())));

        org.junit.jupiter.api.Assertions.assertEquals(beforeCount + 1,
                reversalRepository.count());
        ClaimPaymentReversal saved = reversalRepository.findByClaimId(id).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(paymentId, saved.getPaymentId());
        org.junit.jupiter.api.Assertions.assertEquals("陈撤销", saved.getReversedBy());
        org.junit.jupiter.api.Assertions.assertEquals(REVERSE_REASON, saved.getReverseReason());
        org.junit.jupiter.api.Assertions.assertEquals("RV-SUCCESS-001",
                saved.getReversalVoucherNo());
    }

    @Test
    void reversePaymentKeepsOriginalPaymentUnchanged() throws Exception {
        String paymentVoucher = "PAY-" + UUID.randomUUID().toString().substring(0, 8);
        long id = paidClaim(6000.00, 5200.00, paymentVoucher);

        ClaimPayment paymentBefore = paymentRepository.findByClaimId(id).orElseThrow();
        long paymentCountBefore = paymentRepository.count();

        performReverse(id, "陈撤销", REVERSE_REASON,
                "RV-KEEP-" + UUID.randomUUID().toString().substring(0, 8));

        ClaimPayment paymentAfter = paymentRepository.findByClaimId(id).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(paymentBefore.getId(), paymentAfter.getId());
        org.junit.jupiter.api.Assertions.assertEquals(paymentBefore.getClaimId(),
                paymentAfter.getClaimId());
        org.junit.jupiter.api.Assertions.assertEquals(paymentBefore.getPaidAmount(),
                paymentAfter.getPaidAmount());
        org.junit.jupiter.api.Assertions.assertEquals(paymentBefore.getPaidBy(),
                paymentAfter.getPaidBy());
        org.junit.jupiter.api.Assertions.assertEquals(paymentBefore.getVoucherNo(),
                paymentAfter.getVoucherNo());
        org.junit.jupiter.api.Assertions.assertEquals(paymentBefore.getPaidAt(),
                paymentAfter.getPaidAt());
        org.junit.jupiter.api.Assertions.assertEquals(paymentCountBefore, paymentRepository.count());
    }

    @Test
    void reversePaymentRejectsInvalidParams() throws Exception {
        long blankHandlerId = paidClaim(5000.00, 100.00);
        mockMvc.perform(post("/api/claims/{id}/reverse-payment", blankHandlerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("   ", REVERSE_REASON, "RV-V-000001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("赔付撤销信息校验失败")))
                .andExpect(jsonPath("$.errors.reversedBy", is("撤销办理人不能为空")));

        long shortHandlerId = paidClaim(5000.00, 100.00);
        mockMvc.perform(post("/api/claims/{id}/reverse-payment", shortHandlerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody(" 陈 ", REVERSE_REASON, "RV-V-000002")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reversedBy",
                        is("撤销办理人长度必须为2～30个字符")));

        long blankReasonId = paidClaim(5000.00, 100.00);
        mockMvc.perform(post("/api/claims/{id}/reverse-payment", blankReasonId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("陈撤销", "   ", "RV-V-000003")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reverseReason", is("撤销原因不能为空")));

        long shortReasonId = paidClaim(5000.00, 100.00);
        mockMvc.perform(post("/api/claims/{id}/reverse-payment", shortReasonId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("陈撤销", "  原因过短  ", "RV-V-000004")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reverseReason",
                        is("撤销原因长度必须为5～200个字符")));

        long badVoucherId = paidClaim(5000.00, 100.00);
        mockMvc.perform(post("/api/claims/{id}/reverse-payment", badVoucherId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("陈撤销", REVERSE_REASON, "rv_voucher!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reversalVoucherNo",
                        is("撤销凭证号只允许包含英文字母、数字和连字符")));

        long shortVoucherId = paidClaim(5000.00, 100.00);
        mockMvc.perform(post("/api/claims/{id}/reverse-payment", shortVoucherId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("陈撤销", REVERSE_REASON, "  abc  ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reversalVoucherNo",
                        is("撤销凭证号长度必须为6～40个字符")));

        long missingFieldsId = paidClaim(5000.00, 100.00);
        mockMvc.perform(post("/api/claims/{id}/reverse-payment", missingFieldsId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("赔付撤销信息校验失败")))
                .andExpect(jsonPath("$.errors.reversedBy").exists())
                .andExpect(jsonPath("$.errors.reverseReason").exists())
                .andExpect(jsonPath("$.errors.reversalVoucherNo").exists());

        long wrongTypeId = paidClaim(5000.00, 100.00);
        mockMvc.perform(post("/api/claims/{id}/reverse-payment", wrongTypeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reversedBy\": {\"name\": \"陈撤销\"}, \"reverseReason\": \""
                                + REVERSE_REASON + "\", \"reversalVoucherNo\": \"RV-V-000005\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("请求体格式不正确或字段类型不匹配")));
    }

    @Test
    void reversePaymentNormalizesVoucherAndReplaysIdempotently() throws Exception {
        String voucher = "rv-" + UUID.randomUUID().toString().substring(0, 8);
        long id = paidClaim(5000.00, 4500.50);
        long beforeCount = reversalRepository.count();

        String first = performReverse(id, "  陈撤销  ", "  " + REVERSE_REASON + "  ",
                "  " + voucher + "  ");
        String normalizedVoucher = JsonPath.read(first, "$.reversal.reversalVoucherNo");
        Object reversalId = JsonPath.read(first, "$.reversal.id");
        String reversedAt = JsonPath.read(first, "$.reversal.reversedAt");
        org.junit.jupiter.api.Assertions.assertEquals(voucher.toUpperCase(), normalizedVoucher);

        MvcResult replayResult = mockMvc
                .perform(post("/api/claims/{id}/reverse-payment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("陈撤销", REVERSE_REASON,
                                normalizedVoucher.toLowerCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("赔付已撤销")))
                .andExpect(jsonPath("$.reversal.id", is(reversalId)))
                .andExpect(jsonPath("$.reversal.reversalVoucherNo", is(normalizedVoucher)))
                .andExpect(jsonPath("$.reversal.reversedBy", is("陈撤销")))
                .andExpect(jsonPath("$.reversal.reverseReason", is(REVERSE_REASON)))
                .andReturn();

        String replayReversedAt = JsonPath.read(replayResult.getResponse().getContentAsString(),
                "$.reversal.reversedAt");
        org.junit.jupiter.api.Assertions.assertEquals(reversedAt, replayReversedAt);
        org.junit.jupiter.api.Assertions.assertEquals(beforeCount + 1, reversalRepository.count());
    }

    @Test
    void reversePaymentReturnsNotFoundForMissingClaim() throws Exception {
        mockMvc.perform(post("/api/claims/{id}/reverse-payment", 999999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("陈撤销", REVERSE_REASON, "RV-NOT-FOUND-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("报案不存在，ID：999999999")));
    }

    @Test
    void reversePaymentRejectsIllegalStatus() throws Exception {
        long pendingId = createClaim(5000.00);
        mockMvc.perform(post("/api/claims/{id}/reverse-payment", pendingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("陈撤销", REVERSE_REASON, "RV-STATUS-0001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("报案当前状态为「待受理」，仅「已赔付」状态的报案可以撤销赔付")));

        long acceptedId = acceptedClaim(5000.00);
        mockMvc.perform(post("/api/claims/{id}/reverse-payment", acceptedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("陈撤销", REVERSE_REASON, "RV-STATUS-0002")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("报案当前状态为「处理中」，仅「已赔付」状态的报案可以撤销赔付")));

        long settledId = settledClaim(5000.00, 4500.00);
        mockMvc.perform(post("/api/claims/{id}/reverse-payment", settledId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("陈撤销", REVERSE_REASON, "RV-STATUS-0003")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("报案当前状态为「已结案」，仅「已赔付」状态的报案可以撤销赔付")));

        long rejectedId = createClaim(5000.00);
        mockMvc.perform(post("/api/claims/{id}/reject", rejectedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"赵审核\", \"rejectReason\": \"保单已过期，不在保障范围内\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/claims/{id}/reverse-payment", rejectedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("陈撤销", REVERSE_REASON, "RV-STATUS-0004")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("报案当前状态为「已驳回」，仅「已赔付」状态的报案可以撤销赔付")));
    }

    @Test
    void reversePaymentRejectsMissingPaymentRecord() throws Exception {
        long id = paidClaim(5000.00, 4500.50);
        paymentRepository.delete(paymentRepository.findByClaimId(id).orElseThrow());

        mockMvc.perform(post("/api/claims/{id}/reverse-payment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("陈撤销", REVERSE_REASON,
                                "RV-NOPAY-" + UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("该报案不存在赔付记录，无法撤销赔付")));

        org.junit.jupiter.api.Assertions.assertEquals(
                "已赔付", claimRepository.findById(id).orElseThrow().getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(reversalRepository.findByClaimId(id).isEmpty());
    }

    @Test
    void reversePaymentRejectsVoucherUsedByAnotherClaim() throws Exception {
        String voucher = "RX-" + UUID.randomUUID().toString().substring(0, 8);
        long firstId = paidClaim(5000.00, 4500.00);
        performReverse(firstId, "陈撤销", REVERSE_REASON, voucher);

        long secondId = paidClaim(8000.00, 8000.00);
        mockMvc.perform(post("/api/claims/{id}/reverse-payment", secondId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("陈撤销", REVERSE_REASON, voucher.toLowerCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("撤销凭证号「" + voucher.toUpperCase()
                                + "」已用于其他报案，不能重复使用")));

        org.junit.jupiter.api.Assertions.assertEquals(
                "已赔付", claimRepository.findById(secondId).orElseThrow().getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(
                reversalRepository.findByClaimId(secondId).isEmpty());
    }

    @Test
    void reversePaymentRejectsIdempotentReplayWithDifferentContent() throws Exception {
        String voucher = "RC-" + UUID.randomUUID().toString().substring(0, 8);
        long id = paidClaim(5000.00, 4500.50);
        long beforeCount = reversalRepository.count();
        performReverse(id, "陈撤销", REVERSE_REASON, voucher);

        mockMvc.perform(post("/api/claims/{id}/reverse-payment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("卫复核", REVERSE_REASON, voucher)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("撤销凭证号「" + voucher.toUpperCase()
                                + "」已有撤销记录，撤销办理人或撤销原因与原记录不一致")));

        mockMvc.perform(post("/api/claims/{id}/reverse-payment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("陈撤销", "重复提交但撤销原因与首次记录完全不一致",
                                voucher)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("撤销凭证号「" + voucher.toUpperCase()
                                + "」已有撤销记录，撤销办理人或撤销原因与原记录不一致")));

        org.junit.jupiter.api.Assertions.assertEquals(beforeCount + 1, reversalRepository.count());
    }

    @Test
    void reversePaymentRejectsRepeatReversalWithAnotherVoucher() throws Exception {
        String voucher = "RR-" + UUID.randomUUID().toString().substring(0, 8);
        long id = paidClaim(5000.00, 4500.50);
        long beforeCount = reversalRepository.count();
        performReverse(id, "陈撤销", REVERSE_REASON, voucher);

        mockMvc.perform(post("/api/claims/{id}/reverse-payment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("陈撤销", REVERSE_REASON,
                                "RV-OTHER-" + UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("该报案已使用撤销凭证号「" + voucher.toUpperCase()
                                + "」完成赔付撤销，不能重复撤销")));

        org.junit.jupiter.api.Assertions.assertEquals(beforeCount + 1, reversalRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(
                "赔付已撤销", claimRepository.findById(id).orElseThrow().getStatus());
    }

    @Test
    void listReturnsReversalResultWithPaymentAndSettlementInfo() throws Exception {
        String paymentVoucher =
                "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String reversalVoucher = "RL-" + UUID.randomUUID().toString().substring(0, 8);
        long reversedId = paidClaim(6000.00, 5200.00, paymentVoucher);
        String reverseJson = performReverse(reversedId, "陈撤销", REVERSE_REASON, reversalVoucher);
        long paymentId = ((Number) JsonPath.read(reverseJson, "$.payment.id")).longValue();

        long paidOnlyId = paidClaim(3000.00, 3000.00);

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + reversedId + ")].status",
                        is(java.util.List.of("赔付已撤销"))))
                .andExpect(jsonPath("$[?(@.id == " + reversedId + ")].finalAmount",
                        is(java.util.List.of(5200.00))))
                .andExpect(jsonPath("$[?(@.id == " + reversedId + ")].settledBy",
                        is(java.util.List.of("王结案"))))
                .andExpect(jsonPath("$[?(@.id == " + reversedId + ")].payment.id",
                        is(java.util.List.of((int) paymentId))))
                .andExpect(jsonPath("$[?(@.id == " + reversedId + ")].payment.voucherNo",
                        is(java.util.List.of(paymentVoucher))))
                .andExpect(jsonPath("$[?(@.id == " + reversedId + ")].payment.paidAmount",
                        is(java.util.List.of(5200.00))))
                .andExpect(jsonPath("$[?(@.id == " + reversedId + ")].reversal.paymentId",
                        is(java.util.List.of((int) paymentId))))
                .andExpect(jsonPath("$[?(@.id == " + reversedId + ")].reversal.reversedBy",
                        is(java.util.List.of("陈撤销"))))
                .andExpect(jsonPath("$[?(@.id == " + reversedId + ")].reversal.reverseReason",
                        is(java.util.List.of(REVERSE_REASON))))
                .andExpect(jsonPath(
                        "$[?(@.id == " + reversedId + ")].reversal.reversalVoucherNo",
                        is(java.util.List.of(reversalVoucher.toUpperCase()))))
                .andExpect(jsonPath("$[?(@.id == " + paidOnlyId + ")].status",
                        is(java.util.List.of("已赔付"))))
                .andExpect(jsonPath("$[?(@.id == " + paidOnlyId + ")].payment.paidAmount",
                        is(java.util.List.of(3000.00))))
                .andExpect(jsonPath("$[?(@.id == " + paidOnlyId + ")].reversal").exists())
                .andExpect(jsonPath("$[?(@.id == " + paidOnlyId
                                + ")].reversal.reversalVoucherNo").doesNotExist());
    }
}
