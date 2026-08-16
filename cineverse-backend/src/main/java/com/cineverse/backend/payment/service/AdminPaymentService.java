package com.cineverse.backend.payment.service;

import com.cineverse.backend.payment.dto.AdminPaymentResponse;
import com.cineverse.backend.payment.entity.Payment;
import com.cineverse.backend.payment.entity.PaymentStatus;
import com.cineverse.backend.payment.mapper.AdminPaymentMapper;
import com.cineverse.backend.payment.repository.PaymentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs GET /api/v1/admin/payments — read-only. No refund/status-mutation
 * action lives here or is planned for this endpoint: reconciling an
 * ORPHANED_SUCCESS payment is a manual, out-of-band process (see CLAUDE.md
 * Phase 6), and this service's whole job is making that process's first
 * step — finding which specific rows need it — possible at all.
 */
@Service
public class AdminPaymentService {

    private final PaymentRepository paymentRepository;
    private final AdminPaymentMapper adminPaymentMapper;

    public AdminPaymentService(PaymentRepository paymentRepository, AdminPaymentMapper adminPaymentMapper) {
        this.paymentRepository = paymentRepository;
        this.adminPaymentMapper = adminPaymentMapper;
    }

    @Transactional(readOnly = true)
    public Page<AdminPaymentResponse> search(PaymentStatus status, Pageable pageable) {
        Page<Payment> page = paymentRepository.search(status, pageable);
        return page.map(adminPaymentMapper::toResponse);
    }
}
