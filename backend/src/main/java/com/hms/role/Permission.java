package com.hms.role;

import com.hms.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "permissions") @Getter @Setter @NoArgsConstructor
public class Permission extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, unique = true) private PermissionCode code;
    @Column(nullable = false) private String description;
    public Permission(PermissionCode code, String description) { this.code = code; this.description = description; }
}
