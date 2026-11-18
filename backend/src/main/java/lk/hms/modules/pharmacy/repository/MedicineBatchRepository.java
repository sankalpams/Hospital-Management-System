package lk.hms.modules.pharmacy.repository;

import jakarta.persistence.LockModeType;
import lk.hms.modules.pharmacy.entity.MedicineBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MedicineBatchRepository extends JpaRepository<MedicineBatch, Long> {

    /** FEFO candidates, row-locked for the dispensing transaction: unexpired, with stock, earliest expiry first. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from MedicineBatch b where b.medicineId = :medicineId and b.quantityOnHand > 0 and b.expiryDate > :today
            order by b.expiryDate asc, b.id asc
            """)
    List<MedicineBatch> lockDispensable(Long medicineId, LocalDate today);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from MedicineBatch b where b.id = :id")
    Optional<MedicineBatch> lockById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from MedicineBatch b where b.quantityOnHand > 0 and b.expiryDate <= :today")
    List<MedicineBatch> lockExpiredWithStock(LocalDate today);

    boolean existsByMedicineIdAndBatchNo(Long medicineId, String batchNo);

    List<MedicineBatch> findByMedicineIdOrderByExpiryDateAsc(Long medicineId);

    @Query("select b from MedicineBatch b where b.quantityOnHand > 0 and b.expiryDate <= :until order by b.expiryDate asc")
    List<MedicineBatch> expiringBy(LocalDate until);

    /** Usable stock (unexpired) per medicine. Rows: [medicineId, quantity]. */
    @Query("""
            select b.medicineId, coalesce(sum(b.quantityOnHand), 0) from MedicineBatch b
            where b.medicineId in :ids and b.expiryDate > :today group by b.medicineId
            """)
    List<Object[]> usableStock(Collection<Long> ids, LocalDate today);
}
