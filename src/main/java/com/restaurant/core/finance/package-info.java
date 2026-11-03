/**
 * Module Tài chính. Package con (controller, service, repository, dto) là nội bộ: module khác không được gọi vào.
 * Nghiệp vụ tiền/tồn/trạng thái nằm trong hàm rc_* của database; module chỉ gọi hàm đó qua repository.
 */
@ApplicationModule(displayName = "Tài chính", allowedDependencies = {"common", "security", "order"})
package com.restaurant.core.finance;

import org.springframework.modulith.ApplicationModule;
