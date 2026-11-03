package com.restaurant.core.employee.service;

import java.time.LocalDate;
import java.util.List;

import com.restaurant.core.common.response.PageResponse;
import com.restaurant.core.employee.dto.request.CreateEmployeeRequest;
import com.restaurant.core.employee.dto.request.DepartmentRequest;
import com.restaurant.core.employee.dto.request.UpdateEmployeeRequest;
import com.restaurant.core.employee.dto.response.DepartmentResponse;
import com.restaurant.core.employee.dto.response.EmployeeResponse;
import com.restaurant.core.employee.dto.response.RoleResponse;

/**
 * Hồ sơ nhân viên, tài khoản, vai trò, phòng ban. actorId luôn là CurrentUser.employeeId(), không bao giờ lấy từ request.
 * Owner: Nguyễn Quang Hiếu.
 */
public interface EmployeeService {

    List<DepartmentResponse> getAllDepartments();

    DepartmentResponse createDepartment(DepartmentRequest request);

    DepartmentResponse updateDepartment(long id, DepartmentRequest request);

    List<RoleResponse> getAllRoles();

    /** status null = mọi trạng thái. Mới nhất trước. */
    PageResponse<EmployeeResponse> getEmployees(String status, int page, int size);

    EmployeeResponse getEmployeeById(long id);

    /** Tạo nhân viên + tài khoản + vai trò trong một transaction. */
    EmployeeResponse createEmployee(CreateEmployeeRequest request);

    EmployeeResponse updateEmployee(long id, long actorId, UpdateEmployeeRequest request);

    /** Cho nghỉ việc và hủy các ca đã xếp sau ngày nghỉ. */
    EmployeeResponse markEmployeeAsLeft(long id, long actorId, LocalDate endDate);

    EmployeeResponse updateAccountRoles(long accountId, long actorId, List<String> roleCodes);

    EmployeeResponse lockAccount(long accountId, long actorId);

    EmployeeResponse unlockAccount(long accountId);

    /** Đặt mật khẩu tạm; người dùng phải đổi khi đăng nhập lại. */
    EmployeeResponse resetAccountPassword(long accountId, String newPassword);
}
