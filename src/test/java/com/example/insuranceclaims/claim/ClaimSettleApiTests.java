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

    private String settleBody(String settledBy, String finalAmount, String settleRemark) {
        return """
                {"settledBy": "%s", "finalAmount": %s, "settleRemark": "%s"}"""
                .formatted(settledBy, finalAmount, settleRemark);
    }

    @Test
    void settleClaimSucceedsAndPersistsResult() throws Exception {
        long id = acceptedClaim(5000.00);

        mockMvc.perform(post("/api/claims/{id}/settle", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("  王结案  ", "4500.50",
                                "  核定车辆维修费用后按合同约定赔付  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("已结案")))
                .andExpect(jsonPath("$.settledBy", is("王结案")))
                .andExpect(jsonPath("$.finalAmount", is(4500.50)))
                .andExpect(jsonPath("$.settledAt", not(blankOrNullString())))
                .andExpect(jsonPath("$.settleRemark", is("核定车辆维修费用后按合同约定赔付")));

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].status",
                        is(java.util.List.of("已结案"))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].finalAmount",
                        is(java.util.List.of(4500.50))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].settledBy",
                        is(java.util.List.of("王结案"))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].settleRemark",
                        is(java.util.List.of("核定车辆维修费用后按合同约定赔付"))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].settledAt",
                        not(contains(nullValue()))));
    }

    @Test
    void settleClaimAcceptsBoundaryAmounts() throws Exception {
        long equalId = acceptedClaim(5000.00);
        mockMvc.perform(post("/api/claims/{id}/settle", equalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "5000.00", "最终赔付金额等于申请金额，予以全额结案")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("已结案")))
                .andExpect(jsonPath("$.finalAmount", is(5000.00)));

        long tinyId = acceptedClaim(5000.00);
        mockMvc.perform(post("/api/claims/{id}/settle", tinyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "0.01", "最低赔付金额边界校验通过后予以结案")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalAmount", is(0.01)));

        long twoDecimalId = acceptedClaim(100.00);
        mockMvc.perform(post("/api/claims/{id}/settle", twoDecimalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "99.99", "两位小数的赔付金额符合规则予以结案")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalAmount", is(99.99)));
    }

    @Test
    void settleClaimRejectsAmountExceedingClaimAmount() throws Exception {
        long id = acceptedClaim(5000.00);

        mockMvc.perform(post("/api/claims/{id}/settle", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "5000.01", "超过申请金额的赔付不应被系统接受结案")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message",
                        is("最终赔付金额不能超过申请金额（申请金额：5000.00元）")));

        mockMvc.perform(get("/api/claims"))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].status",
                        is(java.util.List.of("处理中"))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].finalAmount",
                        contains(nullValue())));
    }

    @Test
    void settleClaimRejectsInvalidAmounts() throws Exception {
        long zeroId = acceptedClaim(5000.00);
        mockMvc.perform(post("/api/claims/{id}/settle", zeroId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "0", "零元赔付不合法，本用例应当被校验拦截")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("结案信息校验失败")))
                .andExpect(jsonPath("$.errors.finalAmount", is("最终赔付金额必须大于0")));

        long negativeId = acceptedClaim(5000.00);
        mockMvc.perform(post("/api/claims/{id}/settle", negativeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "-1.00", "负数赔付不合法，本用例应当被校验拦截")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.finalAmount", is("最终赔付金额必须大于0")));

        long threeDecimalId = acceptedClaim(5000.00);
        mockMvc.perform(post("/api/claims/{id}/settle", threeDecimalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "100.005", "超过两位小数的精度不合法，应被拦截")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.finalAmount", is("最终赔付金额最多保留两位小数")));

        long missingId = acceptedClaim(5000.00);
        mockMvc.perform(post("/api/claims/{id}/settle", missingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settledBy\": \"王结案\", \"settleRemark\": \"缺少赔付金额的请求不应通过校验\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.finalAmount", is("最终赔付金额不能为空")));

        long wrongTypeId = acceptedClaim(5000.00);
        mockMvc.perform(post("/api/claims/{id}/settle", wrongTypeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "\"abc\"", "金额类型错误的请求应当返回400提示")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("请求体格式不正确或字段类型不匹配")));
    }

    @Test
    void settleClaimRejectsInvalidTextFields() throws Exception {
        long blankHandlerId = acceptedClaim(5000.00);
        mockMvc.perform(post("/api/claims/{id}/settle", blankHandlerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("   ", "100.00", "办理人为空白的请求不合法，应被拦截")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.settledBy", is("结案办理人不能为空")));

        long shortHandlerId = acceptedClaim(5000.00);
        mockMvc.perform(post("/api/claims/{id}/settle", shortHandlerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody(" 王 ", "100.00", "办理人去除空格后仅一个字，应被拦截")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.settledBy", is("结案办理人长度必须为2～30个字符")));

        long longHandlerId = acceptedClaim(5000.00);
        mockMvc.perform(post("/api/claims/{id}/settle", longHandlerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("长".repeat(31), "100.00", "办理人超过三十个字的请求应被拦截")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.settledBy", is("结案办理人长度必须为2～30个字符")));

        long shortRemarkId = acceptedClaim(5000.00);
        mockMvc.perform(post("/api/claims/{id}/settle", shortRemarkId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "100.00", " 太短 ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.settleRemark", is("结案说明长度必须为5～300个字符")));

        long longRemarkId = acceptedClaim(5000.00);
        mockMvc.perform(post("/api/claims/{id}/settle", longRemarkId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "100.00", "长".repeat(301))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.settleRemark", is("结案说明长度必须为5～300个字符")));

        long missingId = acceptedClaim(5000.00);
        mockMvc.perform(post("/api/claims/{id}/settle", missingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("结案信息校验失败")))
                .andExpect(jsonPath("$.errors.settledBy").exists())
                .andExpect(jsonPath("$.errors.finalAmount").exists())
                .andExpect(jsonPath("$.errors.settleRemark").exists());
    }

    @Test
    void settleClaimReturnsNotFoundForMissingClaim() throws Exception {
        mockMvc.perform(post("/api/claims/{id}/settle", 999999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "100.00", "不存在的报案结案时应返回404提示信息")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("报案不存在，ID：999999999")));
    }

    @Test
    void settleClaimRejectsIllegalStatus() throws Exception {
        long pendingId = createClaim(5000.00);
        mockMvc.perform(post("/api/claims/{id}/settle", pendingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "100.00", "待受理的报案尚未处理，不能直接结案")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("报案当前状态为「待受理」，仅「处理中」状态的报案可以结案")));

        long rejectedId = createClaim(5000.00);
        mockMvc.perform(post("/api/claims/{id}/reject", rejectedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"赵审核\", \"rejectReason\": \"保单已过期，不在保障范围内\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/claims/{id}/settle", rejectedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "100.00", "已驳回的报案不能再进行结案操作")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("报案当前状态为「已驳回」，仅「处理中」状态的报案可以结案")));
    }

    @Test
    void settleClaimRejectsDuplicateSettlement() throws Exception {
        long id = acceptedClaim(5000.00);
        mockMvc.perform(post("/api/claims/{id}/settle", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "4500.00", "首次结案成功，记录赔付结果与说明")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/claims/{id}/settle", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("刘复核", "4800.00", "重复结案请求应被拒绝，原结案结果保留")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("报案当前状态为「已结案」，仅「处理中」状态的报案可以结案")));

        mockMvc.perform(get("/api/claims"))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].status",
                        is(java.util.List.of("已结案"))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].finalAmount",
                        is(java.util.List.of(4500.00))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].settledBy",
                        is(java.util.List.of("王结案"))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].settleRemark",
                        is(java.util.List.of("首次结案成功，记录赔付结果与说明"))));
    }

    @Test
    void failedSettlementLeavesClaimUnchanged() throws Exception {
        long id = acceptedClaim(5000.00);

        mockMvc.perform(post("/api/claims/{id}/settle", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("王结案", "9999.00", "超额赔付导致结案失败，原有处理数据不应改变")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/claims/{id}/settle", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settleBody("  ", "100.00", "空白办理人导致结案失败，原有处理数据不应改变")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].status",
                        is(java.util.List.of("处理中"))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].handledBy",
                        is(java.util.List.of("李理赔"))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].finalAmount",
                        contains(nullValue())))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].settledBy",
                        contains(nullValue())))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].settledAt",
                        contains(nullValue())))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].settleRemark",
                        contains(nullValue())));
    }
}
