package com.coderank.common.constants;

/**
 * Canonical Kafka topic name constants shared across all CodeRank services.
 * All services MUST reference these constants — never hardcode topic strings.
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    // ── Execution pipeline ──────────────────────────────────────────────────

    /** Published by Submission Service; consumed by Execution Service. */
    public static final String EXECUTION_REQUESTS     = "code.execution.requests";

    /** DLT for code.execution.requests (Execution Service consumer failures). */
    public static final String EXECUTION_REQUESTS_DLT = "code.execution.requests-dlt";

    /** Published by Execution Service; consumed by Result Processor. */
    public static final String EXECUTION_RESULTS      = "code.execution.results";

    /** DLT for code.execution.results (Result Processor consumer failures). */
    public static final String EXECUTION_RESULTS_DLT  = "code.execution.results-dlt";

    // ── State update pipeline ───────────────────────────────────────────────

    /** Published by Result Processor; consumed by Problem Service. */
    public static final String STATE_UPDATE_EVENTS     = "state-update-events";

    /** DLT for state-update-events (Problem Service consumer failures). */
    public static final String STATE_UPDATE_EVENTS_DLT = "state-update-events-dlt";
}