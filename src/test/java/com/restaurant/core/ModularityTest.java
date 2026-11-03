package com.restaurant.core;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Kiểm tra kiến trúc module (Spring Modulith), không cần Spring context hay database.
 * Lỗi khi: hai module phụ thuộc vòng, module gọi vào package nội bộ của module khác,
 * hoặc phụ thuộc không có trong allowedDependencies của package-info.java.
 */
class ModularityTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(RestaurantCoreApplication.class);

    @Test
    void moduleBoundariesAreRespected() {
        MODULES.verify();
    }

    /** Sơ đồ module (PlantUML) và mô tả từng module, ghi vào target/spring-modulith-docs. */
    @Test
    void writeModuleDocumentation() {
        new Documenter(MODULES)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
    }
}
