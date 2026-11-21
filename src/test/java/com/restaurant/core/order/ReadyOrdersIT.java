package com.restaurant.core.order;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReadyOrdersIT extends OrderApiTestBase {

    @Test
    void waiterSeesOnlyReadyItemsAcrossTablesUntilTheyAreServed() throws Exception {
        String readyUrl = "/api/order-items/ready";
        long[] firstTable = openTable();
        long[] secondTable = openTable();
        long dishId = data.dish(10000, null, null).dailyMenuItemId();
        long first = submitOne(firstTable[2], dishId, 2);
        long second = submitOne(secondTable[2], dishId, 1);
        long pending = submitOne(secondTable[2], dishId, 1);

        for (long id : new long[] {first, second}) {
            postEmpty(kitchen, "/api/order-items/{id}/accept", id).andExpect(status().isOk());
            postEmpty(kitchen, "/api/order-items/{id}/start-cooking", id).andExpect(status().isOk());
            postEmpty(kitchen, "/api/order-items/{id}/finish", id).andExpect(status().isOk());
        }

        String firstCode = read(getAs(waiter, "/api/tables/{id}", firstTable[0]), "$.data.code");
        String secondCode = read(getAs(waiter, "/api/tables/{id}", secondTable[0]), "$.data.code");
        getAs(waiter, readyUrl).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + first + ")].tableCode").value(hasItem(firstCode)))
                .andExpect(jsonPath("$.data[?(@.id == " + second + ")].tableCode").value(hasItem(secondCode)))
                .andExpect(jsonPath("$.data[*].id").value(not(hasItem((int) pending))));
        getAs(manager, readyUrl).andExpect(status().isForbidden());
        getAs(cashier, readyUrl).andExpect(status().isForbidden());
        getAs(kitchen, "/api/kitchen/queue?status=READY").andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id").value(hasItem((int) first)));

        postEmpty(waiter, "/api/order-items/{id}/serve", first).andExpect(status().isOk());
        getAs(waiter, readyUrl).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id").value(not(hasItem((int) first))))
                .andExpect(jsonPath("$.data[*].id").value(hasItem((int) second)));
    }
}
