package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.config.SecurityConfigurationValidator;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/** Test classpath only: never inherit application credentials for full Boot tests. */
public final class IsolatedDatabaseTestEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        boolean loadsApplication = application.getAllSources().stream().anyMatch(source ->
                source == SmartEcommerceApplication.class
                        || SmartEcommerceApplication.class.getName().equals(source));
        if (!loadsApplication) {
            return;
        }

        String url = required(environment, "TEST_DB_URL");
        try {
            SecurityConfigurationValidator.validateDatabaseUrl(url);
            String database = URI.create(url.substring("jdbc:".length())).getPath().substring(1);
            if (!database.equals("smart_ecommerce_test")
                    && !database.startsWith("smart_ecommerce_test_")) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("TEST_DB_URL must explicitly target a PostgreSQL "
                    + "database named smart_ecommerce_test or smart_ecommerce_test_* "
                    + "without embedded credentials");
        }
        String username = required(environment, "TEST_DB_USERNAME");
        String password = required(environment, "TEST_DB_PASSWORD");
        byte[] key = new byte[64];
        new SecureRandom().nextBytes(key);
        String encodedKey;
        try {
            encodedKey = Base64.getEncoder().encodeToString(key);
        } finally {
            Arrays.fill(key, (byte) 0);
        }

        environment.getPropertySources().addFirst(new MapPropertySource("isolated-database-tests",
                Map.of("spring.datasource.url", url,
                        "spring.datasource.username", username,
                        "spring.datasource.password", password,
                        "spring.datasource.hikari.jdbc-url", url,
                        "spring.datasource.hikari.username", username,
                        "spring.datasource.hikari.password", password,
                        "app.jwt.secret", encodedKey,
                        "app.jwt.expiration", "86400000",
                        "app.bootstrap-admin.email", "",
                        "app.bootstrap-admin.password", "")));
    }

    private static String required(ConfigurableEnvironment environment, String variable) {
        String value;
        try {
            value = environment.getProperty(variable);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(variable + " is required for isolated database tests");
        }
        if (value == null || value.isBlank() || value.contains("${")) {
            throw new IllegalArgumentException(variable + " is required for isolated database tests; "
                    + "application DB_* credentials are never used as a fallback");
        }
        return value;
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
