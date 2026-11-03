package com.restaurant.core.employee.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Bảng roles: danh mục cố định (MANAGER, WAITER, KITCHEN, CASHIER), ứng dụng chỉ được đọc. */
@Entity
@Immutable
@Table(name = "roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role {

    @Id
    private Long id;

    @Column(name = "role_code", nullable = false, length = 30)
    private String roleCode;

    @Column(name = "role_name", nullable = false, length = 50)
    private String roleName;
}
