package com.ansj.shoppayment.payment.repository;

import com.ansj.shoppayment.payment.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
}
