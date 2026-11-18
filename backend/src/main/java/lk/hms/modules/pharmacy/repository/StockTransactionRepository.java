package lk.hms.modules.pharmacy.repository;

import lk.hms.modules.pharmacy.entity.StockTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {

    @Query("""
            select t from StockTransaction t
            where (:medicineId is null or t.medicineId = :medicineId)
              and (:type is null or t.type = :type)
              and t.createdAt >= :from and t.createdAt < :to
            """)
    Page<StockTransaction> search(Long medicineId, String type, Instant from, Instant to, Pageable pageable);
}
