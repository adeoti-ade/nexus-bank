package com.nexus.core.external.nibss;

import tools.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks/nibss")
class NibssWebhookController {

    private final NibssWebhookSignatureVerifier signatureVerifier;
    private final NibssWebhookService webhookService;
    private final ObjectMapper objectMapper;

    NibssWebhookController(NibssWebhookSignatureVerifier signatureVerifier,
                           NibssWebhookService webhookService,
                           ObjectMapper objectMapper) {
        this.signatureVerifier = signatureVerifier;
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/transfer-out/callback")
    ResponseEntity<Void> transferOutCallback(
            @RequestHeader(value = "X-NIBSS-Signature", required = false) String signature,
            @RequestBody byte[] body) {
        signatureVerifier.verify(signature, body);
        NibssTransferOutCallbackRequest request = objectMapper.readValue(body, NibssTransferOutCallbackRequest.class);
        webhookService.processTransferOutCallback(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transfer-in")
    ResponseEntity<Void> transferIn(
            @RequestHeader(value = "X-NIBSS-Signature", required = false) String signature,
            @RequestBody byte[] body) {
        signatureVerifier.verify(signature, body);
        NibssTransferInRequest request = objectMapper.readValue(body, NibssTransferInRequest.class);
        webhookService.processTransferIn(request);
        return ResponseEntity.ok().build();
    }
}
