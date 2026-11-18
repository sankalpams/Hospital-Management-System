package lk.hms.modules.billing.repository;

import lk.hms.modules.billing.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByInvoiceIdOrderByIdAsc(Long invoiceId);

    @Query("select coalesce(sum(-p.amount), 0) from Payment p where p.refundOfPaymentId = :paymentId")
    BigDecimal refundedAmount(Long paymentId);
}
