package com.example.insuranceclaims.claim;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClaimReportControllerTest {

    private static final String VALID_BODY = """
            {
              "policyNumber": "P2026001",
              "insuredName": "张三",
              "contactInfo": "13800138000",
              "accidentType": "交通事故",
              "accidentDate": "2026-09-01",
              "claimAmount": 5000.50,
              "description": "路口追尾，车身受损"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClaimReportRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAllInBatch();
    }

    @Test
    void createShouldSucceedAndGenerateServerFields() throws Exception {
        String bodyWithClientValues = """
                {
                  "policyNumber": "P2026001",
                  "insuredName": "张三",
                  "contactInfo": "13800138000",
                  "accidentType": "交通事故",
                  "accidentDate": "2026-09-01",
                  "claimAmount": 5000.50,
                  "description": "路口追尾，车身受损",
                  "claimNo": "FAKE-NO-001",
                  "status": "已结案",
                  "createdAt": "2000-01-01T00:00:00"
                }
                """;

        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithClientValues))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.claimNo", matchesPattern("^CLM\\d{8}[0-9A-Z]{8}$")))
                .andExpect(jsonPath("$.claimNo", not("FAKE-NO-001")))
                .andExpect(jsonPath("$.policyNumber").value("P2026001"))
                .andExpect(jsonPath("$.insuredName").value("张三"))
                .andExpect(jsonPath("$.accidentType").value("交通事故"))
                .andExpect(jsonPath("$.accidentDate").value("2026-09-01"))
                .andExpect(jsonPath("$.claimAmount").value(5000.50))
                .andExpect(jsonPath("$.description").value("路口追尾，车身受损"))
                .andExpect(jsonPath("$.status").value("待受理"))
                .andExpect(jsonPath("$.createdAt", notNullValue()));

        ClaimReport saved = repository.findAll().get(0);
        org.assertj.core.api.Assertions.assertThat(saved.getStatus()).isEqualTo("待受理");
        org.assertj.core.api.Assertions.assertThat(saved.getClaimNo())
                .startsWith("CLM").doesNotContain("FAKE");
        org.assertj.core.api.Assertions.assertThat(saved.getCreatedAt().getYear())
                .isGreaterThanOrEqualTo(2026);
    }

    @Test
    void createShouldRejectMissingRequiredFields() throws Exception {
        String body = """
                {
                  "policyNumber": "",
                  "insuredName": "",
                  "contactInfo": "",
                  "accidentType": "",
                  "description": "缺字段"
                }
                """;

        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.policyNumber").value("保单号不能为空"))
                .andExpect(jsonPath("$.fieldErrors.insuredName").value("被保险人姓名不能为空"))
                .andExpect(jsonPath("$.fieldErrors.contactInfo").value("联系方式不能为空"))
                .andExpect(jsonPath("$.fieldErrors.accidentType").value("事故类型不能为空"))
                .andExpect(jsonPath("$.fieldErrors.accidentDate").value("事故日期不能为空"))
                .andExpect(jsonPath("$.fieldErrors.claimAmount").value("申请金额不能为空"));
    }

    @Test
    void createShouldRejectFutureAccidentDate() throws Exception {
        String body = VALID_BODY.replace("2026-09-01", "2099-01-01");

        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.accidentDate").value("事故日期不能晚于当前日期"));
    }

    @Test
    void createShouldRejectNonPositiveAmount() throws Exception {
        String body = VALID_BODY.replace("5000.50", "0");

        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.claimAmount").value("申请金额必须大于 0"));

        String negativeBody = VALID_BODY.replace("5000.50", "-100");
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(negativeBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.claimAmount").value("申请金额必须大于 0"));
    }

    @Test
    void createShouldRejectDuplicateClaim() throws Exception {
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("重复报案")));

        org.assertj.core.api.Assertions.assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void createShouldTreatDifferentAccidentTypesAsSeparateClaims() throws Exception {
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());

        String otherTypeBody = VALID_BODY.replace("交通事故", "财产损失");
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(otherTypeBody))
                .andExpect(status().isCreated());

        org.assertj.core.api.Assertions.assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void listShouldReturnCreatedClaimsOrderedByCreatedAtDesc() throws Exception {
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());

        String secondBody = VALID_BODY
                .replace("P2026001", "P2026002")
                .replace("2026-09-01", "2026-09-02");
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].policyNumber").value("P2026002"))
                .andExpect(jsonPath("$[0].status").value("待受理"))
                .andExpect(jsonPath("$[1].policyNumber").value("P2026001"));
    }

    @Test
    void listShouldBeEmptyInitially() throws Exception {
        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
