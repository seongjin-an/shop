package com.ansj.shoppayment.payment.service;

import com.ansj.shoppayment.payment.entity.PaymentEntity;
import com.ansj.shoppayment.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentEntity save(PaymentEntity payment) {
        return paymentRepository.save(payment);
    }
}
