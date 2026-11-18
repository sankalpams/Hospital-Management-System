package lk.hms.modules.inpatient.repository;

import jakarta.persistence.LockModeType;
import lk.hms.modules.inpatient.entity.Bed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BedRepository extends JpaRepository<Bed, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Bed b where b.id = :id")
    Optional<Bed> lockById(Long id);

    List<Bed> findByRoomIdInOrderByBedNoAsc(Collection<Long> roomIds);

    boolean existsByRoomIdAndBedNo(Long roomId, String bedNo);
}
