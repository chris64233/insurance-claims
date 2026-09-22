package com.example.insuranceclaims.claim;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
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

    private long acceptedClaim() throws Exception {
        long id = createClaim();
        mockMvc.perform(post("/api/claims/{id}/accept", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"李理赔\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private long settledClaim(double finalAmount) throws Exception {
        long id = acceptedClaim();
        mockMvc.perform(post("/api/claims/{id}/settle", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"settledBy": "王结案", "finalAmount": %s,
                                 "settleRemark": "核定车辆维修费用后按合同约定结案"}"""
                                .formatted(finalAmount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("已结案")));
        return id;
    }

    private String payBody(String paidBy, String paidAmount, String voucherNo) {
        return """
                {"paidBy": %s, "paidAmount": %s, "voucherNo": %s}"""
                .formatted(paidBy == null ? "null" : "\"" + paidBy + "\"",
                        paidAmount == null ? "null" : paidAmount,
                        voucherNo == null ? "null" : "\"" + voucherNo + "\"");
    }

    @Test
    void payClaimSucceedsAndPersistsResult() throws Exception {
        long id = settledClaim(4500.00);

        mockMvc.perform(post("/api/claims/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("  周付款  ", "4500.00", "PAY-2026-0001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("已赔付")))
                .andExpect(jsonPath("$.finalAmount", is(4500.00)))
                .andExpect(jsonPath("$.settledBy", is("王结案")))
                .andExpect(jsonPath("$.settleRemark", is("核定车辆维修费用后按合同约定结案")))
                .andExpect(jsonPath("$.payment.paidAmount", is(4500.00)))
                .andExpect(jsonPath("$.payment.paidBy", is("周付款")))
                .andExpect(jsonPath("$.payment.voucherNo", is("PAY-2026-0001")))
                .andExpect(jsonPath("$.payment.paidAt", not(blankOrNullString())));

        ClaimPayment payment = paymentRepository.findByClaimId(id).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(1, paymentRepository.countByClaimId(id));
        org.junit.jupiter.api.Assertions.assertEquals("周付款", payment.getPaidBy());
        org.junit.jupiter.api.Assertions.assertEquals(new java.math.BigDecimal("4500.00"),
                payment.getPaidAmount());
    }

    @Test
    void payClaimRejectsInvalidParams() throws Exception {
        long blankHandlerId = settledClaim(100.00);
        mockMvc.perform(post("/api/claims/{id}/pay", blankHandlerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("   ", "100.00", "PAY-001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.paidBy", is("赔付办理人长度必须为2～30个字符")));

        long shortHandlerId = settledClaim(100.00);
        mockMvc.perform(post("/api/claims/{id}/pay", shortHandlerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(" 周 ", "100.00", "PAY-001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.paidBy", is("赔付办理人长度必须为2～30个字符")));

        long longHandlerId = settledClaim(100.00);
        mockMvc.perform(post("/api/claims/{id}/pay", longHandlerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("长".repeat(31), "100.00", "PAY-001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.paidBy", is("赔付办理人长度必须为2～30个字符")));

        long zeroAmountId = settledClaim(100.00);
        mockMvc.perform(post("/api/claims/{id}/pay", zeroAmountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "0", "PAY-001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.paidAmount", is("实际赔付金额必须大于0")));

        long threeDecimalId = settledClaim(100.00);
        mockMvc.perform(post("/api/claims/{id}/pay", threeDecimalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "100.005", "PAY-001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.paidAmount", is("实际赔付金额最多保留两位小数")));

        long missingAmountId = settledClaim(100.00);
        mockMvc.perform(post("/api/claims/{id}/pay", missingAmountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paidBy\": \"周付款\", \"voucherNo\": \"PAY-001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.paidAmount", is("实际赔付金额不能为空")));

        long shortVoucherId = settledClaim(100.00);
        mockMvc.perform(post("/api/claims/{id}/pay", shortVoucherId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "100.00", "ABC12")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.voucherNo", is("赔付凭证号长度必须为6～40个字符")));

        long invalidVoucherId = settledClaim(100.00);
        mockMvc.perform(post("/api/claims/{id}/pay", invalidVoucherId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "100.00", "PAY_001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.voucherNo",
                        is("赔付凭证号只允许包含英文字母、数字和连字符")));

        long missingAllId = settledClaim(100.00);
        mockMvc.perform(post("/api/claims/{id}/pay", missingAllId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("赔付信息校验失败")))
                .andExpect(jsonPath("$.errors.paidBy").exists())
                .andExpect(jsonPath("$.errors.paidAmount").exists())
                .andExpect(jsonPath("$.errors.voucherNo").exists());

        long wrongTypeId = settledClaim(100.00);
        mockMvc.perform(post("/api/claims/{id}/pay", wrongTypeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "\"abc\"", "PAY-001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("请求体格式不正确或字段类型不匹配")));
    }

    @Test
    void payClaimNormalizesVoucherNoToUpperCase() throws Exception {
        long id = settledClaim(4500.00);

        mockMvc.perform(post("/api/claims/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "4500.00", "  pay-ab01  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.voucherNo", is("PAY-AB01")));

        org.junit.jupiter.api.Assertions.assertEquals("PAY-AB01",
                paymentRepository.findByClaimId(id).orElseThrow().getVoucherNo());

        mockMvc.perform(get("/api/claims"))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].payment.voucherNo",
                        is(List.of("PAY-AB01"))));
    }

    @Test
    void payClaimRejectsAmountDifferentFromFinalAmount() throws Exception {
        long id = settledClaim(4500.00);

        mockMvc.perform(post("/api/claims/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "4400.00", "PAY-DIFF-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message",
                        is("实际赔付金额必须与最终结案金额一致（结案金额：4500.00元）")));

        mockMvc.perform(get("/api/claims"))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].status",
                        is(List.of("已结案"))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].payment",
                        contains(nullValue())));
        org.junit.jupiter.api.Assertions.assertEquals(0, paymentRepository.countByClaimId(id));
    }

    @Test
    void payClaimReturnsNotFoundForMissingClaim() throws Exception {
        mockMvc.perform(post("/api/claims/{id}/pay", 999999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "100.00", "PAY-001")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("报案不存在，ID：999999999")));
    }

    @Test
    void payClaimRejectsIllegalStatus() throws Exception {
        long pendingId = createClaim();
        mockMvc.perform(post("/api/claims/{id}/pay", pendingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "5000.00", "PAY-PEND-01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("报案当前状态为「待受理」，仅「已结案」状态的报案可以执行赔付")));

        long acceptedId = acceptedClaim();
        mockMvc.perform(post("/api/claims/{id}/pay", acceptedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "5000.00", "PAY-PROC-01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("报案当前状态为「处理中」，仅「已结案」状态的报案可以执行赔付")));

        long rejectedId = createClaim();
        mockMvc.perform(post("/api/claims/{id}/reject", rejectedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"赵审核\", \"rejectReason\": \"保单已过期，不在保障范围内\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/claims/{id}/pay", rejectedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "5000.00", "PAY-REJ-001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("报案当前状态为「已驳回」，仅「已结案」状态的报案可以执行赔付")));
    }

    @Test
    void payClaimIdempotentReplayReturnsFirstResult() throws Exception {
        long id = settledClaim(4500.00);

        MvcResult firstResult = mockMvc.perform(post("/api/claims/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "4500.00", "pay-replay-01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("已赔付")))
                .andExpect(jsonPath("$.payment.voucherNo", is("PAY-REPLAY-01")))
                .andReturn();

        String firstBody = firstResult.getResponse().getContentAsString();
        Integer firstPaymentId = JsonPath.read(firstBody, "$.payment.id");
        String firstPaidAt = JsonPath.read(firstBody, "$.payment.paidAt");
        java.time.LocalDateTime storedPaidAt =
                paymentRepository.findByClaimId(id).orElseThrow().getPaidAt();

        mockMvc.perform(post("/api/claims/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("  周付款  ", "4500", "  PAY-REPLAY-01  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("已赔付")))
                .andExpect(jsonPath("$.payment.id", is(firstPaymentId.intValue())))
                .andExpect(jsonPath("$.payment.paidAt", is(firstPaidAt)));

        org.junit.jupiter.api.Assertions.assertEquals(1, paymentRepository.countByClaimId(id));
        org.junit.jupiter.api.Assertions.assertEquals(storedPaidAt,
                paymentRepository.findByClaimId(id).orElseThrow().getPaidAt());
    }

    @Test
    void payClaimRejectsVoucherUsedByOtherClaim() throws Exception {
        long firstId = settledClaim(4500.00);
        mockMvc.perform(post("/api/claims/{id}/pay", firstId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "4500.00", "pay-share-01")))
                .andExpect(status().isOk());

        long secondId = settledClaim(4500.00);
        mockMvc.perform(post("/api/claims/{id}/pay", secondId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "4500.00", "PAY-SHARE-01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("赔付凭证号「PAY-SHARE-01」已被其他报案使用，不能重复使用")));

        org.junit.jupiter.api.Assertions.assertEquals(0, paymentRepository.countByClaimId(secondId));
        mockMvc.perform(get("/api/claims"))
                .andExpect(jsonPath("$[?(@.id == " + secondId + ")].status",
                        is(List.of("已结案"))));
    }

    @Test
    void payClaimRejectsIdempotentContentMismatch() throws Exception {
        long id = settledClaim(4500.00);
        mockMvc.perform(post("/api/claims/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "4500.00", "pay-mismatch-01")))
                .andExpect(status().isOk());

        long differentAmountId = settledClaim(4000.00);
        mockMvc.perform(post("/api/claims/{id}/pay", differentAmountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "4000.00", "PAY-MISMATCH-01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("赔付凭证号「PAY-MISMATCH-01」已被其他报案使用，不能重复使用")));

        mockMvc.perform(post("/api/claims/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "4499.00", "PAY-MISMATCH-01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("赔付凭证号「PAY-MISMATCH-01」对应的赔付金额或办理人与原记录不一致，不能重复赔付")));

        mockMvc.perform(post("/api/claims/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("郑复核", "4500.00", "PAY-MISMATCH-01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("赔付凭证号「PAY-MISMATCH-01」对应的赔付金额或办理人与原记录不一致，不能重复赔付")));

        ClaimPayment firstPayment = paymentRepository.findByClaimId(id).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("周付款", firstPayment.getPaidBy());
        org.junit.jupiter.api.Assertions.assertEquals(0,
                new java.math.BigDecimal("4500.00").compareTo(firstPayment.getPaidAmount()));
        org.junit.jupiter.api.Assertions.assertEquals(1, paymentRepository.countByClaimId(id));
    }

    @Test
    void payClaimRejectsSecondPaymentWithDifferentVoucher() throws Exception {
        long id = settledClaim(4500.00);
        mockMvc.perform(post("/api/claims/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "4500.00", "pay-first-001")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/claims/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "4500.00", "PAY-SECOND-01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("报案当前状态为「已赔付」，仅「已结案」状态的报案可以执行赔付")));

        org.junit.jupiter.api.Assertions.assertEquals(1, paymentRepository.countByClaimId(id));
        ClaimPayment payment = paymentRepository.findByClaimId(id).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("PAY-FIRST-001", payment.getVoucherNo());
    }

    @Test
    void listClaimsReturnsPaymentResultWithSettleInfo() throws Exception {
        long paidId = settledClaim(4500.00);
        mockMvc.perform(post("/api/claims/{id}/pay", paidId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("周付款", "4500.00", "pay-list-0001")))
                .andExpect(status().isOk());

        long settledOnlyId = settledClaim(3000.00);

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + paidId + ")].status",
                        is(List.of("已赔付"))))
                .andExpect(jsonPath("$[?(@.id == " + paidId + ")].finalAmount",
                        is(List.of(4500.00))))
                .andExpect(jsonPath("$[?(@.id == " + paidId + ")].settledBy",
                        is(List.of("王结案"))))
                .andExpect(jsonPath("$[?(@.id == " + paidId + ")].payment.paidAmount",
                        is(List.of(4500.00))))
                .andExpect(jsonPath("$[?(@.id == " + paidId + ")].payment.paidBy",
                        is(List.of("周付款"))))
                .andExpect(jsonPath("$[?(@.id == " + paidId + ")].payment.voucherNo",
                        is(List.of("PAY-LIST-0001"))))
                .andExpect(jsonPath("$[?(@.id == " + paidId + ")].payment.paidAt",
                        not(contains(nullValue()))))
                .andExpect(jsonPath("$[?(@.id == " + settledOnlyId + ")].status",
                        is(List.of("已结案"))))
                .andExpect(jsonPath("$[?(@.id == " + settledOnlyId + ")].finalAmount",
                        is(List.of(3000.00))))
                .andExpect(jsonPath("$[?(@.id == " + settledOnlyId + ")].payment",
                        contains(nullValue())));
    }
}
