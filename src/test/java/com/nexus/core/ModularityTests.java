package com.nexus.core;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    @Test
    void verifyModuleStructure() {
        ApplicationModules.of(NexusBankApplication.class).verify();
    }
}
