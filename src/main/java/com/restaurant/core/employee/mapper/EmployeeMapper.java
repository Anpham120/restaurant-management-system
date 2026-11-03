package com.restaurant.core.employee.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.restaurant.core.employee.dto.response.DepartmentResponse;
import com.restaurant.core.employee.dto.response.EmployeeResponse;
import com.restaurant.core.employee.dto.response.RoleResponse;
import com.restaurant.core.employee.entity.Account;
import com.restaurant.core.employee.entity.Department;
import com.restaurant.core.employee.entity.Employee;
import com.restaurant.core.employee.entity.Role;

/** Entity → DTO trả ra API. Entity không bao giờ được trả thẳng ra controller. */
@Component
public class EmployeeMapper {

    public DepartmentResponse toResponse(Department d) {
        return new DepartmentResponse(d.getId(), d.getName());
    }

    public RoleResponse toResponse(Role r) {
        return new RoleResponse(r.getId(), r.getRoleCode(), r.getRoleName());
    }

    /** account có thể null (nhân viên chưa có tài khoản). */
    public EmployeeResponse toResponse(Employee e, Account account) {
        List<String> roles = account == null ? List.of() : account.getRoles().stream().map(Role::getRoleCode).sorted().toList();
        return new EmployeeResponse(e.getId(), e.getEmployeeCode(), e.getFullName(), e.getGender(), e.getDateOfBirth(), e.getPhone(),
                e.getEmail(), e.getAddress(), e.getHireDate(), e.getEndDate(), e.getWorkStatus().name(),
                e.getDepartment().getId(), e.getDepartment().getName(),
                account == null ? null : account.getId(),
                account == null ? null : account.getUsername(),
                account == null ? null : account.getStatus().name(),
                account == null ? null : account.isMustChangePassword(),
                roles);
    }
}
