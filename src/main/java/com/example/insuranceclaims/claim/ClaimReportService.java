package com.example.insuranceclaims.claim;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ClaimReportService {

    public static final String STATUS_PENDING = "待受理";
    public static final String STATUS_PROCESSING = "处理中";
    public static final String STATUS_REJECTED = "已驳回";
    public static final String INITIAL_STATUS = STATUS_PENDING;

    public static final String ACTION_ACCEPT = "受理";
    public static final String ACTION_REJECT = "驳回";

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

    @Transactional
    public ClaimReportResponse review(Long id, ReviewClaimRequest request) {
        String action = request.action() == null ? "" : request.action().trim();
        if (!ACTION_ACCEPT.equals(action) && !ACTION_REJECT.equals(action)) {
            throw new InvalidReviewRequestException("处理动作只能是“受理”或“驳回”");
        }

        String handler = request.handler() == null ? "" : request.handler().trim();
        if (handler.length() < 2 || handler.length() > 30) {
            throw new InvalidReviewRequestException("办理人去除首尾空格后长度需为2～30个字符");
        }

        String rejectReason = request.rejectReason() == null ? "" : request.rejectReason().trim();
        if (ACTION_REJECT.equals(action) && (rejectReason.length() < 5 || rejectReason.length() > 200)) {
            throw new InvalidReviewRequestException("驳回原因长度需为5～200个字符");
        }

        ClaimReport report = repository.findById(id)
                .orElseThrow(() -> new ClaimNotFoundException("报案不存在"));

        if (!STATUS_PENDING.equals(report.getStatus())) {
            throw new ClaimAlreadyProcessedException(
                    "该报案当前状态为“" + report.getStatus() + "”，仅“待受理”状态可以处理，请勿重复操作");
        }

        report.setHandler(handler);
        report.setHandledAt(LocalDateTime.now());
        if (ACTION_ACCEPT.equals(action)) {
            report.setStatus(STATUS_PROCESSING);
            report.setRejectReason(null);
        } else {
            report.setStatus(STATUS_REJECTED);
            report.setRejectReason(rejectReason);
        }

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
