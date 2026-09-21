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
class ClaimHandleApiTests {

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

    @Test
    void acceptClaimSucceedsAndPersistsHandleResult() throws Exception {
        long id = createClaim();

        mockMvc.perform(post("/api/claims/{id}/accept", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"  李理赔  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("处理中")))
                .andExpect(jsonPath("$.handledBy", is("李理赔")))
                .andExpect(jsonPath("$.handledAt", not(blankOrNullString())))
                .andExpect(jsonPath("$.rejectReason", nullValue()));

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].status", is(java.util.List.of("处理中"))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].handledBy", is(java.util.List.of("李理赔"))));
    }

    @Test
    void acceptClaimIgnoresClientProvidedRejectReason() throws Exception {
        long id = createClaim();

        mockMvc.perform(post("/api/claims/{id}/accept", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"王审核\", \"rejectReason\": \"不应保存的原因\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("处理中")))
                .andExpect(jsonPath("$.rejectReason", nullValue()));
    }

    @Test
    void rejectClaimSucceedsAndPersistsHandleResult() throws Exception {
        long id = createClaim();

        mockMvc.perform(post("/api/claims/{id}/reject", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"赵审核\", \"rejectReason\": \"保单已过期，不在保障范围内\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("已驳回")))
                .andExpect(jsonPath("$.handledBy", is("赵审核")))
                .andExpect(jsonPath("$.handledAt", not(blankOrNullString())))
                .andExpect(jsonPath("$.rejectReason", is("保单已过期，不在保障范围内")));

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].status", is(java.util.List.of("已驳回"))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].rejectReason",
                        is(java.util.List.of("保单已过期，不在保障范围内"))));
    }

    @Test
    void acceptClaimRejectsInvalidHandler() throws Exception {
        long blankId = createClaim();
        mockMvc.perform(post("/api/claims/{id}/accept", blankId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.handler").exists());

        long shortId = createClaim();
        mockMvc.perform(post("/api/claims/{id}/accept", shortId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"张\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.handler", is("办理人长度必须为2～30个字符")));

        long longId = createClaim();
        mockMvc.perform(post("/api/claims/{id}/accept", longId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"%s\"}".formatted("超".repeat(31))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.handler", is("办理人长度必须为2～30个字符")));

        long missingId = createClaim();
        mockMvc.perform(post("/api/claims/{id}/accept", missingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.handler", is("办理人不能为空")));
    }

    @Test
    void rejectClaimRejectsInvalidReason() throws Exception {
        long missingId = createClaim();
        mockMvc.perform(post("/api/claims/{id}/reject", missingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"赵审核\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rejectReason", is("驳回原因不能为空")));

        long shortId = createClaim();
        mockMvc.perform(post("/api/claims/{id}/reject", shortId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"赵审核\", \"rejectReason\": \"太短\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rejectReason", is("驳回原因长度必须为5～200个字符")));

        long longId = createClaim();
        mockMvc.perform(post("/api/claims/{id}/reject", longId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"赵审核\", \"rejectReason\": \"%s\"}".formatted("长".repeat(201))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rejectReason", is("驳回原因长度必须为5～200个字符")));
    }

    @Test
    void handleClaimReturnsNotFoundForMissingClaim() throws Exception {
        mockMvc.perform(post("/api/claims/{id}/accept", 999999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"李理赔\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("报案不存在，ID：999999999")));

        mockMvc.perform(post("/api/claims/{id}/reject", 999999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"李理赔\", \"rejectReason\": \"保单已过期，不在保障范围内\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("报案不存在，ID：999999999")));
    }

    @Test
    void handleClaimRejectsDuplicateHandling() throws Exception {
        long acceptedId = createClaim();
        mockMvc.perform(post("/api/claims/{id}/accept", acceptedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"李理赔\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/claims/{id}/accept", acceptedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"王理赔\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("报案当前状态为「处理中」，仅「待受理」状态的报案可以受理")));

        mockMvc.perform(post("/api/claims/{id}/reject", acceptedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"王理赔\", \"rejectReason\": \"保单已过期，不在保障范围内\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("报案当前状态为「处理中」，仅「待受理」状态的报案可以驳回")));

        long rejectedId = createClaim();
        mockMvc.perform(post("/api/claims/{id}/reject", rejectedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"赵审核\", \"rejectReason\": \"保单已过期，不在保障范围内\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/claims/{id}/accept", rejectedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"王理赔\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("报案当前状态为「已驳回」，仅「待受理」状态的报案可以受理")));
    }

    @Test
    void failedHandlingLeavesClaimUnchanged() throws Exception {
        long id = createClaim();
        mockMvc.perform(post("/api/claims/{id}/accept", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"李理赔\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/claims/{id}/reject", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handler\": \"王审核\", \"rejectReason\": \"保单已过期，不在保障范围内\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].status", is(java.util.List.of("处理中"))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].handledBy", is(java.util.List.of("李理赔"))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].rejectReason", contains(nullValue())));
    }
}
