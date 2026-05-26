package com.coderank.common.enums;

/**
 * The final verdict of a code submission after execution against all test cases.
 * Defined in coderank-common so Execution Service, Result Processor,
 * and Submission Service all share the same type without circular module deps.
 */
public enum Verdict {

    /** All test cases passed. */
    ACCEPTED,

    /** Code produced incorrect output for at least one test case. */
    WRONG_ANSWER,

    /** Execution exceeded the allowed time limit. */
    TIME_LIMIT_EXCEEDED,

    /** Runtime error during execution (non-zero exit, crash, OOM). */
    RUNTIME_ERROR,

    /** Source code failed to compile. */
    COMPILATION_ERROR,

    /** Internal execution infrastructure failure — not a user code fault. */
    INTERNAL_ERROR,

    /** Verdict not yet determined (job queued or running). */
    PENDING
}