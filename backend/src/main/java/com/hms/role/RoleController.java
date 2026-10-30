package com.hms.role;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController @RequestMapping("/api/v1/roles") @RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_READ')")
public class RoleController {
    private final RoleRepository roles;
    @GetMapping public List<RoleDtos.RoleResponse> list() {
        return roles.findAll().stream().map(r -> new RoleDtos.RoleResponse(r.getCode(), r.getName(), r.getDescription(),
            r.getPermissions().stream().map(Permission::getCode).collect(Collectors.toSet()))).sorted(Comparator.comparing(r -> r.code().name())).toList();
    }
}
