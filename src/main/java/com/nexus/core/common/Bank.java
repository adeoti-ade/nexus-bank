package com.nexus.core.common;

import lombok.Getter;

@Getter
public enum Bank {
    NEXUS("000", "Nexus Bank"),
    FIRSTBANK("011", "First Bank of Nigeria"),
    GTBANK("058", "Guaranty Trust Bank"),
    ZENITH("057", "Zenith Bank"),
    ACCESS("044", "Access Bank"),
    UBA("033", "United Bank for Africa"),
    KUDA("090267", "Kuda Bank");

    private final String code;
    private final String name;

    Bank(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static Bank fromCode(String code) {
        for (Bank bank : values()) {
            if (bank.getCode().equals(code)) {
                return bank;
            }
        }
        throw new IllegalArgumentException("Unknown Bank Code: " + code);
    }
}
