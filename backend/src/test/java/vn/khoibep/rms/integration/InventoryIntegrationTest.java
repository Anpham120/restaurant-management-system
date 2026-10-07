package vn.khoibep.rms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import vn.khoibep.rms.IntegrationTest;

/** US-06 and BR-19, BR-20. */
class InventoryIntegrationTest extends IntegrationTest {

    @Test
    void stockChangesOnlyThroughMovements() throws Exception {
        long id = readLong(post("/api/inventory-items", as("quanly"),
                        Map.of("name", unique("Hành lá"), "unit", "kg", "minQuantity", 5))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.quantity").value(0.0))
                        .andExpect(jsonPath("$.lowStock").value(true)),
                "$.id");
        String movements = "/api/inventory-items/" + id + "/movements";

        post(movements, as("quanly"), Map.of("type", "IN", "quantity", 10, "note", "Nhập chợ đầu mối"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(10.0))
                .andExpect(jsonPath("$.lowStock").value(false));
        post(movements, as("quanly"), Map.of("type", "OUT", "quantity", 3))
                .andExpect(jsonPath("$.quantity").value(7.0));

        ResultActions tooMuch = post(movements, as("quanly"), Map.of("type", "OUT", "quantity", 20))
                .andExpect(status().isConflict());
        assertThat(body(tooMuch)).contains("Xuất vượt tồn");

        // Stock count of 4 records an adjustment of -3.
        post(movements, as("quanly"), Map.of("type", "ADJUST", "quantity", 4, "note", "Kiểm kê cuối ngày"))
                .andExpect(jsonPath("$.quantity").value(4.0))
                .andExpect(jsonPath("$.lowStock").value(true));

        get(movements, as("quanly"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].type").value("ADJUST"))
                .andExpect(jsonPath("$[0].quantityChange").value(-3.0))
                .andExpect(jsonPath("$[0].createdByName").value("Nguyễn Văn Quản"));
    }

    /** FR-09.3: the history holds the newest 200 movements; older ones stay in the database. */
    @Test
    void historyListsTheNewest200Movements() throws Exception {
        long id = readLong(post("/api/inventory-items", as("quanly"),
                        Map.of("name", unique("Muối"), "unit", "kg", "minQuantity", 5))
                        .andExpect(status().isCreated()),
                "$.id");
        String movements = "/api/inventory-items/" + id + "/movements";
        for (int i = 1; i <= 201; i++) {
            post(movements, as("quanly"), Map.of("type", "IN", "quantity", 1, "note", "Lần " + i))
                    .andExpect(status().isCreated());
        }

        get(movements, as("quanly"))
                .andExpect(jsonPath("$.length()").value(200))
                .andExpect(jsonPath("$[0].note").value("Lần 201"))
                .andExpect(jsonPath("$[199].note").value("Lần 2"));
    }

    @Test
    void onlyManagersWorkWithInventory() throws Exception {
        get("/api/inventory-items", as("phucvu")).andExpect(status().isForbidden());
        get("/api/inventory-items", as("bep")).andExpect(status().isForbidden());
        get("/api/inventory-items", as("quanly")).andExpect(status().isOk());
    }
}
