package com.smart_ecommerce.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/** Google-issued ID tokens are validated against Google's public, rotating signing keys. */
@Service
public class GoogleIdTokenService {
    private final String clientId;
    private final NimbusJwtDecoder decoder;

    public GoogleIdTokenService(@Value("${app.google.client-id:}") String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
        decoder = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> googleClaims = jwt -> {
            String issuer = jwt.getIssuer() == null ? "" : jwt.getIssuer().toString();
            boolean issuerValid = issuer.equals("https://accounts.google.com")
                    || issuer.equals("accounts.google.com");
            boolean audienceValid = jwt.getAudience().contains(this.clientId);
            String authorizedParty = jwt.getClaimAsString("azp");
            boolean partyValid = authorizedParty == null || authorizedParty.equals(this.clientId);
            if (issuerValid && audienceValid && partyValid && !this.clientId.isBlank()) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Google ID Token issuer/audience is invalid", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), googleClaims));
    }

    public GoogleIdentity verify(String idToken) {
        if (clientId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GOOGLE_CLIENT_ID chưa được cấu hình ở backend");
        }
        if (idToken == null || idToken.isBlank() || idToken.length() > 16384) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google ID Token không hợp lệ");
        }
        try {
            Jwt jwt = decoder.decode(idToken);
            String sub = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            Object verified = jwt.getClaim("email_verified");
            if (sub == null || sub.isBlank() || email == null || email.isBlank()
                    || !Boolean.TRUE.equals(verified)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Google chưa xác minh email hoặc thông tin tài khoản không hợp lệ");
            }
            email = email.trim().toLowerCase(Locale.ROOT);
            if (email.length() > 150) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Email Google vượt quá giới hạn 150 ký tự");
            }
            String name = jwt.getClaimAsString("name");
            if (name == null || name.isBlank()) name = email.split("@", 2)[0];
            name = name.trim();
            if (name.length() > 150) name = name.substring(0, 150);
            String picture = jwt.getClaimAsString("picture");
            // Do not permit storing an arbitrary, unsanitized URL into the profile.
            if (picture != null && (!picture.startsWith("https://") || picture.length() > 2048)) {
                picture = null;
            }
            return new GoogleIdentity(sub, email, name, picture);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            // Never echo a credential or internal verification details to the caller.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Google ID Token không hợp lệ hoặc đã hết hạn");
        }
    }

    public record GoogleIdentity(String sub, String email, String name, String picture) { }
}
