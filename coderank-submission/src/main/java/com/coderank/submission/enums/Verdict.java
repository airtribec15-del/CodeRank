package com.coderank.submission.enums;

/**
 * Final judging verdict set by the Result Processor after comparing
 * execution output against expected test-case answers.
 */
public enum Verdict {
    ACCEPTED,
    WRONG_ANSWER,
    TIME_LIMIT_EXCEEDED,
    RUNTIME_ERROR,
    COMPILATION_ERROR,
    PENDING   // not yet judged
}
