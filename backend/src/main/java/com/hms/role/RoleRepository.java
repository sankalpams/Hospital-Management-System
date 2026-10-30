package com.hms.role;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface RoleRepository extends JpaRepository<Role, Long> {
    @EntityGraph(attributePaths = "permissions") Optional<Role> findWithPermissionsByCode(RoleCode code);
    @Override @EntityGraph(attributePaths = "permissions") List<Role> findAll();
    Optional<Role> findByCode(RoleCode code);
}
