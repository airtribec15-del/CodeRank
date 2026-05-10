package com.coderank.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Language {

    PYTHON("python"),
    JAVA("java"),
    JAVASCRIPT("javascript"),
    CPP("cpp");

    private final String value;

    Language(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static Language fromValue(String value) {
        for (Language language : Language.values()) {
            if (language.value.equalsIgnoreCase(value)) {
                return language;
            }
        }
        throw new IllegalArgumentException("Unsupported language: " + value);
    }
}