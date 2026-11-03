/**
 * Module Kho. Package con (controller, service, repository, dto) là nội bộ: module khác không được gọi vào.
 * Nghiệp vụ tiền/tồn/trạng thái nằm trong hàm rc_* của database; module chỉ gọi hàm đó qua repository.
 */
@ApplicationModule(displayName = "Kho", allowedDependencies = {"common", "security", "menu"})
package com.restaurant.core.inventory;

import org.springframework.modulith.ApplicationModule;
