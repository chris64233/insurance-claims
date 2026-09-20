package com.example.insuranceclaims.claim;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/claims")
public class ClaimReportController {

    private final ClaimReportService service;

    public ClaimReportController(ClaimReportService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimReportResponse create(@Valid @RequestBody CreateClaimRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<ClaimReportResponse> list() {
        return service.listAll();
    }
}
