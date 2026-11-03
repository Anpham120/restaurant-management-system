package com.restaurant.core.employee.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.restaurant.core.employee.entity.Role;

public interface RoleRepository extends JpaRepository<Role, Long> {

    List<Role> findByRoleCodeIn(Collection<String> roleCodes);
}
