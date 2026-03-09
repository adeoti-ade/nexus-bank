package com.nexus.core.external.nibss;

import com.nexus.core.common.WebhookSignatureException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
class NibssWebhookSignatureVerifier {

    private final NibssProperties nibssProperties;

    NibssWebhookSignatureVerifier(NibssProperties nibssProperties) {
        this.nibssProperties = nibssProperties;
    }

    void verify(String signatureHeader, byte[] body) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            throw new WebhookSignatureException("Missing or malformed X-NIBSS-Signature header");
        }

        String providedHex = signatureHeader.substring("sha256=".length());

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(nibssProperties.webhookSecret().getBytes(), "HmacSHA256"));
            byte[] expectedBytes = mac.doFinal(body);
            byte[] providedBytes = HexFormat.of().parseHex(providedHex);

            if (!MessageDigest.isEqual(expectedBytes, providedBytes)) {
                throw new WebhookSignatureException("Invalid webhook signature");
            }
        } catch (WebhookSignatureException e) {
            throw e;
        } catch (Exception e) {
            throw new WebhookSignatureException("Signature verification failed: " + e.getMessage());
        }
    }
}
