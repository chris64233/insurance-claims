package com.example.insuranceclaims.claim;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClaimPaymentRepository extends JpaRepository<ClaimPayment, Long> {

    Optional<ClaimPayment> findByClaimId(Long claimId);

    Optional<ClaimPayment> findByVoucherNo(String voucherNo);

    List<ClaimPayment> findByClaimIdIn(List<Long> claimIds);

    long countByClaimId(Long claimId);
}
