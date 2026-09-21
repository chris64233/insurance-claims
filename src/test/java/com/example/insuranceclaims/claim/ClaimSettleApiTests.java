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
class ClaimSettleApiTests {

    @Autowired
    private MockMvc mockMvc;

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

    private long createAcceptedClaim() throws Exception {
        long id = createClaim();
        mockMvc.perform(post("/api/claims/{id}/accept", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"李理赔\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private String settleBody(String handler, String finalAmount, String settleNote) {
        return """
                {"handler": "%s", "finalAmount": %s, "settleNote": "%s"}
                """.formatted(handler, finalAmount, settleNote);
    }

    @Test
    void settleClaimSucceedsAndPersistsSettleResult() throws Exception {
        long id = createAcceptedClaim();

        mockMvc.perform(post("/api/claims/{id}/settle", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("  王结案  ", "3200.50", "  责任认定完成，按定损金额赔付结案  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("已结案")))
                .andExpect(jsonPath("$.settleAmount", is(3200.50)))
                .andExpect(jsonPath("$.settledBy", is("王结案")))
                .andExpect(jsonPath("$.settledAt", not(blankOrNullString())))
                .andExpect(jsonPath("$.settleNote", is("责任认定完成，按定损金额赔付结案")))
                .andExpect(jsonPath("$.handledBy", is("李理赔")));

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].status", is(java.util.List.of("已结案"))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].settleAmount", is(java.util.List.of(3200.50))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].settledBy", is(java.util.List.of("王结案"))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].settleNote",
                        is(java.util.List.of("责任认定完成，按定损金额赔付结案"))));
    }

    @Test
    void settleClaimAcceptsAmountEqualToClaimAmount() throws Exception {
        long id = createAcceptedClaim();

        mockMvc.perform(post("/api/claims/{id}/settle", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "5000.00", "按申请金额全额赔付结案")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("已结案")))
                .andExpect(jsonPath("$.settleAmount", is(5000.00)));
    }

    @Test
    void settleClaimRejectsInvalidAmount() throws Exception {
        long zeroId = createAcceptedClaim();
        mockMvc.perform(post("/api/claims/{id}/settle", zeroId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "0", "责任认定完成，按定损金额赔付结案")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.finalAmount", is("最终赔付金额必须大于0")));

        long negativeId = createAcceptedClaim();
        mockMvc.perform(post("/api/claims/{id}/settle", negativeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "-100", "责任认定完成，按定损金额赔付结案")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.finalAmount", is("最终赔付金额必须大于0")));

        long scaleId = createAcceptedClaim();
        mockMvc.perform(post("/api/claims/{id}/settle", scaleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "100.001", "责任认定完成，按定损金额赔付结案")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.finalAmount", is("最终赔付金额最多保留两位小数")));

        long missingId = createAcceptedClaim();
        mockMvc.perform(post("/api/claims/{id}/settle", missingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"王结案\", \"settleNote\": \"责任认定完成，按定损金额赔付结案\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.finalAmount", is("最终赔付金额不能为空")));

        long exceedId = createAcceptedClaim();
        mockMvc.perform(post("/api/claims/{id}/settle", exceedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "5000.01", "责任认定完成，按定损金额赔付结案")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("最终赔付金额不能超过申请金额（申请金额：5000.00）")));

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + exceedId + ")].status", is(java.util.List.of("处理中"))))
                .andExpect(jsonPath("$[?(@.id == " + exceedId + ")].settleAmount", contains(nullValue())));
    }

    @Test
    void settleClaimRejectsInvalidHandler() throws Exception {
        long blankId = createAcceptedClaim();
        mockMvc.perform(post("/api/claims/{id}/settle", blankId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("   ", "1000", "责任认定完成，按定损金额赔付结案")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.handler").exists());

        long shortId = createAcceptedClaim();
        mockMvc.perform(post("/api/claims/{id}/settle", shortId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("张", "1000", "责任认定完成，按定损金额赔付结案")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.handler", is("结案办理人长度必须为2～30个字符")));

        long longId = createAcceptedClaim();
        mockMvc.perform(post("/api/claims/{id}/settle", longId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("超".repeat(31), "1000", "责任认定完成，按定损金额赔付结案")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.handler", is("结案办理人长度必须为2～30个字符")));

        long missingId = createAcceptedClaim();
        mockMvc.perform(post("/api/claims/{id}/settle", missingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finalAmount\": 1000, \"settleNote\": \"责任认定完成，按定损金额赔付结案\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.handler", is("结案办理人不能为空")));
    }

    @Test
    void settleClaimRejectsInvalidSettleNote() throws Exception {
        long missingId = createAcceptedClaim();
        mockMvc.perform(post("/api/claims/{id}/settle", missingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"王结案\", \"finalAmount\": 1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.settleNote", is("结案说明不能为空")));

        long blankId = createAcceptedClaim();
        mockMvc.perform(post("/api/claims/{id}/settle", blankId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "1000", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.settleNote").exists());

        long shortId = createAcceptedClaim();
        mockMvc.perform(post("/api/claims/{id}/settle", shortId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "1000", "太短")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.settleNote", is("结案说明长度必须为5～300个字符")));

        long longId = createAcceptedClaim();
        mockMvc.perform(post("/api/claims/{id}/settle", longId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "1000", "长".repeat(301))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.settleNote", is("结案说明长度必须为5～300个字符")));
    }

    @Test
    void settleClaimReturnsNotFoundForMissingClaim() throws Exception {
        mockMvc.perform(post("/api/claims/{id}/settle", 999999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "1000", "责任认定完成，按定损金额赔付结案")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("报案不存在，ID：999999999")));
    }

    @Test
    void settleClaimRejectsIllegalStatus() throws Exception {
        long pendingId = createClaim();
        mockMvc.perform(post("/api/claims/{id}/settle", pendingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "1000", "责任认定完成，按定损金额赔付结案")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("报案当前状态为「待受理」，仅「处理中」状态的报案可以结案")));

        long rejectedId = createClaim();
        mockMvc.perform(post("/api/claims/{id}/reject", rejectedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"赵审核\", \"rejectReason\": \"保单已过期，不在保障范围内\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/claims/{id}/settle", rejectedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "1000", "责任认定完成，按定损金额赔付结案")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("报案当前状态为「已驳回」，仅「处理中」状态的报案可以结案")));
    }

    @Test
    void settleClaimRejectsDuplicateSettle() throws Exception {
        long id = createAcceptedClaim();
        mockMvc.perform(post("/api/claims/{id}/settle", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "1000", "责任认定完成，按定损金额赔付结案")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/claims/{id}/settle", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("孙结案", "2000", "重复结案应当被拒绝的说明")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("报案当前状态为「已结案」，仅「处理中」状态的报案可以结案")));

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].status", is(java.util.List.of("已结案"))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].settleAmount", is(java.util.List.of(1000.0))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].settledBy", is(java.util.List.of("王结案"))));
    }

    @Test
    void failedSettleLeavesClaimUnchanged() throws Exception {
        long id = createAcceptedClaim();

        mockMvc.perform(post("/api/claims/{id}/settle", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "9999.99", "责任认定完成，按定损金额赔付结案")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/claims/{id}/settle", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "1000", "太短")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].status", is(java.util.List.of("处理中"))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].handledBy", is(java.util.List.of("李理赔"))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].settleAmount", contains(nullValue())))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].settledBy", contains(nullValue())))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].settledAt", contains(nullValue())))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].settleNote", contains(nullValue())));
    }
}
