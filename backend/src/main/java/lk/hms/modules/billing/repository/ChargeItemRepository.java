package lk.hms.modules.billing.repository;

import lk.hms.modules.billing.entity.ChargeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChargeItemRepository extends JpaRepository<ChargeItem, Long> {

    boolean existsBySourceTypeAndSourceId(String sourceType, Long sourceId);

    java.util.Optional<ChargeItem> findBySourceTypeAndSourceId(String sourceType, Long sourceId);

    List<ChargeItem> findByPatientIdAndInvoiceIdIsNullOrderByServiceDateAsc(Long patientId);

    List<ChargeItem> findByInvoiceId(Long invoiceId);
}
