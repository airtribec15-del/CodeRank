package com.coderank.execution.service;

import com.coderank.common.enums.Language;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves Docker image and run command for each supported {@link Language}.
 *
 * <p>Compiler output for Java and C++ is written to /tmp rather than /code.
 * /tmp is always writable by any UID inside the container. /code is a tmpfs
 * mounted with uid=1000,gid=1000 (see DockerSandboxRunner), but writing
 * compiler artifacts to /tmp is an additional safeguard that removes any
 * dependency on tmpfs mount flag behavior across Docker daemon versions.
 *
 * <p>Fix 1: javaImage default changed from the deprecated/removed
 * "openjdk:21-slim" to "eclipse-temurin:21-jdk-alpine", which is the
 * official community-maintained OpenJDK 21 image.
 *
 * <p>Fix 2: Java source file renamed from "Main.java" to "Solution.java".
 * The public class in the submitted code is named Solution. javac requires
 * the source file name to exactly match the public class name — compiling
 * a file named Main.java that contains "public class Solution" produces:
 *   error: class Solution is public, should be declared in a file named Solution.java
 * The run command class name is updated to match: "java -cp /tmp Solution".
 */
@Component
public class LanguageConfigResolver {

    @Value("${execution.images.python:python:3.11-slim}")
    private String pythonImage;

    // FIX 1: default changed from "openjdk:21-slim" (removed from Docker Hub)
    //         to "eclipse-temurin:21-jdk-alpine" (official OpenJDK replacement).
    @Value("${execution.images.java:eclipse-temurin:21-jdk-alpine}")
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
            // FIX 2: source file renamed Main.java → Solution.java so the file
            //         name matches the public class name required by javac.
            //         Run command updated: "java -cp /tmp Main" → "java -cp /tmp Solution".
            case JAVA -> new LanguageProfile(
                    javaImage,
                    "Solution.java",
                    "javac -d /tmp /code/Solution.java && java -cp /tmp Solution"
            );
            case JAVASCRIPT -> new LanguageProfile(
                    jsImage,
                    "solution.js",
                    "node /code/solution.js"
            );
            case CPP -> new LanguageProfile(
                    cppImage,
                    "solution.cpp",
                    "g++ -O2 -o /tmp/solution /code/solution.cpp && /tmp/solution"
            );
        };
    }
}