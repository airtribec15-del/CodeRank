package com.coderank.common.enums;

public enum ExecutionStatus {

    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    TIMED_OUT;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == TIMED_OUT;
    }
}