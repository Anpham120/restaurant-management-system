/**
 * Xác thực và phân quyền: đăng nhập, JWT, đổi mật khẩu, chống dò mật khẩu.
 * API cho module khác: các lớp ở package gốc, chủ yếu {@link com.restaurant.core.security.CurrentUser} (p_actor của hàm rc_*).
 * Package con (controller, service, repository, dto) là nội bộ.
 */
@ApplicationModule(displayName = "Bảo mật", allowedDependencies = "common")
package com.restaurant.core.security;

import org.springframework.modulith.ApplicationModule;
