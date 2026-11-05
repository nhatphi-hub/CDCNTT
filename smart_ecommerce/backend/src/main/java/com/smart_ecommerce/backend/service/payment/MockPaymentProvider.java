package com.smart_ecommerce.backend.service.payment;

import com.smart_ecommerce.backend.dto.payment.PaymentCallbackRequest;
import com.smart_ecommerce.backend.entity.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

@Component
public class MockPaymentProvider implements PaymentProvider {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Set<String> ONLINE_METHODS =
            Set.of("BANK_TRANSFER", "VNPAY", "MOMO");

    private final String callbackSecret;

    public MockPaymentProvider(
            @Value("${app.payment.mock-callback-secret:}") String callbackSecret
    ) {
        this.callbackSecret = callbackSecret == null ? "" : callbackSecret.trim();
    }

    @Override
    public String providerCode() {
        return "MOCK";
    }

    @Override
    public boolean supportsPaymentMethod(String paymentMethod) {
        return paymentMethod != null
                && ONLINE_METHODS.contains(paymentMethod.trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public void initialize(Payment payment) {
        payment.setProvider(providerCode());
        payment.setStatus("PENDING");
    }

    @Override
    public boolean verifyCallback(PaymentCallbackRequest request, String signature) {
        if (callbackSecret.isBlank() || request == null || signature == null
                || !signature.matches("(?i)[0-9a-f]{64}")) {
            return false;
        }
        try {
            byte[] expected = HexFormat.of().parseHex(signCallback(callbackSecret, request));
            byte[] supplied = HexFormat.of().parseHex(signature);
            return MessageDigest.isEqual(expected, supplied);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static String signCallback(String secret, PaymentCallbackRequest request) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(canonicalPayload(request)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể ký callback thanh toán", exception);
        }
    }

    public static String canonicalPayload(PaymentCallbackRequest request) {
        return String.join("|",
                request.paymentId().toString(),
                request.providerTransactionId().trim(),
                request.amount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                request.currency().trim().toUpperCase(Locale.ROOT),
                request.status().trim().toUpperCase(Locale.ROOT));
    }
}
