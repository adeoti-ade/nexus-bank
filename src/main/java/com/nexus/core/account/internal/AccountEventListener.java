package com.nexus.core.account.internal;

import com.nexus.core.account.AccountService;
import com.nexus.core.auth.UserRegisteredEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
class AccountEventListener {

    private static final Logger log = LoggerFactory.getLogger(AccountEventListener.class);
    private final AccountService accountService;

    AccountEventListener(AccountService accountService) {
        this.accountService = accountService;
    }

    @ApplicationModuleListener
    void onUserRegistered(UserRegisteredEvent event) {
        log.info("Creating account for registered user: {}", event.userId());
        try {
            accountService.createAccount(event.userId());
            log.info("Successfully created account for user: {}", event.userId());
        } catch (Exception e) {
            log.error("Failed to create account for user: {}", event.userId(), e);
        }
    }
}
