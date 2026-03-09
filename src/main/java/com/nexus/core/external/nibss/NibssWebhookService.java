package com.nexus.core.external.nibss;

interface NibssWebhookService {
    void processTransferOutCallback(NibssTransferOutCallbackRequest request);
    void processTransferIn(NibssTransferInRequest request);
}
