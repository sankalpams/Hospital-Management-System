package lk.hms.modules.inpatient.repository;

import lk.hms.modules.inpatient.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByWardIdInOrderByRoomNoAsc(Collection<Long> wardIds);

    boolean existsByWardIdAndRoomNo(Long wardId, String roomNo);
}
