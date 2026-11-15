/**
 * Module Thực đơn. Package con (controller, service, repository, dto) là nội bộ: module khác không được gọi vào.
 * Nghiệp vụ nằm trong service/impl (Java); repository chỉ đọc/ghi dữ liệu. Trigger trong DB chỉ là lưới an toàn cuối cùng.
 * Module khác chỉ dùng các interface ở package gốc (docs/backend/MODULE_BOUNDARIES.md).
 */
@ApplicationModule(displayName = "Thực đơn", allowedDependencies = {"common", "security"})
package com.restaurant.core.menu;

import org.springframework.modulith.ApplicationModule;
