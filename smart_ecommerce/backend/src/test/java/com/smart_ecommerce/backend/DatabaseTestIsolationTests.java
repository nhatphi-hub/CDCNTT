package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.config.SecurityConfigurationValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** No test in this class starts a datasource or accesses any PostgreSQL instance. */
class DatabaseTestIsolationTests {

    private final IsolatedDatabaseTestEnvironmentPostProcessor guard =
            new IsolatedDatabaseTestEnvironmentPostProcessor();

    @ParameterizedTest
    @ValueSource(strings = {"TEST_DB_URL", "TEST_DB_USERNAME", "TEST_DB_PASSWORD"})
    void everyTestDatabaseVariableIsRequiredEvenWhenApplicationCredentialsExist(String missing) {
        MockEnvironment environment = environment();
        environment.getPropertySources().addFirst(new MapPropertySource("missing", Map.of(missing, "")));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> guard.postProcessEnvironment(environment, application()));
        assertTrue(exception.getMessage().contains(missing));
        assertFalse(exception.getMessage().contains("unit-only-password"));
        assertNull(exception.getCause());
        assertNull(environment.getPropertySources().get("isolated-database-tests"));
    }

    @Test
    void absentTestVariablesDoNotFallBackToDeveloperDatabase() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DB_URL", "jdbc:postgresql://localhost:5432/smart_ecommerce")
                .withProperty("DB_USERNAME", "unit-only-user")
                .withProperty("DB_PASSWORD", "unit-only-password");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> guard.postProcessEnvironment(environment, application()));
        assertTrue(exception.getMessage().contains("TEST_DB_URL"));
        assertNull(exception.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:postgresql://localhost:5432/smart_ecommerce",
            "jdbc:postgresql://localhost:5432/another_database",
            "jdbc:postgresql://localhost/smart_ecommerce_test?dbname=smart_ecommerce",
            "jdbc:postgresql://localhost/smart_ecommerce_test?PGDBNAME=smart_ecommerce",
            "jdbc:postgresql://localhost/smart_ecommerce_test?%64bname=smart_ecommerce",
            "jdbc:postgresql://localhost/smart_ecommerce_test?host=another-host",
            "jdbc:postgresql://localhost/smart_ecommerce_test?PGHOST=another-host",
            "jdbc:postgresql://localhost/smart_ecommerce_test?port=5433",
            "jdbc:postgresql://localhost/smart_ecommerce_test?PGPORT=5433",
            "jdbc:postgresql://localhost/smart_ecommerce_test?service=another-service",
            "jdbc:postgresql://localhost:5432/smart_ecommerce_test?password=unit-only-password",
            "jdbc:postgresql://localhost:bad/smart_ecommerce_test",
            "jdbc:postgresql://unit-only-user:unit-only-password@localhost/smart_ecommerce_test",
            "jdbc:h2:mem:smart_ecommerce_test"})
    void rejectsTargetsThatAreNotAnExplicitIsolatedPostgresqlDatabase(String url) {
        MockEnvironment environment = environment().withProperty("TEST_DB_URL", url);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> guard.postProcessEnvironment(environment, application()));
        assertTrue(exception.getMessage().contains("TEST_DB_URL"));
        assertFalse(exception.getMessage().contains(url));
        assertFalse(exception.getMessage().contains("unit-only-password"));
        assertNull(exception.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"smart_ecommerce_test", "smart_ecommerce_test_jwt"})
    void dedicatedTestVariablesOverrideApplicationCredentialsAndDisableBootstrap(String database) {
        MockEnvironment environment = environment().withProperty("TEST_DB_URL",
                "jdbc:postgresql://localhost:5432/" + database);
        guard.postProcessEnvironment(environment, application());
        assertEquals("jdbc:postgresql://localhost:5432/" + database,
                environment.getProperty("spring.datasource.url"));
        assertEquals("unit-only-test-user", environment.getProperty("spring.datasource.username"));
        assertEquals("unit-only-test-password", environment.getProperty("spring.datasource.password"));
        assertEquals(environment.getProperty("spring.datasource.url"),
                environment.getProperty("spring.datasource.hikari.jdbc-url"));
        assertEquals("unit-only-test-user", environment.getProperty("spring.datasource.hikari.username"));
        assertEquals("unit-only-test-password", environment.getProperty("spring.datasource.hikari.password"));
        assertEquals("", environment.getProperty("app.bootstrap-admin.email"));
        assertEquals("", environment.getProperty("app.bootstrap-admin.password"));
        assertEquals(64, Base64.getDecoder().decode(environment.getProperty("app.jwt.secret")).length);
        assertNotEquals("unit-only-application-key", environment.getProperty("app.jwt.secret"));
        assertDoesNotThrow(() -> SecurityConfigurationValidator.validate(environment));
    }

    @Test
    void testGuardIsAutoDiscoveredBeforeMainApplicationContextCanBeCreated() {
        AtomicBoolean contextCreated = new AtomicBoolean();
        SpringApplication application = application();
        // This test intentionally cannot reach refresh, component scanning, JPA or runners.
        MockEnvironment environment = new MockEnvironment();
        Map<String, Object> blankTestVariables = new HashMap<>();
        for (String variable : new String[]{"TEST_DB_URL", "TEST_DB_USERNAME", "TEST_DB_PASSWORD"}) {
            blankTestVariables.put(variable, "");
        }
        environment.getPropertySources().addFirst(new MapPropertySource("no-test-database", blankTestVariables));
        application.setEnvironment(environment);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        application.setApplicationContextFactory(type -> {
            contextCreated.set(true);
            throw new AssertionError("Full application context creation must never be reached by this test");
        });

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> application.run("--logging.level.root=OFF"));
        assertTrue(exception.getMessage().contains("TEST_DB_URL"));
        assertFalse(contextCreated.get());
        assertNull(exception.getCause());
    }

    private static SpringApplication application() {
        return new SpringApplication(SmartEcommerceApplication.class);
    }

    private static MockEnvironment environment() {
        return new MockEnvironment()
                .withProperty("TEST_DB_URL", "jdbc:postgresql://localhost:5432/smart_ecommerce_test")
                .withProperty("TEST_DB_USERNAME", "unit-only-test-user")
                .withProperty("TEST_DB_PASSWORD", "unit-only-test-password")
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/smart_ecommerce")
                .withProperty("spring.datasource.username", "unit-only-user")
                .withProperty("spring.datasource.password", "unit-only-password")
                .withProperty("spring.datasource.hikari.jdbc-url", "jdbc:postgresql://localhost/smart_ecommerce")
                .withProperty("spring.datasource.hikari.username", "unit-only-user")
                .withProperty("spring.datasource.hikari.password", "unit-only-password")
                .withProperty("app.jwt.secret", "unit-only-application-key")
                .withProperty("app.bootstrap-admin.email", "bootstrap@example.test")
                .withProperty("app.bootstrap-admin.password", "unit-only-bootstrap-password");
    }
}
