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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
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
    private ClaimPaymentRepository paymentRepository;

    @Autowired
    private ClaimPaymentReversalRepository reversalRepository;

    private static final String REVERSE_REASON = "发现赔付金额核定有误，申请撤销本次赔付";

    private long createClaim() throws Exception {
        String body = """
                {
                  "policyNo": "P-%s",
                  "insuredName": "张三",
                  "contactPhone": "13800000000",
                  "accidentType": "车辆碰撞",
                  "accidentDate": "2026-09-01",
                  "claimAmount": 5000.00,
                  "description": "雨天路滑追尾前车，后备箱受损"
                }
                """.formatted(UUID.randomUUID());
        MvcResult result = mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private long paidClaim(String payVoucher) throws Exception {
        long id = settledClaim();
        mockMvc.perform(post("/api/claims/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidBy": "周付款", "paidAmount": 4500.50, "voucherNo": "%s"}"""
                                .formatted(payVoucher)))
                .andExpect(status().isOk());
        return id;
    }

    private long settledClaim() throws Exception {
        long id = createClaim();
        mockMvc.perform(post("/api/claims/{id}/accept", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"李理赔\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/claims/{id}/settle", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"settledBy": "王结案", "finalAmount": 4500.50,
                                 "settleRemark": "核定车辆维修费用后按合同约定结案"}"""))
                .andExpect(status().isOk());
        return id;
    }

    private String uniqueVoucher(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String reverseBody(String reversedBy, String reverseReason, String voucherNo) {
        return """
                {"reversedBy": "%s", "reverseReason": "%s", "reversalVoucherNo": "%s"}"""
                .formatted(reversedBy, reverseReason, voucherNo);
    }

    private String performReverse(long id, String voucherNo) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/claims/{id}/reverse-payment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("  孙撤销  ", REVERSE_REASON, voucherNo)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    @Test
    void reversePaymentSucceedsAndKeepsOriginalPayment() throws Exception {
        String payVoucher = uniqueVoucher("PAY-OK-");
        long id = paidClaim(payVoucher);
        ClaimPayment paymentBefore = paymentRepository.findByClaimId(id).orElseThrow();
        long reversalCountBefore = reversalRepository.count();

        String reverseVoucher = uniqueVoucher("REV-OK-");
        mockMvc.perform(post("/api/claims/{id}/reverse-payment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("  孙撤销  ",
                                "  " + REVERSE_REASON + "  ",
                                "  " + reverseVoucher.toLowerCase() + "  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("赔付已撤销")))
                .andExpect(jsonPath("$.payment.id", is(paymentBefore.getId().intValue())))
                .andExpect(jsonPath("$.payment.paidAmount", is(4500.50)))
                .andExpect(jsonPath("$.payment.paidBy", is("周付款")))
                .andExpect(jsonPath("$.payment.voucherNo", is(payVoucher)))
                .andExpect(jsonPath("$.reversal.paymentId",
                        is(paymentBefore.getId().intValue())))
                .andExpect(jsonPath("$.reversal.reversedBy", is("孙撤销")))
                .andExpect(jsonPath("$.reversal.reverseReason", is(REVERSE_REASON)))
                .andExpect(jsonPath("$.reversal.reversalVoucherNo", is(reverseVoucher)))
                .andExpect(jsonPath("$.reversal.reversedAt", not(nullValue())));

        org.junit.jupiter.api.Assertions.assertEquals(
                reversalCountBefore + 1, reversalRepository.count());

        ClaimPayment paymentAfter = paymentRepository.findByClaimId(id).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(paymentBefore.getId(), paymentAfter.getId());
        org.junit.jupiter.api.Assertions.assertEquals(paymentBefore.getPaidAmount(),
                paymentAfter.getPaidAmount());
        org.junit.jupiter.api.Assertions.assertEquals(paymentBefore.getPaidBy(),
                paymentAfter.getPaidBy());
        org.junit.jupiter.api.Assertions.assertEquals(paymentBefore.getVoucherNo(),
                paymentAfter.getVoucherNo());
        org.junit.jupiter.api.Assertions.assertEquals(paymentBefore.getPaidAt(),
                paymentAfter.getPaidAt());
    }

    @Test
    void reversePaymentRejectsInvalidParams() throws Exception {
        String payVoucher = uniqueVoucher("PAY-INV-");
        long id = paidClaim(payVoucher);

        mockMvc.perform(post("/api/claims/{id}/reverse-payment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("   ", REVERSE_REASON,
                                uniqueVoucher("REV-INV-"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("撤销赔付信息校验失败")))
                .andExpect(jsonPath("$.errors.reversedBy", is("撤销办理人不能为空")));

        mockMvc.perform(post("/api/claims/{id}/reverse-payment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody(" 孙 ", REVERSE_REASON,
                                uniqueVoucher("REV-INV-"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reversedBy",
                        is("撤销办理人长度必须为2～30个字符")));

        mockMvc.perform(post("/api/claims/{id}/reverse-payment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("孙撤销", "  有误  ",
                                uniqueVoucher("REV-INV-"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reverseReason",
                        is("撤销原因长度必须为5～200个字符")));

        mockMvc.perform(post("/api/claims/{id}/reverse-payment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("孙撤销", REVERSE_REASON, "rev_voucher!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reversalVoucherNo",
                        is("撤销凭证号只允许包含英文字母、数字和连字符")));

        mockMvc.perform(post("/api/claims/{id}/reverse-payment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("孙撤销", REVERSE_REASON, "  abc  ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reversalVoucherNo",
                        is("撤销凭证号长度必须为6～40个字符")));

        mockMvc.perform(post("/api/claims/{id}/reverse-payment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("撤销赔付信息校验失败")))
                .andExpect(jsonPath("$.errors.reversedBy").exists())
                .andExpect(jsonPath("$.errors.reverseReason").exists())
                .andExpect(jsonPath("$.errors.reversalVoucherNo").exists());

        org.junit.jupiter.api.Assertions.assertFalse(
                reversalRepository.findByClaimId(id).isPresent());
    }

    @Test
    void reversePaymentNormalizesVoucherAndReplaysIdempotently() throws Exception {
        String payVoucher = uniqueVoucher("PAY-IDEM-");
        long id = paidClaim(payVoucher);
        String reverseVoucher = uniqueVoucher("REV-IDEM-");
        long countBefore = reversalRepository.count();

        String first = performReverse(id, "  " + reverseVoucher.toLowerCase() + "  ");
        Object reversalId = JsonPath.read(first, "$.reversal.id");
        String normalizedVoucher = JsonPath.read(first, "$.reversal.reversalVoucherNo");
        String reversedAt = JsonPath.read(first, "$.reversal.reversedAt");
        org.junit.jupiter.api.Assertions.assertEquals(reverseVoucher, normalizedVoucher);

        MvcResult replay = mockMvc.perform(post("/api/claims/{id}/reverse-payment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("孙撤销", REVERSE_REASON,
                                normalizedVoucher.toLowerCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("赔付已撤销")))
                .andExpect(jsonPath("$.reversal.id", is(reversalId)))
                .andExpect(jsonPath("$.reversal.reversalVoucherNo", is(normalizedVoucher)))
                .andExpect(jsonPath("$.reversal.reversedBy", is("孙撤销")))
                .andReturn();

        String replayReversedAt = JsonPath.read(replay.getResponse().getContentAsString(),
                "$.reversal.reversedAt");
        org.junit.jupiter.api.Assertions.assertEquals(reversedAt, replayReversedAt);
        org.junit.jupiter.api.Assertions.assertEquals(countBefore + 1, reversalRepository.count());
    }

    @Test
    void reversePaymentReturnsNotFoundForMissingClaim() throws Exception {
        mockMvc.perform(post("/api/claims/{id}/reverse-payment", 999999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("孙撤销", REVERSE_REASON,
                                uniqueVoucher("REV-NF-"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("报案不存在，ID：999999999")));
    }

    @Test
    void reversePaymentRejectsIllegalStatus() throws Exception {
        long pendingId = createClaim();
        mockMvc.perform(post("/api/claims/{id}/reverse-payment", pendingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("孙撤销", REVERSE_REASON,
                                uniqueVoucher("REV-ST-"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("报案当前状态为「待受理」，仅「已赔付」状态的报案可以撤销赔付")));

        long settledId = settledClaim();
        mockMvc.perform(post("/api/claims/{id}/reverse-payment", settledId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("孙撤销", REVERSE_REASON,
                                uniqueVoucher("REV-ST-"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("报案当前状态为「已结案」，仅「已赔付」状态的报案可以撤销赔付")));
    }

    @Test
    void reversePaymentRejectsVoucherUsedByAnotherClaim() throws Exception {
        String sharedVoucher = uniqueVoucher("REV-SHARE-");
        long firstId = paidClaim(uniqueVoucher("PAY-SHARE-1-"));
        performReverse(firstId, sharedVoucher);

        long secondId = paidClaim(uniqueVoucher("PAY-SHARE-2-"));
        mockMvc.perform(post("/api/claims/{id}/reverse-payment", secondId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("孙撤销", REVERSE_REASON,
                                sharedVoucher.toLowerCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("撤销凭证号「" + sharedVoucher + "」已用于其他报案，不能重复使用")));

        org.junit.jupiter.api.Assertions.assertFalse(
                reversalRepository.findByClaimId(secondId).isPresent());
    }

    @Test
    void reversePaymentRejectsWhenPaymentRecordMissing() throws Exception {
        long id = paidClaim(uniqueVoucher("PAY-MISS-"));
        paymentRepository.deleteAll(paymentRepository.findByClaimIdIn(java.util.List.of(id)));

        mockMvc.perform(post("/api/claims/{id}/reverse-payment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("孙撤销", REVERSE_REASON,
                                uniqueVoucher("REV-MISS-"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("该报案不存在赔付记录，无法撤销赔付")));

        org.junit.jupiter.api.Assertions.assertFalse(
                reversalRepository.findByClaimId(id).isPresent());
    }

    @Test
    void reversePaymentRejectsIdempotentReplayWithDifferentContent() throws Exception {
        String reverseVoucher = uniqueVoucher("REV-CONF-");
        long id = paidClaim(uniqueVoucher("PAY-CONF-"));
        long countBefore = reversalRepository.count();
        performReverse(id, reverseVoucher);

        mockMvc.perform(post("/api/claims/{id}/reverse-payment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("钱复核", REVERSE_REASON, reverseVoucher)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("撤销凭证号「" + reverseVoucher
                                + "」已有撤销记录，撤销办理人或撤销原因与原记录不一致")));

        mockMvc.perform(post("/api/claims/{id}/reverse-payment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("孙撤销", "另一个不同的撤销原因，内容不一致",
                                reverseVoucher)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("撤销凭证号「" + reverseVoucher
                                + "」已有撤销记录，撤销办理人或撤销原因与原记录不一致")));

        org.junit.jupiter.api.Assertions.assertEquals(countBefore + 1, reversalRepository.count());
    }

    @Test
    void reversePaymentRejectsSecondReversalWithDifferentVoucher() throws Exception {
        String firstVoucher = uniqueVoucher("REV-DUP-1-");
        long id = paidClaim(uniqueVoucher("PAY-DUP-"));
        performReverse(id, firstVoucher);

        mockMvc.perform(post("/api/claims/{id}/reverse-payment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reverseBody("孙撤销", REVERSE_REASON,
                                uniqueVoucher("REV-DUP-2-"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("该报案已使用撤销凭证号「" + firstVoucher
                                + "」完成赔付撤销，不能重复撤销")));

        org.junit.jupiter.api.Assertions.assertEquals(1,
                reversalRepository.findByClaimId(id).stream().count());
    }

    @Test
    void listReturnsReversalResultWithPaymentAndSettlementInfo() throws Exception {
        String payVoucher = uniqueVoucher("PAY-LIST-");
        String reverseVoucher = uniqueVoucher("REV-LIST-");
        long reversedId = paidClaim(payVoucher);
        performReverse(reversedId, reverseVoucher);

        long paidId = paidClaim(uniqueVoucher("PAY-LIST-2-"));

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + reversedId + ")].status",
                        is(java.util.List.of("赔付已撤销"))))
                .andExpect(jsonPath("$[?(@.id == " + reversedId + ")].finalAmount",
                        is(java.util.List.of(4500.50))))
                .andExpect(jsonPath("$[?(@.id == " + reversedId + ")].settledBy",
                        is(java.util.List.of("王结案"))))
                .andExpect(jsonPath("$[?(@.id == " + reversedId + ")].payment.voucherNo",
                        is(java.util.List.of(payVoucher))))
                .andExpect(jsonPath("$[?(@.id == " + reversedId + ")].payment.paidBy",
                        is(java.util.List.of("周付款"))))
                .andExpect(jsonPath(
                        "$[?(@.id == " + reversedId + ")].reversal.reversalVoucherNo",
                        is(java.util.List.of(reverseVoucher))))
                .andExpect(jsonPath(
                        "$[?(@.id == " + reversedId + ")].reversal.reversedBy",
                        is(java.util.List.of("孙撤销"))))
                .andExpect(jsonPath(
                        "$[?(@.id == " + reversedId + ")].reversal.reversedAt",
                        not(org.hamcrest.Matchers.contains(nullValue()))))
                .andExpect(jsonPath("$[?(@.id == " + paidId + ")].status",
                        is(java.util.List.of("已赔付"))))
                .andExpect(jsonPath("$[?(@.id == " + paidId + ")].reversal",
                        org.hamcrest.Matchers.contains(nullValue())));
    }
}
