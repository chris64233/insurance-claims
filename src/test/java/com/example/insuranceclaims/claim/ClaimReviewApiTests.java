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
class ClaimReviewApiTests {

    @Autowired
    private MockMvc mockMvc;

    private long createPendingClaim() throws Exception {
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
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private String reviewJson(String action, String handler, String rejectReason) {
        String reasonPart = rejectReason == null
                ? ""
                : ", \"rejectReason\": \"%s\"".formatted(rejectReason);
        return """
                { "action": "%s", "handler": "%s"%s }
                """.formatted(action, handler, reasonPart);
    }

    @Test
    void acceptClaimSucceedsAndPersistsResult() throws Exception {
        long id = createPendingClaim();

        mockMvc.perform(post("/api/claims/{id}/review", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("受理", "  李办理  ", "不应保存的驳回原因内容")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("处理中")))
                .andExpect(jsonPath("$.handler", is("李办理")))
                .andExpect(jsonPath("$.handledAt", not(blankOrNullString())))
                .andExpect(jsonPath("$.rejectReason", nullValue()));

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].status".formatted(id), contains("处理中")))
                .andExpect(jsonPath("$[?(@.id == %d)].handler".formatted(id), contains("李办理")))
                .andExpect(jsonPath("$[?(@.id == %d)].handledAt[0]".formatted(id), not(blankOrNullString())))
                .andExpect(jsonPath("$[?(@.id == %d)].rejectReason".formatted(id), contains(nullValue())));
    }

    @Test
    void rejectClaimSucceedsAndPersistsReason() throws Exception {
        long id = createPendingClaim();

        mockMvc.perform(post("/api/claims/{id}/review", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("驳回", "王审核", "保单不在有效保障期内")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("已驳回")))
                .andExpect(jsonPath("$.handler", is("王审核")))
                .andExpect(jsonPath("$.handledAt", not(blankOrNullString())))
                .andExpect(jsonPath("$.rejectReason", is("保单不在有效保障期内")));

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].status".formatted(id), contains("已驳回")))
                .andExpect(jsonPath("$[?(@.id == %d)].rejectReason".formatted(id),
                        contains("保单不在有效保障期内")));
    }

    @Test
    void reviewRejectsInvalidAction() throws Exception {
        long id = createPendingClaim();

        mockMvc.perform(post("/api/claims/{id}/review", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("结案", "李办理", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("处理动作只能是“受理”或“驳回”")));
    }

    @Test
    void reviewRejectsMissingOrInvalidHandler() throws Exception {
        long id = createPendingClaim();

        mockMvc.perform(post("/api/claims/{id}/review", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"action\": \"受理\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("办理人去除首尾空格后长度需为2～30个字符")));

        mockMvc.perform(post("/api/claims/{id}/review", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("受理", "  张  ", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("办理人去除首尾空格后长度需为2～30个字符")));

        mockMvc.perform(post("/api/claims/{id}/review", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("受理", "办".repeat(31), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("办理人去除首尾空格后长度需为2～30个字符")));
    }

    @Test
    void rejectRequiresReasonOfProperLength() throws Exception {
        long id = createPendingClaim();

        mockMvc.perform(post("/api/claims/{id}/review", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("驳回", "王审核", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("驳回原因长度需为5～200个字符")));

        mockMvc.perform(post("/api/claims/{id}/review", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("驳回", "王审核", "太短")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("驳回原因长度需为5～200个字符")));

        mockMvc.perform(post("/api/claims/{id}/review", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("驳回", "王审核", "长".repeat(201))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("驳回原因长度需为5～200个字符")));
    }

    @Test
    void reviewReturns404WhenClaimNotFound() throws Exception {
        mockMvc.perform(post("/api/claims/{id}/review", Long.MAX_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("受理", "李办理", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("报案不存在")));
    }

    @Test
    void reviewReturns409WhenClaimAlreadyProcessed() throws Exception {
        long acceptedId = createPendingClaim();
        mockMvc.perform(post("/api/claims/{id}/review", acceptedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("受理", "李办理", null)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/claims/{id}/review", acceptedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("受理", "李办理", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("该报案当前状态为“处理中”，仅“待受理”状态可以处理，请勿重复操作")));

        long rejectedId = createPendingClaim();
        mockMvc.perform(post("/api/claims/{id}/review", rejectedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("驳回", "王审核", "保单不在有效保障期内")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/claims/{id}/review", rejectedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("受理", "李办理", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("该报案当前状态为“已驳回”，仅“待受理”状态可以处理，请勿重复操作")));
    }

    @Test
    void failedReviewLeavesClaimUnchanged() throws Exception {
        long id = createPendingClaim();

        mockMvc.perform(post("/api/claims/{id}/review", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("驳回", "王审核", "太短")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].status".formatted(id), contains("待受理")))
                .andExpect(jsonPath("$[?(@.id == %d)].handler".formatted(id), contains(nullValue())));
    }
}
