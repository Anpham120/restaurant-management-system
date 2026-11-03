/**
 * Hạ tầng dùng chung: định dạng response, xử lý lỗi, CORS, đo thời gian Service.
 * Module mở (OPEN): mọi module khác dùng trực tiếp các package con. Không chứa nghiệp vụ, không phụ thuộc module nào.
 */
@ApplicationModule(displayName = "Dùng chung", type = ApplicationModule.Type.OPEN, allowedDependencies = {})
package com.restaurant.core.common;

import org.springframework.modulith.ApplicationModule;
