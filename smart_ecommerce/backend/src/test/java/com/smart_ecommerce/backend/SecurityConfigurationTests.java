package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.config.SecurityConfigurationValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigurationTests {

    @ParameterizedTest
    @MethodSource("requiredProperties")
    void missingRequiredPropertyIsRejected(String property, String variable) {
        Map<String, Object> properties = validProperties();
        properties.remove(property);

        IllegalArgumentException exception = validateFailure(properties);

        assertTrue(exception.getMessage().contains(variable));
    }

    @ParameterizedTest
    @MethodSource("requiredProperties")
    void blankRequiredPropertyIsRejected(String property, String variable) {
        Map<String, Object> properties = validProperties();
        properties.put(property, " \t\n");

        IllegalArgumentException exception = validateFailure(properties);

        assertTrue(exception.getMessage().contains(variable));
    }

    @ParameterizedTest
    @MethodSource("requiredProperties")
    void unresolvedRequiredPropertyIsRejectedWithoutLeakingPlaceholder(String property, String variable) {
        Map<String, Object> properties = validProperties();
        properties.put(property, "${TEST_ONLY_UNRESOLVED_PROPERTY}");

        IllegalArgumentException exception = validateFailure(properties);

        assertTrue(exception.getMessage().contains(variable));
    }

    @ParameterizedTest
    @ValueSource(strings = {"test-only-invalid-key%", "test-only-invalid-key===="})
    void invalidBase64JwtKeyIsRejected(String value) {
        Map<String, Object> properties = validProperties();
        properties.put("app.jwt.secret", value);

        validateFailure(properties);
    }

    @Test
    void jwtKeyMustDecodeToAtLeast32Bytes() {
        Map<String, Object> properties = validProperties();
        properties.put("app.jwt.secret", dummyKey(31));

        validateFailure(properties);
    }

    @ParameterizedTest
    @ValueSource(strings = {"test-only-not-a-number", "0", "-1", "9223372036854775807",
            "9223372036854775808"})
    void malformedNonPositiveOrOverflowingExpirationIsRejected(String value) {
        Map<String, Object> properties = validProperties();
        properties.put("app.jwt.expiration", value);

        IllegalArgumentException exception = validateFailure(properties);

        assertTrue(exception.getMessage().contains("JWT_EXPIRATION_MS"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mysql://localhost/test_only_database",
            "jdbc:postgresql:test_only_database",
            "jdbc:postgresql://localhost",
            "jdbc:postgresql://localhost/",
            "jdbc:postgresql:///test_only_database",
            "jdbc:postgresql://localhost:not-a-port/test_only_database",
            "jdbc:postgresql://localhost:0/test_only_database",
            "jdbc:postgresql://localhost:65536/test_only_database",
            "jdbc:postgresql://localhost:/test_only_database",
            "jdbc:postgresql://localhost/test_only_database/extra",
            "jdbc:postgresql://localhost/test_only_database#fragment",
            "jdbc:postgresql://bad host/test_only_database"
    })
    void malformedDatabaseUrlIsRejectedWithoutEchoingIt(String value) {
        Map<String, Object> properties = validProperties();
        properties.put("spring.datasource.url", value);

        validateFailure(properties);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://test_only_user:test_only_password@localhost/test_only_database",
            "jdbc:postgresql://localhost/test_only_database?user=test_only_user",
            "jdbc:postgresql://localhost/test_only_database?password=test_only_password",
            "jdbc:postgresql://localhost/test_only_database?sslpassword=test_only_password",
            "jdbc:postgresql://localhost/test_only_database?PASSWORD=test_only_password",
            "jdbc:postgresql://localhost/test_only_database?pass%77ord=test_only_password"
    })
    void credentialsInsideDatabaseUrlAreRejected(String value) {
        Map<String, Object> properties = validProperties();
        properties.put("spring.datasource.url", value);

        validateFailure(properties);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://localhost/test_only_database",
            "jdbc:postgresql://127.0.0.1:5432/test_only_database",
            "jdbc:postgresql://localhost:5432/test_only_database?sslmode=require&connectTimeout=5",
            "jdbc:postgresql://host1:5432,host2:5433/test_only_database?targetServerType=primary",
            "jdbc:postgresql://[::1]:5432/test_only_database?sslmode=disable"
    })
    void validDummyConfigurationAndNonCredentialUrlOptionsAreAccepted(String url) {
        Map<String, Object> properties = validProperties();
        properties.put("spring.datasource.url", url);

        assertDoesNotThrow(() -> SecurityConfigurationValidator.validate(environment(properties)));
    }

    @Test
    void missingJwtKeyStopsSpringBeforeInitializersBeansAndRunners() {
        Map<String, Object> properties = validProperties();
        properties.remove("app.jwt.secret");
        StartupProbe probe = new StartupProbe();

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> application(probe).run(arguments(properties)));

        assertStartupFailedBeforeContext(probe, exception, properties);
    }

    @ParameterizedTest
    @MethodSource("invalidStartupProperties")
    void invalidConfigurationStopsSpringBeforeInitializersBeansAndRunners(String property, String value) {
        Map<String, Object> properties = validProperties();
        properties.put(property, value);
        StartupProbe probe = new StartupProbe();

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> application(probe).run(arguments(properties)));

        assertStartupFailedBeforeContext(probe, exception, properties);
    }

    @Test
    void validDummyConfigurationStartsEmptySpringContextWithoutADatabase() {
        StartupProbe probe = new StartupProbe();

        try (ConfigurableApplicationContext context = application(probe).run(arguments(validProperties()))) {
            assertTrue(context.isActive());
            assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
            assertTrue(probe.initialized.get());
            assertTrue(probe.beanCreated.get());
            assertTrue(probe.runnerInvoked.get());
        }
    }

    @ParameterizedTest
    @MethodSource("requiredEnvironmentVariables")
    void packagedConfigurationWithoutRequiredVariableStopsBeforeContext(String variable, String property) {
        Map<String, Object> variables = validEnvironmentVariables();
        variables.remove(variable);
        StartupProbe probe = new StartupProbe();

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> applicationWithPackagedConfiguration(probe).run(arguments(variables)));

        assertTrue(exception.getMessage().contains(variable));
        assertStartupFailedBeforeContext(probe, exception, variables);
        assertTrue(exception.getMessage().contains(property));
    }

    @ParameterizedTest
    @MethodSource("requiredEnvironmentVariables")
    void packagedConfigurationWithBlankRequiredVariableStopsBeforeContext(String variable, String property) {
        Map<String, Object> variables = validEnvironmentVariables();
        variables.put(variable, " \t\n");
        StartupProbe probe = new StartupProbe();

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> applicationWithPackagedConfiguration(probe).run(arguments(variables)));

        assertTrue(exception.getMessage().contains(variable));
        assertStartupFailedBeforeContext(probe, exception, variables);
        assertTrue(exception.getMessage().contains(property));
    }

    @Test
    void packagedConfigurationResolvesSuppliedVariablesWithoutADatabase() {
        Map<String, Object> variables = validEnvironmentVariables();
        StartupProbe probe = new StartupProbe();

        try (ConfigurableApplicationContext context = applicationWithPackagedConfiguration(probe)
                .run(arguments(variables))) {
            requiredEnvironmentVariables().forEach(argument -> {
                Object[] names = argument.get();
                assertTrue(variables.get(names[0]).equals(context.getEnvironment().getProperty((String) names[1])),
                        "Required property must resolve from the supplied test variable");
            });
            assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
            assertTrue(probe.initialized.get());
            assertTrue(probe.beanCreated.get());
            assertTrue(probe.runnerInvoked.get());
        }
    }

    private static Stream<Arguments> requiredEnvironmentVariables() {
        return Stream.of(
                Arguments.of("DB_URL", "spring.datasource.url"),
                Arguments.of("DB_USERNAME", "spring.datasource.username"),
                Arguments.of("DB_PASSWORD", "spring.datasource.password"),
                Arguments.of("JWT_SECRET", "app.jwt.secret"));
    }

    private static Map<String, Object> validEnvironmentVariables() {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("DB_URL", "jdbc:postgresql://127.0.0.1:5432/test_only_database");
        variables.put("DB_USERNAME", "test_only_user");
        variables.put("DB_PASSWORD", "test_only_password_do_not_use");
        variables.put("JWT_SECRET", dummyKey(32));
        return variables;
    }

    private static SpringApplication applicationWithPackagedConfiguration(StartupProbe probe) {
        SpringApplication application = application(probe);
        // Load the real resource, but never inherit developer environment or scan application/JPA beans.
        application.setEnvironment(new MockEnvironment()
                .withProperty("spring.config.location", "classpath:/application.properties")
                .withProperty("logging.level.root", "OFF"));
        return application;
    }

    private static Stream<Arguments> requiredProperties() {
        return Stream.of(
                Arguments.of("app.jwt.secret", "JWT_SECRET"),
                Arguments.of("app.jwt.expiration", "JWT_EXPIRATION_MS"),
                Arguments.of("spring.datasource.url", "DB_URL"),
                Arguments.of("spring.datasource.username", "DB_USERNAME"),
                Arguments.of("spring.datasource.password", "DB_PASSWORD"));
    }

    private static Stream<Arguments> invalidStartupProperties() {
        return Stream.of(
                Arguments.of("app.jwt.secret", "test-only-invalid-key%"),
                Arguments.of("app.jwt.expiration", "0"),
                Arguments.of("spring.datasource.url", "jdbc:postgresql://localhost:not-a-port/test_only_database"),
                Arguments.of("spring.datasource.username", " "),
                Arguments.of("spring.datasource.password", " "));
    }

    private static IllegalArgumentException validateFailure(Map<String, Object> properties) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> SecurityConfigurationValidator.validate(environment(properties)));
        assertNull(exception.getCause());
        assertSanitized(exception, properties);
        return exception;
    }

    private static void assertStartupFailedBeforeContext(
            StartupProbe probe, RuntimeException exception, Map<String, Object> properties) {
        assertFalse(probe.initialized.get());
        assertFalse(probe.beanCreated.get());
        assertFalse(probe.runnerInvoked.get());
        assertTrue(exception instanceof IllegalArgumentException);
        assertNull(exception.getCause());
        assertSanitized(exception, properties);
    }

    private static void assertSanitized(Throwable exception, Map<String, Object> properties) {
        assertNotNull(exception.getMessage());
        for (Object value : properties.values()) {
            String text = String.valueOf(value);
            if (!text.isBlank()) {
                assertFalse(exception.getMessage().contains(text));
            }
        }
    }

    private static MockEnvironment environment(Map<String, Object> properties) {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-only-config", properties));
        return environment;
    }

    private static SpringApplication application(StartupProbe probe) {
        SpringApplication application = new SpringApplication(EmptyConfig.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        // Avoid reading the developer's system properties, environment or application config.
        application.setEnvironment(new MockEnvironment()
                .withProperty("spring.config.location", "optional:classpath:/test-only-no-config.properties")
                .withProperty("logging.level.root", "OFF"));
        application.addInitializers(context -> {
            probe.initialized.set(true);
            GenericApplicationContext generic = (GenericApplicationContext) context;
            generic.registerBean("testOnlyMarker", Object.class, () -> {
                probe.beanCreated.set(true);
                return new Object();
            });
            generic.registerBean("testOnlyRunner", ApplicationRunner.class,
                    () -> args -> probe.runnerInvoked.set(true));
        });
        return application;
    }

    private static String[] arguments(Map<String, Object> properties) {
        return properties.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
    }

    private static Map<String, Object> validProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("app.jwt.secret", dummyKey(32));
        properties.put("app.jwt.expiration", "86400000");
        properties.put("spring.datasource.url", "jdbc:postgresql://127.0.0.1:5432/test_only_database");
        properties.put("spring.datasource.username", "test_only_user");
        properties.put("spring.datasource.password", "test_only_password_do_not_use");
        return properties;
    }

    private static String dummyKey(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) (i + 1);
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EmptyConfig {
    }

    private static class StartupProbe {
        final AtomicBoolean initialized = new AtomicBoolean();
        final AtomicBoolean beanCreated = new AtomicBoolean();
        final AtomicBoolean runnerInvoked = new AtomicBoolean();
    }
}
