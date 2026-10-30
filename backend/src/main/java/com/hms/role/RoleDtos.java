package com.hms.role;

import java.util.Set;
public final class RoleDtos {
    private RoleDtos() {}
    public record RoleResponse(RoleCode code, String name, String description, Set<PermissionCode> permissions) {}
}
