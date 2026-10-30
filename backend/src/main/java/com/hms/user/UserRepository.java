package com.hms.user;

import com.hms.role.RoleCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    @EntityGraph(attributePaths = {"role", "role.permissions"})
    @Query("select u from User u where u.deletedAt is null and (lower(u.username) = lower(:identity) or lower(u.email) = lower(:identity))")
    Optional<User> findActiveLoginUser(String identity);
    @EntityGraph(attributePaths = "role")
    Optional<User> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    @Query("select u from User u join u.role r where u.deletedAt is null and " +
        "(:q is null or lower(u.username) like lower(concat('%', :q, '%')) or lower(u.firstName) like lower(concat('%', :q, '%')) or lower(u.lastName) like lower(concat('%', :q, '%')) or lower(u.email) like lower(concat('%', :q, '%'))) and " +
        "(:role is null or r.code = :role) and (:active is null or u.active = :active)")
    Page<User> search(String q, RoleCode role, Boolean active, Pageable pageable);
}
