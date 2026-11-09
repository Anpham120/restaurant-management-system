package com.restaurant.core.employee.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Bảng employees. Ràng buộc ngày (end_date ≥ hire_date, LEFT phải có end_date) do CHECK của database giữ. */
@Entity
@Table(name = "employees")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", nullable = false, length = 20, updatable = false)
    private String employeeCode;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(length = 10)
    private String gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 15)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(length = 255)
    private String address;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_status", nullable = false, length = 20)
    private WorkStatus workStatus = WorkStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    public Employee(String employeeCode, String fullName, Department department) {
        this.employeeCode = employeeCode;
        this.fullName = fullName;
        this.department = department;
    }

    /** Sửa thông tin làm việc: họ tên, phòng ban, trạng thái (nghỉ việc dùng {@link #markAsLeft}). */
    public void updateWorkInfo(String fullName, Department department, WorkStatus workStatus) {
        this.fullName = fullName;
        this.department = department;
        this.workStatus = workStatus;
    }

    /** Sửa thông tin cá nhân; các trường văn bản đã được chuẩn hóa (rỗng → null) ở Service. */
    public void updateProfile(String gender, LocalDate dateOfBirth, String phone, String email, String address, LocalDate hireDate) {
        this.gender = gender;
        this.dateOfBirth = dateOfBirth;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.hireDate = hireDate;
    }

    public boolean hasLeft() {
        return workStatus == WorkStatus.LEFT;
    }

    /** Nghỉ việc: không quay lại được; đăng nhập và mọi hàm rc_* của người này bị từ chối từ đây. */
    public void markAsLeft(LocalDate endDate) {
        this.workStatus = WorkStatus.LEFT;
        this.endDate = endDate;
    }
}
