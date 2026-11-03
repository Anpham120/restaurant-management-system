/**
 * Module Bàn & gọi món. Package con (controller, service, repository, dto) là nội bộ: module khác không được gọi vào.
 * Nghiệp vụ tiền/tồn/trạng thái nằm trong hàm rc_* của database; module chỉ gọi hàm đó qua repository.
 */
@ApplicationModule(displayName = "Bàn & gọi món", allowedDependencies = {"common", "security", "menu", "inventory"})
package com.restaurant.core.order;

import org.springframework.modulith.ApplicationModule;
