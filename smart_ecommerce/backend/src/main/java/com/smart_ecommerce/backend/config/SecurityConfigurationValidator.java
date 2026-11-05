package com.smart_ecommerce.backend.config;

import io.jsonwebtoken.security.Keys;
import org.springframework.core.env.Environment;

import javax.crypto.SecretKey;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/** Validates configuration without logging values or opening a database connection. */
public final class SecurityConfigurationValidator {

    private static final Set<String> URL_OVERRIDE_PARAMETERS = Set.of(
            "user", "password", "sslpassword", "dbname", "pgdbname",
            "host", "pghost", "port", "pgport", "service");

    private SecurityConfigurationValidator() {
    }

    public static void validate(Environment environment) {
        validateJwtKey(required(environment, "app.jwt.secret", "JWT_SECRET"));
        validateExpiration(environment);
        validateDatabaseUrl(required(environment, "spring.datasource.url", "DB_URL"));
        required(environment, "spring.datasource.username", "DB_USERNAME");
        // This application's PostgreSQL configuration uses password authentication.
        required(environment, "spring.datasource.password", "DB_PASSWORD");
    }

    public static SecretKey validateJwtKey(String encodedSecret) {
        if (encodedSecret == null || encodedSecret.isBlank()) {
            throw new IllegalArgumentException("JWT_SECRET is required and must not be blank");
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            // Decoder exceptions can contain input details; do not retain their cause.
            throw new IllegalArgumentException("JWT_SECRET must be valid standard Base64");
        }

        try {
            if (decoded.length < 32) {
                throw new IllegalArgumentException(
                        "JWT_SECRET must decode to at least 32 bytes for HMAC signing");
            }
            // Keep JJWT's existing automatic HS256/HS384/HS512 key selection.
            return Keys.hmacShaKeyFor(decoded);
        } finally {
            Arrays.fill(decoded, (byte) 0);
        }
    }

    private static void validateExpiration(Environment environment) {
        String value = required(environment, "app.jwt.expiration", "JWT_EXPIRATION_MS");
        try {
            long expiration = Long.parseLong(value);
            if (expiration > 0 && expiration <= Long.MAX_VALUE - System.currentTimeMillis()) {
                return;
            }
        } catch (NumberFormatException exception) {
            // Do not include the rejected value in diagnostics.
        }
        throw new IllegalArgumentException(
                "JWT_EXPIRATION_MS must be positive and fit within the supported timestamp range");
    }

    public static void validateDatabaseUrl(String value) {
        try {
            if (value == null || !value.startsWith("jdbc:postgresql://")) {
                throw new IllegalArgumentException();
            }
            URI uri = URI.create(value.substring("jdbc:".length()));
            String path = uri.getPath();
            if (uri.getRawAuthority() == null || uri.getRawAuthority().isBlank()
                    || uri.getRawUserInfo() != null || uri.getRawAuthority().contains("@")
                    || path == null || path.length() <= 1 || path.substring(1).isBlank()
                    || path.substring(1).contains("/")
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            // Validate each server separately so PostgreSQL multi-host URLs still work.
            for (String address : uri.getRawAuthority().split(",", -1)) {
                URI server = URI.create("postgresql://" + address + "/");
                if (server.getHost() == null || server.getHost().isBlank()
                        || address.endsWith(":") || server.getPort() == 0
                        || server.getPort() > 65535) {
                    throw new IllegalArgumentException();
                }
            }
            if (uri.getRawQuery() != null) {
                for (String parameter : uri.getRawQuery().split("&")) {
                    String name = URLDecoder.decode(parameter.split("=", 2)[0],
                            StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                    if (URL_OVERRIDE_PARAMETERS.contains(name)) {
                        throw new IllegalArgumentException();
                    }
                }
            }
        } catch (IllegalArgumentException exception) {
            // URI parsing errors may echo a password embedded in a malformed URL.
            throw new IllegalArgumentException(
                    "DB_URL must be an explicit jdbc:postgresql://host[:port]/database URL "
                            + "without credentials or connection-target overrides; "
                            + "use DB_USERNAME and DB_PASSWORD");
        }
    }

    private static String required(Environment environment, String property, String variable) {
        String value;
        try {
            value = environment.getProperty(property);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(variable + " is required (" + property + ")");
        }
        if (value == null || value.isBlank() || value.contains("${")) {
            throw new IllegalArgumentException(variable + " is required and must not be blank ("
                    + property + ")");
        }
        return value;
    }
}
