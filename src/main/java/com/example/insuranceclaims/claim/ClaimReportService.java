package com.example.insuranceclaims.claim;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ClaimReportService {

    public static final String INITIAL_STATUS = "待受理";
    public static final String ACCEPTED_STATUS = "处理中";
    public static final String REJECTED_STATUS = "已驳回";
    public static final String SETTLED_STATUS = "已结案";
    public static final String PAID_STATUS = "已赔付";
    public static final String REVERSED_STATUS = "赔付已撤销";

    private static final DateTimeFormatter CLAIM_NO_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ClaimReportRepository repository;
    private final ClaimPaymentRepository paymentRepository;
    private final ClaimPaymentReversalRepository reversalRepository;
    private final SecureRandom random = new SecureRandom();

    public ClaimReportService(ClaimReportRepository repository,
                              ClaimPaymentRepository paymentRepository,
                              ClaimPaymentReversalRepository reversalRepository) {
        this.repository = repository;
        this.paymentRepository = paymentRepository;
        this.reversalRepository = reversalRepository;
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
        List<ClaimReport> reports = repository.findAllByOrderByCreatedAtDesc();
        Map<Long, ClaimPayment> payments = paymentRepository
                .findByClaimIdIn(reports.stream().map(ClaimReport::getId).toList())
                .stream()
                .collect(Collectors.toMap(ClaimPayment::getClaimId, Function.identity()));
        Map<Long, ClaimPaymentReversal> reversals = reversalRepository
                .findByClaimIdIn(reports.stream().map(ClaimReport::getId).toList())
                .stream()
                .collect(Collectors.toMap(ClaimPaymentReversal::getClaimId, Function.identity()));
        return reports.stream()
                .map(report -> ClaimReportResponse.from(
                        report,
                        payments.get(report.getId()),
                        reversals.get(report.getId())))
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
            throw new ClaimValidationException(
                    "最终赔付金额不能超过申请金额（申请金额：" + report.getClaimAmount() + "元）");
        }
        report.setStatus(SETTLED_STATUS);
        report.setFinalAmount(request.finalAmount());
        report.setSettledBy(request.settledBy());
        report.setSettledAt(LocalDateTime.now());
        report.setSettleRemark(request.settleRemark());
        return ClaimReportResponse.from(repository.save(report));
    }

    @Transactional
    public ClaimReportResponse pay(Long id, PayClaimRequest request) {
        ClaimReport report = repository.findById(id)
                .orElseThrow(() -> new ClaimNotFoundException("报案不存在，ID：" + id));

        ClaimPayment existing = paymentRepository.findByClaimId(id).orElse(null);
        if (existing != null) {
            if (existing.getVoucherNo().equals(request.voucherNo())) {
                if (existing.getPaidAmount().compareTo(request.paidAmount()) == 0
                        && existing.getPaidBy().equals(request.paidBy())) {
                    return ClaimReportResponse.from(report, existing);
                }
                throw new ClaimStateConflictException(
                        "赔付凭证号「" + request.voucherNo() + "」已有赔付记录，赔付金额或办理人与原记录不一致");
            }
            throw new ClaimStateConflictException(
                    "该报案已使用凭证号「" + existing.getVoucherNo() + "」完成赔付，不能重复赔付");
        }

        if (paymentRepository.findByVoucherNo(request.voucherNo()).isPresent()) {
            throw new ClaimStateConflictException(
                    "赔付凭证号「" + request.voucherNo() + "」已用于其他报案，不能重复使用");
        }

        if (!SETTLED_STATUS.equals(report.getStatus())) {
            throw new ClaimStateConflictException(
                    "报案当前状态为「" + report.getStatus() + "」，仅「已结案」状态的报案可以执行赔付");
        }

        if (report.getFinalAmount() == null) {
            throw new ClaimValidationException("报案尚未结案，缺少最终结案金额，无法执行赔付");
        }
        if (report.getFinalAmount().compareTo(request.paidAmount()) != 0) {
            throw new ClaimValidationException(
                    "实际赔付金额必须等于最终结案金额（结案金额：" + report.getFinalAmount() + "元）");
        }

        ClaimPayment payment = new ClaimPayment();
        payment.setClaimId(report.getId());
        payment.setPaidAmount(request.paidAmount());
        payment.setPaidBy(request.paidBy());
        payment.setVoucherNo(request.voucherNo());
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);

        report.setStatus(PAID_STATUS);
        repository.save(report);
        return ClaimReportResponse.from(report, payment);
    }

    @Transactional
    public ClaimReportResponse reversePayment(Long id, ReverseClaimPaymentRequest request) {
        ClaimReport report = repository.findById(id)
                .orElseThrow(() -> new ClaimNotFoundException("报案不存在，ID：" + id));

        ClaimPaymentReversal existing = reversalRepository.findByClaimId(id).orElse(null);
        if (existing != null) {
            if (existing.getReversalVoucherNo().equals(request.reversalVoucherNo())) {
                if (existing.getReversedBy().equals(request.reversedBy())
                        && existing.getReverseReason().equals(request.reverseReason())) {
                    ClaimPayment existingPayment = paymentRepository.findByClaimId(id).orElse(null);
                    return ClaimReportResponse.from(report, existingPayment, existing);
                }
                throw new ClaimStateConflictException(
                        "撤销凭证号「" + request.reversalVoucherNo()
                                + "」已有撤销记录，撤销办理人或撤销原因与原记录不一致");
            }
            throw new ClaimStateConflictException(
                    "该报案已使用撤销凭证号「" + existing.getReversalVoucherNo()
                            + "」完成赔付撤销，不能重复撤销");
        }

        if (reversalRepository.findByReversalVoucherNo(request.reversalVoucherNo()).isPresent()) {
            throw new ClaimStateConflictException(
                    "撤销凭证号「" + request.reversalVoucherNo() + "」已用于其他报案，不能重复使用");
        }

        if (!PAID_STATUS.equals(report.getStatus())) {
            throw new ClaimStateConflictException(
                    "报案当前状态为「" + report.getStatus()
                            + "」，仅「已赔付」状态的报案可以撤销赔付");
        }

        ClaimPayment payment = paymentRepository.findByClaimId(id).orElse(null);
        if (payment == null) {
            throw new ClaimStateConflictException("该报案不存在赔付记录，无法撤销赔付");
        }

        ClaimPaymentReversal reversal = new ClaimPaymentReversal();
        reversal.setClaimId(report.getId());
        reversal.setPaymentId(payment.getId());
        reversal.setReversedBy(request.reversedBy());
        reversal.setReverseReason(request.reverseReason());
        reversal.setReversalVoucherNo(request.reversalVoucherNo());
        reversal.setReversedAt(LocalDateTime.now());
        reversalRepository.save(reversal);

        report.setStatus(REVERSED_STATUS);
        repository.save(report);
        return ClaimReportResponse.from(report, payment, reversal);
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
