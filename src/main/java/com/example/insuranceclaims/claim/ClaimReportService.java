package com.example.insuranceclaims.claim;

import com.example.insuranceclaims.claim.dto.ClaimReportCreateRequest;
import com.example.insuranceclaims.claim.dto.ClaimReportResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class ClaimReportService {

    public static final String INITIAL_STATUS = "待受理";

    private static final DateTimeFormatter CLAIM_NO_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final long SUFFIX_MOD = 2_821_109_907_456L; // 36^8
    private static final int MAX_CLAIM_NO_ATTEMPTS = 10;
    private static final String DUPLICATE_MESSAGE =
            "该保单项下当日已存在相同事故类型的报案，请勿重复报案";

    private final ClaimReportRepository repository;
    private final SecureRandom random = new SecureRandom();

    public ClaimReportService(ClaimReportRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ClaimReportResponse create(ClaimReportCreateRequest request) {
        String policyNumber = request.getPolicyNumber().trim();
        String insuredName = request.getInsuredName().trim();
        String contactInfo = request.getContactInfo().trim();
        String accidentType = request.getAccidentType().trim();
        String description = request.getDescription() == null
                ? null : request.getDescription().trim();

        if (repository.existsByPolicyNumberAndAccidentDateAndAccidentType(
                policyNumber, request.getAccidentDate(), accidentType)) {
            throw new DuplicateClaimException(DUPLICATE_MESSAGE);
        }

        ClaimReport report = new ClaimReport();
        report.setClaimNo(generateClaimNo());
        report.setPolicyNumber(policyNumber);
        report.setInsuredName(insuredName);
        report.setContactInfo(contactInfo);
        report.setAccidentType(accidentType);
        report.setAccidentDate(request.getAccidentDate());
        report.setClaimAmount(request.getClaimAmount());
        report.setDescription(description);
        // 以下字段由服务端生成，不接受客户端传入
        report.setStatus(INITIAL_STATUS);
        report.setCreatedAt(LocalDateTime.now());

        try {
            return ClaimReportResponse.from(repository.saveAndFlush(report));
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateClaimException(DUPLICATE_MESSAGE);
        }
    }

    @Transactional(readOnly = true)
    public List<ClaimReportResponse> findAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(ClaimReportResponse::from)
                .toList();
    }

    private String generateClaimNo() {
        String datePart = LocalDate.now().format(CLAIM_NO_DATE);
        for (int attempt = 0; attempt < MAX_CLAIM_NO_ATTEMPTS; attempt++) {
            String suffix = Long.toString(
                    (random.nextLong() & Long.MAX_VALUE) % SUFFIX_MOD, 36
            ).toUpperCase(Locale.ROOT);
            String candidate =
                    "CLM" + datePart + String.format("%8s", suffix).replace(' ', '0');
            if (!repository.existsByClaimNo(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("理赔编号生成失败，请稍后重试");
    }
}
