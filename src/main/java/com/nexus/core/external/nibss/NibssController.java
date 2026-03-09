package com.nexus.core.external.nibss;

import com.nexus.core.common.BeneficiaryResponse;
import com.nexus.core.transaction.NibssService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/external/nibss")
public class NibssController {

    private final NibssService nibssService;

    public NibssController(NibssService nibssService) {
        this.nibssService = nibssService;
    }

    @GetMapping("/resolve")
    public ResponseEntity<BeneficiaryResponse> resolveAccount(
            @RequestParam String bankCode,
            @RequestParam String accountNumber) {
        return ResponseEntity.ok(nibssService.resolveAccount(bankCode, accountNumber));
    }
}
