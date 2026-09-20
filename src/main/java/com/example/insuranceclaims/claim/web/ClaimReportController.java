package com.example.insuranceclaims.claim.web;

import com.example.insuranceclaims.claim.ClaimReportService;
import com.example.insuranceclaims.claim.dto.ClaimReportCreateRequest;
import com.example.insuranceclaims.claim.dto.ClaimReportResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/claims")
public class ClaimReportController {

    private final ClaimReportService claimReportService;

    public ClaimReportController(ClaimReportService claimReportService) {
        this.claimReportService = claimReportService;
    }

    @PostMapping
    public ResponseEntity<ClaimReportResponse> create(
            @Valid @RequestBody ClaimReportCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(claimReportService.create(request));
    }

    @GetMapping
    public List<ClaimReportResponse> list() {
        return claimReportService.findAll();
    }
}
