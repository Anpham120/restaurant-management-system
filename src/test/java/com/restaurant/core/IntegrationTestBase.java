package com.restaurant.core;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Nền cho integration test: một container PostgreSQL 17 dùng chung cho mọi test, khởi tạo ĐÚNG như docker compose
 * (docs/04 → 09 → 10 + docker/db-init/40-app-logins.sh). Ứng dụng kết nối bằng tài khoản thuộc rc_app;
 * dữ liệu mẫu được tạo bằng tài khoản quản trị qua {@link #admin()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    protected static final String APP_USER = "rms_api_test";
    private static final String APP_PASSWORD = "app-test-password";
    protected static final String WEBHOOK_USER = "rms_sepay_test";
    private static final String WEBHOOK_PASSWORD = "webhook-test-password";
    protected static final String SEPAY_API_KEY = "test-sepay-api-key";

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("restaurant_core")
            .withEnv("DB_APP_USER", APP_USER)
            .withEnv("DB_APP_PASSWORD", APP_PASSWORD)
            .withEnv("DB_WEBHOOK_USER", WEBHOOK_USER)
            .withEnv("DB_WEBHOOK_PASSWORD", WEBHOOK_PASSWORD)
            .withCopyFileToContainer(MountableFile.forHostPath("docs/04-schema-v4.1.sql"), "/docker-entrypoint-initdb.d/10-schema-v4.1.sql")
            .withCopyFileToContainer(MountableFile.forHostPath("docs/09-acl-v4.1.sql"), "/docker-entrypoint-initdb.d/20-acl-v4.1.sql")
            .withCopyFileToContainer(MountableFile.forHostPath("docs/10-bao-cao-v4.1.sql"), "/docker-entrypoint-initdb.d/30-bao-cao-v4.1.sql")
            .withCopyFileToContainer(MountableFile.forHostPath("docker/db-init/40-app-logins.sh", 0755), "/docker-entrypoint-initdb.d/40-app-logins.sh");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_USER);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("app.jwt.secret", () -> "test-only-jwt-secret-0123456789-abcdefghij");
        registry.add("app.sepay.api-key", () -> SEPAY_API_KEY);
        registry.add("app.sepay.bank-account", () -> "0010000000355");
        registry.add("app.sepay.bank-name", () -> "Vietcombank");
        registry.add("app.sepay.webhook-db.url", POSTGRES::getJdbcUrl);
        registry.add("app.sepay.webhook-db.username", () -> WEBHOOK_USER);
        registry.add("app.sepay.webhook-db.password", () -> WEBHOOK_PASSWORD);
    }

    @Autowired
    protected MockMvc mvc;

    protected static TestData data;

    @BeforeAll
    static void initTestData() {
        if (data == null) {
            data = new TestData(admin());
        }
    }

    /** JdbcClient bằng tài khoản quản trị của container — chỉ để dựng dữ liệu test, không phải đường của ứng dụng. */
    protected static JdbcClient admin() {
        return JdbcClient.create(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    protected String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    protected String bearer(TestData.Staff staff) throws Exception {
        return "Bearer " + login(staff.username(), TestData.PASSWORD);
    }
}
