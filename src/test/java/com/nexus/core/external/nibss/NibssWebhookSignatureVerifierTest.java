package com.nexus.core.external.nibss;

import com.nexus.core.common.WebhookSignatureException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class NibssWebhookSignatureVerifierTest {

    private static final String SECRET = "test-secret";
    private final NibssWebhookSignatureVerifier verifier =
            new NibssWebhookSignatureVerifier(new NibssProperties(SECRET, true, "http://localhost:8090"));

    @Test
    void validSignaturePasses() throws Exception {
        byte[] body = "{\"nipSessionId\":\"test\",\"amount\":100}".getBytes();
        String signature = computeHmac(SECRET, body);
        assertDoesNotThrow(() -> verifier.verify(signature, body));
    }

    @Test
    void wrongSignatureThrows() {
        byte[] body = "{\"nipSessionId\":\"test\"}".getBytes();
        String wrongSig = "sha256=" + "0".repeat(64);
        assertThrows(WebhookSignatureException.class, () -> verifier.verify(wrongSig, body));
    }

    @Test
    void nullSignatureHeaderThrows() {
        byte[] body = "{}".getBytes();
        assertThrows(WebhookSignatureException.class, () -> verifier.verify(null, body));
    }

    @Test
    void missingPrefixSignatureThrows() {
        byte[] body = "{}".getBytes();
        assertThrows(WebhookSignatureException.class, () -> verifier.verify("not-sha256=abc", body));
    }

    private String computeHmac(String secret, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
        byte[] hmacBytes = mac.doFinal(body);
        return "sha256=" + HexFormat.of().formatHex(hmacBytes);
    }
}
