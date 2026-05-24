package com.coderank.execution.service;

import com.coderank.common.enums.Language;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves Docker image and run command for each supported {@link Language}.
 */
@Component
public class LanguageConfigResolver {

    @Value("${execution.images.python:python:3.11-slim}")
    private String pythonImage;

    @Value("${execution.images.java:openjdk:21-slim}")
    private String javaImage;

    @Value("${execution.images.javascript:node:20-slim}")
    private String jsImage;

    @Value("${execution.images.cpp:gcc:13}")
    private String cppImage;

    public record LanguageProfile(String dockerImage, String sourceFileName, String runCommand) {}

    public LanguageProfile resolve(Language language) {
        return switch (language) {
            case PYTHON -> new LanguageProfile(
                    pythonImage,
                    "solution.py",
                    "python3 /code/solution.py"
            );
            case JAVA -> new LanguageProfile(
                    javaImage,
                    "Main.java",
                    "cd /code && javac Main.java && java Main"
            );
            case JAVASCRIPT -> new LanguageProfile(
                    jsImage,
                    "solution.js",
                    "node /code/solution.js"
            );
            case CPP -> new LanguageProfile(
                    cppImage,
                    "solution.cpp",
                    "cd /code && g++ -O2 -o solution solution.cpp && ./solution"
            );
        };
    }
}
