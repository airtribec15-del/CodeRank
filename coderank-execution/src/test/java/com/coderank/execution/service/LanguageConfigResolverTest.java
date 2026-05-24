package com.coderank.execution.service;

import com.coderank.common.enums.Language;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

@DisplayName("LanguageConfigResolver")
class LanguageConfigResolverTest {

    private final LanguageConfigResolver resolver = new LanguageConfigResolver();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(resolver, "pythonImage", "python:3.11-slim");
        ReflectionTestUtils.setField(resolver, "javaImage", "openjdk:21-slim");
        ReflectionTestUtils.setField(resolver, "jsImage", "node:20-slim");
        ReflectionTestUtils.setField(resolver, "cppImage", "gcc:13");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Language.class)
    @DisplayName("resolves a non-null profile for all languages")
    void shouldResolveAllLanguages(Language language) {
        LanguageConfigResolver.LanguageProfile profile = resolver.resolve(language);
        assertThat(profile).isNotNull();
        assertThat(profile.dockerImage()).isNotBlank();
        assertThat(profile.sourceFileName()).isNotBlank();
        assertThat(profile.runCommand()).isNotBlank();
    }

    @Test void python_usesCorrectImage() {
        assertThat(resolver.resolve(Language.PYTHON).dockerImage()).isEqualTo("python:3.11-slim");
    }
    @Test void python_sourceFile() {
        assertThat(resolver.resolve(Language.PYTHON).sourceFileName()).isEqualTo("solution.py");
    }
    @Test void python_runCommand() {
        assertThat(resolver.resolve(Language.PYTHON).runCommand()).contains("python3");
    }

    @Test void java_usesCorrectImage() {
        assertThat(resolver.resolve(Language.JAVA).dockerImage()).isEqualTo("openjdk:21-slim");
    }
    @Test void java_sourceFile() {
        assertThat(resolver.resolve(Language.JAVA).sourceFileName()).isEqualTo("Main.java");
    }
    @Test void java_compileAndRun() {
        String cmd = resolver.resolve(Language.JAVA).runCommand();
        assertThat(cmd).contains("javac").contains("java Main");
    }

    @Test void javascript_usesCorrectImage() {
        assertThat(resolver.resolve(Language.JAVASCRIPT).dockerImage()).isEqualTo("node:20-slim");
    }
    @Test void javascript_sourceFile() {
        assertThat(resolver.resolve(Language.JAVASCRIPT).sourceFileName()).isEqualTo("solution.js");
    }
    @Test void javascript_runCommand() {
        assertThat(resolver.resolve(Language.JAVASCRIPT).runCommand()).contains("node");
    }

    @Test void cpp_usesCorrectImage() {
        assertThat(resolver.resolve(Language.CPP).dockerImage()).isEqualTo("gcc:13");
    }
    @Test void cpp_sourceFile() {
        assertThat(resolver.resolve(Language.CPP).sourceFileName()).isEqualTo("solution.cpp");
    }
    @Test void cpp_compileAndRun() {
        String cmd = resolver.resolve(Language.CPP).runCommand();
        assertThat(cmd).contains("g++").contains("./solution");
    }
}
