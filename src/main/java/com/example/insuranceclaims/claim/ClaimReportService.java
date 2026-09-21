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
    public static final String ACCEPTED_STATUS = "处理中";
    public static final String REJECTED_STATUS = "已驳回";
    public static final String SETTLED_STATUS = "已结案";

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

    @Transactional
    public ClaimReportResponse accept(Long id, AcceptClaimRequest request) {
        ClaimReport report = findPendingClaim(id, "受理");
        report.setStatus(ACCEPTED_STATUS);
        report.setHandledBy(request.handler());
        report.setHandledAt(LocalDateTime.now());
        report.setRejectReason(null);
        return ClaimReportResponse.from(repository.save(report));
    }

    @Transactional
    public ClaimReportResponse reject(Long id, RejectClaimRequest request) {
        ClaimReport report = findPendingClaim(id, "驳回");
        report.setStatus(REJECTED_STATUS);
        report.setHandledBy(request.handler());
        report.setHandledAt(LocalDateTime.now());
        report.setRejectReason(request.rejectReason());
        return ClaimReportResponse.from(repository.save(report));
    }

    @Transactional
    public ClaimReportResponse settle(Long id, SettleClaimRequest request) {
        ClaimReport report = repository.findById(id)
                .orElseThrow(() -> new ClaimNotFoundException("报案不存在，ID：" + id));
        if (!ACCEPTED_STATUS.equals(report.getStatus())) {
            throw new ClaimStateConflictException(
                    "报案当前状态为「" + report.getStatus() + "」，仅「处理中」状态的报案可以结案");
        }
        if (request.finalAmount().compareTo(report.getClaimAmount()) > 0) {
            throw new InvalidSettleAmountException(
                    "最终赔付金额不能超过申请金额（申请金额：" + report.getClaimAmount() + "）");
        }
        report.setStatus(SETTLED_STATUS);
        report.setSettleAmount(request.finalAmount());
        report.setSettledBy(request.handler());
        report.setSettledAt(LocalDateTime.now());
        report.setSettleNote(request.settleNote());
        return ClaimReportResponse.from(repository.save(report));
    }

    private ClaimReport findPendingClaim(Long id, String operation) {
        ClaimReport report = repository.findById(id)
                .orElseThrow(() -> new ClaimNotFoundException("报案不存在，ID：" + id));
        if (!INITIAL_STATUS.equals(report.getStatus())) {
            throw new ClaimStateConflictException(
                    "报案当前状态为「" + report.getStatus() + "」，仅「待受理」状态的报案可以" + operation);
        }
        return report;
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
