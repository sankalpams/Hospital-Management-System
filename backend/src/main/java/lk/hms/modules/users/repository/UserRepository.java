package lk.hms.modules.users.repository;

import lk.hms.modules.users.entity.User;
import lk.hms.modules.users.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Loads the user with roles and permissions in one query (used to build the security principal). */
    @Query("""
            select distinct u from User u
            left join fetch u.roles r
            left join fetch r.permissions
            where u.username = :username
            """)
    Optional<User> findWithAuthoritiesByUsername(String username);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByEmail(String email);

    /** q is a lower-cased "%term%" pattern or null. */
    @Query("""
            select u from User u
            where (:q is null or u.username like :q or lower(u.fullName) like :q or lower(u.email) like :q)
              and (:status is null or u.status = :status)
              and (:role is null or exists (select 1 from u.roles r where r.code = :role))
            """)
    Page<User> search(String q, UserStatus status, String role, Pageable pageable);

    @Query("""
            select count(u) from User u join u.roles r
            where r.code = 'SUPER_ADMIN' and u.status = lk.hms.modules.users.entity.UserStatus.ACTIVE
            """)
    long countActiveSuperAdmins();
}
