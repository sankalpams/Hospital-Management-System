package com.hms.department;

import com.hms.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor
public class Department extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String location;

    @Column(nullable = false)
    private boolean active = true;

    public Department(String code, String name, String description, String phone, String location) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.phone = phone;
        this.location = location;
        this.active = true;
    }
}

