package com.smart_ecommerce.backend;

import com.smart_ecommerce.backend.entity.Role;
import com.smart_ecommerce.backend.entity.User;
import com.smart_ecommerce.backend.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTests {

    private static final long EXPIRATION_MS = 60_000L;
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @ParameterizedTest
    @CsvSource({"32, HS256", "48, HS384", "64, HS512"})
    void signingAlgorithmStillFollowsTheConfiguredKeySize(int keyBytes, String algorithm) {
        String encodedKey = testKey(keyBytes, 1);
        JwtService service = new JwtService(encodedKey, EXPIRATION_MS);

        Jws<Claims> parsed = parse(service.generateToken(user()), encodedKey);

        assertEquals(algorithm, parsed.getHeader().getAlgorithm());
    }

    @Test
    void generatedTokenPreservesUserClaimsAndConfiguredLifetime() {
        String encodedKey = testKey(32, 1);
        JwtService service = new JwtService(encodedKey, EXPIRATION_MS);
        long before = System.currentTimeMillis();

        String token = service.generateToken(user());
        long after = System.currentTimeMillis();
        Claims claims = parse(token, encodedKey).getPayload();

        assertEquals(USER_ID.toString(), claims.getSubject());
        assertEquals("jwt-test@example.test", claims.get("email", String.class));
        assertEquals("CUSTOMER,STAFF", claims.get("roles", String.class));
        assertEquals(Set.of("sub", "email", "roles", "iat", "exp"), claims.keySet());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertEquals(EXPIRATION_MS,
                claims.getExpiration().getTime() - claims.getIssuedAt().getTime());
        // JWT NumericDate stores whole seconds, so allow only serialization truncation.
        assertTrue(claims.getIssuedAt().getTime() >= before - 999L);
        assertTrue(claims.getIssuedAt().getTime() <= after);
    }

    @Test
    void validSignedTokenCanBeValidatedAndItsSubjectExtracted() {
        JwtService service = new JwtService(testKey(32, 1), EXPIRATION_MS);
        String token = service.generateToken(user());

        assertTrue(service.isTokenValid(token));
        assertEquals(USER_ID, service.extractUserId(token));
    }

    @Test
    void tokenSignedWithADifferentKeyIsRejected() {
        JwtService issuer = new JwtService(testKey(32, 1), EXPIRATION_MS);
        JwtService verifier = new JwtService(testKey(32, 2), EXPIRATION_MS);

        assertFalse(verifier.isTokenValid(issuer.generateToken(user())));
    }

    @Test
    void alreadyExpiredSignedTokenIsRejectedWithoutWaiting() {
        String encodedKey = testKey(32, 1);
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject(USER_ID.toString())
                .issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(secretKey(encodedKey))
                .compact();

        assertFalse(new JwtService(encodedKey, EXPIRATION_MS).isTokenValid(token));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-token", "one.two.three"})
    void malformedTokensAreRejected(String token) {
        assertFalse(new JwtService(testKey(32, 1), EXPIRATION_MS).isTokenValid(token));
    }

    @Test
    void signedTokenWithoutExpirationIsRejected() {
        String encodedKey = testKey(32, 1);
        String token = Jwts.builder()
                .subject(USER_ID.toString())
                .signWith(secretKey(encodedKey))
                .compact();

        assertFalse(new JwtService(encodedKey, EXPIRATION_MS).isTokenValid(token));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void missingOrBlankKeyFailsWithoutExposingItsValueOrDecoderCause(String encodedKey) {
        assertSanitizedKeyFailure(encodedKey);
    }

    @ParameterizedTest
    @ValueSource(strings = {"test-only-invalid-key%", "test-only-invalid-key====", "é"})
    void invalidBase64KeyFailsWithoutExposingItsValueOrDecoderCause(String encodedKey) {
        assertSanitizedKeyFailure(encodedKey);
    }

    @Test
    void base64KeyWithEmbeddedWhitespaceIsRejected() {
        String encodedKey = testKey(32, 1);
        assertSanitizedKeyFailure(encodedKey.substring(0, 10) + "\n" + encodedKey.substring(10));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 16, 31})
    void validBase64KeyBelowTheMinimumSizeFailsWithoutExposingIt(int keyBytes) {
        assertSanitizedKeyFailure(testKey(keyBytes, 1));
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE})
    void nonPositiveOrOverflowingExpirationIsRejected(long expiration) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new JwtService(testKey(32, 1), expiration));

        assertTrue(exception.getMessage().contains("JWT_EXPIRATION_MS"));
        assertNull(exception.getCause());
    }

    private void assertSanitizedKeyFailure(String encodedKey) {
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> new JwtService(encodedKey, EXPIRATION_MS));

        assertNotNull(exception.getMessage());
        assertFalse(exception.getMessage().isBlank());
        assertNull(exception.getCause());
        if (encodedKey != null && !encodedKey.isBlank()) {
            assertFalse(exception.getMessage().contains(encodedKey));
        }
    }

    private static Jws<Claims> parse(String token, String encodedKey) {
        return Jwts.parser()
                .verifyWith(secretKey(encodedKey))
                .build()
                .parseSignedClaims(token);
    }

    private static SecretKey secretKey(String encodedKey) {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(encodedKey));
    }

    private static String testKey(int size, int seed) {
        // Deterministic dummy key material is used only by these isolated unit tests.
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) (i + seed);
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static User user() {
        LinkedHashSet<Role> roles = new LinkedHashSet<>(List.of(
                Role.builder().name("CUSTOMER").build(),
                Role.builder().name("STAFF").build()));
        return User.builder()
                .id(USER_ID)
                .email("jwt-test@example.test")
                .roles(roles)
                .build();
    }
}
