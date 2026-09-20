package com.example.insuranceclaims.claim;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClaimReportApiTests {

    @Autowired
    private MockMvc mockMvc;

    private String validRequestJson(String policyNo) {
        return """
                {
                  "policyNo": "%s",
                  "insuredName": "张三",
                  "contactPhone": "13800000000",
                  "accidentType": "车辆碰撞",
                  "accidentDate": "2026-09-01",
                  "claimAmount": 5000.00,
                  "description": "雨天路滑追尾前车，后备箱受损"
                }
                """.formatted(policyNo);
    }

    @Test
    void createClaimReturnsGeneratedFieldsAndPersists() throws Exception {
        String policyNo = "P-" + UUID.randomUUID();

        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson(policyNo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimNo", startsWith("CL")))
                .andExpect(jsonPath("$.claimNo", not(blankOrNullString())))
                .andExpect(jsonPath("$.status", is("待受理")))
                .andExpect(jsonPath("$.createdAt", not(blankOrNullString())))
                .andExpect(jsonPath("$.policyNo", is(policyNo)))
                .andExpect(jsonPath("$.claimAmount", is(5000.00)));

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].policyNo", hasItem(policyNo)));
    }

    @Test
    void createClaimIgnoresClientProvidedServerFields() throws Exception {
        String policyNo = "P-" + UUID.randomUUID();
        String body = """
                {
                  "policyNo": "%s",
                  "insuredName": "李四",
                  "contactPhone": "13900000000",
                  "accidentType": "意外摔伤",
                  "accidentDate": "2026-09-02",
                  "claimAmount": 1200.50,
                  "description": "下楼梯踩空导致脚踝骨折",
                  "claimNo": "FAKE-CLIENT-001",
                  "status": "已结案",
                  "createdAt": "2000-01-01T00:00:00"
                }
                """.formatted(policyNo);

        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimNo", startsWith("CL")))
                .andExpect(jsonPath("$.claimNo", not(is("FAKE-CLIENT-001"))))
                .andExpect(jsonPath("$.status", is("待受理")))
                .andExpect(jsonPath("$.createdAt", not(startsWith("2000-01-01"))));
    }

    @Test
    void createClaimRejectsMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("报案信息校验失败")))
                .andExpect(jsonPath("$.errors.policyNo").exists())
                .andExpect(jsonPath("$.errors.insuredName").exists())
                .andExpect(jsonPath("$.errors.contactPhone").exists())
                .andExpect(jsonPath("$.errors.accidentType").exists())
                .andExpect(jsonPath("$.errors.accidentDate").exists())
                .andExpect(jsonPath("$.errors.claimAmount").exists())
                .andExpect(jsonPath("$.errors.description").exists());
    }

    @Test
    void createClaimRejectsFutureAccidentDate() throws Exception {
        String body = """
                {
                  "policyNo": "P-%s",
                  "insuredName": "王五",
                  "contactPhone": "13700000000",
                  "accidentType": "车辆碰撞",
                  "accidentDate": "%s",
                  "claimAmount": 800.00,
                  "description": "测试未来日期"
                }
                """.formatted(UUID.randomUUID(), LocalDate.now().plusDays(1));

        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.accidentDate", is("事故日期不能晚于当前日期")));
    }

    @Test
    void createClaimRejectsNonPositiveAmount() throws Exception {
        String body = """
                {
                  "policyNo": "P-%s",
                  "insuredName": "赵六",
                  "contactPhone": "13600000000",
                  "accidentType": "车辆碰撞",
                  "accidentDate": "2026-09-03",
                  "claimAmount": 0,
                  "description": "测试非法金额"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.claimAmount", is("申请金额必须大于0")));
    }

    @Test
    void createClaimRejectsDuplicateReport() throws Exception {
        String policyNo = "P-" + UUID.randomUUID();

        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson(policyNo)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson(policyNo)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        is("已存在相同保单号、事故日期和事故类型的报案，请勿重复提交")));
    }

    @Test
    void listClaimsReturnsCreatedClaimsInReverseChronologicalOrder() throws Exception {
        String policyNo = "P-" + UUID.randomUUID();
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson(policyNo)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].policyNo", is(policyNo)))
                .andExpect(jsonPath("$[*].status", everyItem(is("待受理"))));
    }
}
