package com.example.insuranceclaims.claim;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ClaimReportService {

    public static final String INITIAL_STATUS = "待受理";

    private static final DateTimeFormatter CLAIM_NO_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ClaimReportRepository repository;
    private final SecureRandom random = new SecureRandom();

    public ClaimReportService(ClaimReportRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ClaimReportResponse create(CreateClaimRequest request) {
        if (repository.existsByPolicyNoAndAccidentDateAndAccidentType(
                request.policyNo(), request.accidentDate(), request.accidentType())) {
            throw new DuplicateClaimException(
                    "已存在相同保单号、事故日期和事故类型的报案，请勿重复提交");
        }

        ClaimReport report = new ClaimReport();
        report.setClaimNo(generateClaimNo());
        report.setPolicyNo(request.policyNo());
        report.setInsuredName(request.insuredName());
        report.setContactPhone(request.contactPhone());
        report.setAccidentType(request.accidentType());
        report.setAccidentDate(request.accidentDate());
        report.setClaimAmount(request.claimAmount());
        report.setDescription(request.description());
        report.setStatus(INITIAL_STATUS);
        report.setCreatedAt(LocalDateTime.now());

        return ClaimReportResponse.from(repository.save(report));
    }

    @Transactional(readOnly = true)
    public List<ClaimReportResponse> listAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(ClaimReportResponse::from)
                .toList();
    }

    private String generateClaimNo() {
        String claimNo;
        do {
            claimNo = "CL" + LocalDateTime.now().format(CLAIM_NO_TIME_FORMAT)
                    + String.format("%06d", random.nextInt(1_000_000));
        } while (repository.existsByClaimNo(claimNo));
        return claimNo;
    }
}
