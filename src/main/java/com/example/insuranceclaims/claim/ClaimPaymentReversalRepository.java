package com.example.insuranceclaims.claim;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClaimPaymentReversalRepository extends JpaRepository<ClaimPaymentReversal, Long> {

    Optional<ClaimPaymentReversal> findByClaimId(Long claimId);

    Optional<ClaimPaymentReversal> findByReversalVoucherNo(String reversalVoucherNo);

    List<ClaimPaymentReversal> findByClaimIdIn(Collection<Long> claimIds);
}
