// src/test/java/com/coderank/execution/service/LanguageConfigResolverTest.java
package com.coderank.execution.service;

import com.coderank.common.enums.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link LanguageConfigResolver}.
 *
 * <p>Verifies that each supported {@link Language} resolves to a non-null
 * {@link LanguageConfigResolver.LanguageProfile} with the configured Docker
 * image, the expected source file name, and a runnable shell command.
 */
@DisplayName("LanguageConfigResolver")
class LanguageConfigResolverTest {

    private LanguageConfigResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new LanguageConfigResolver();
        // @Value fields are not populated outside the Spring context
        ReflectionTestUtils.setField(resolver, "pythonImage", "python:3.11-slim");
        ReflectionTestUtils.setField(resolver, "javaImage",   "openjdk:21-slim");
        ReflectionTestUtils.setField(resolver, "jsImage",     "node:20-slim");
        ReflectionTestUtils.setField(resolver, "cppImage",    "gcc:13");
    }

    // ── Parametrized smoke test ─────────────────────────────────────────

    @ParameterizedTest(name = "{0} → non-null profile")
    @EnumSource(Language.class)
    @DisplayName("every Language enum value resolves to a non-null profile")
    void shouldResolveAllLanguages(Language language) {
        LanguageConfigResolver.LanguageProfile profile = resolver.resolve(language);

        assertThat(profile).isNotNull();
        assertThat(profile.dockerImage()).isNotBlank();
        assertThat(profile.sourceFileName()).isNotBlank();
        assertThat(profile.runCommand()).isNotBlank();
    }

    // ── Python ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("PYTHON uses configured pythonImage")
    void python_image() {
        assertThat(resolver.resolve(Language.PYTHON).dockerImage())
                .isEqualTo("python:3.11-slim");
    }

    @Test
    @DisplayName("PYTHON source file is solution.py")
    void python_sourceFile() {
        assertThat(resolver.resolve(Language.PYTHON).sourceFileName())
                .isEqualTo("solution.py");
    }

    @Test
    @DisplayName("PYTHON run command invokes python3 against /code/solution.py")
    void python_runCommand() {
        assertThat(resolver.resolve(Language.PYTHON).runCommand())
                .isEqualTo("python3 /code/solution.py");
    }

    // ── Java ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("JAVA uses configured javaImage")
    void java_image() {
        assertThat(resolver.resolve(Language.JAVA).dockerImage())
                .isEqualTo("openjdk:21-slim");
    }

    @Test
    @DisplayName("JAVA source file is Main.java")
    void java_sourceFile() {
        assertThat(resolver.resolve(Language.JAVA).sourceFileName())
                .isEqualTo("Main.java");
    }

    @Test
    @DisplayName("JAVA run command compiles then executes Main")
    void java_runCommand() {
        String cmd = resolver.resolve(Language.JAVA).runCommand();
        assertThat(cmd).contains("javac Main.java");
        assertThat(cmd).contains("java Main");
        assertThat(cmd).startsWith("cd /code");
    }

    // ── JavaScript ──────────────────────────────────────────────────────

    @Test
    @DisplayName("JAVASCRIPT uses configured jsImage")
    void javascript_image() {
        assertThat(resolver.resolve(Language.JAVASCRIPT).dockerImage())
                .isEqualTo("node:20-slim");
    }

    @Test
    @DisplayName("JAVASCRIPT source file is solution.js")
    void javascript_sourceFile() {
        assertThat(resolver.resolve(Language.JAVASCRIPT).sourceFileName())
                .isEqualTo("solution.js");
    }

    @Test
    @DisplayName("JAVASCRIPT run command invokes node against /code/solution.js")
    void javascript_runCommand() {
        assertThat(resolver.resolve(Language.JAVASCRIPT).runCommand())
                .isEqualTo("node /code/solution.js");
    }

    // ── C++ ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CPP uses configured cppImage")
    void cpp_image() {
        assertThat(resolver.resolve(Language.CPP).dockerImage())
                .isEqualTo("gcc:13");
    }

    @Test
    @DisplayName("CPP source file is solution.cpp")
    void cpp_sourceFile() {
        assertThat(resolver.resolve(Language.CPP).sourceFileName())
                .isEqualTo("solution.cpp");
    }

    @Test
    @DisplayName("CPP run command compiles with g++ -O2 then executes ./solution")
    void cpp_runCommand() {
        String cmd = resolver.resolve(Language.CPP).runCommand();
        assertThat(cmd).contains("g++ -O2");
        assertThat(cmd).contains("./solution");
        assertThat(cmd).startsWith("cd /code");
    }

    // ── Null safety ─────────────────────────────────────────────────────

    @Test
    @DisplayName("null Language throws NullPointerException from switch expression")
    void nullLanguageThrows() {
        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(NullPointerException.class);
    }
}