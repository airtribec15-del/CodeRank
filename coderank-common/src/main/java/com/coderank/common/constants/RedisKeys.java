package com.coderank.common.constants;

public final class RedisKeys {

    private RedisKeys() {}

    public static final String JOB_STATUS_PREFIX  = "job_status:";
    public static final String JOB_RUNNING_PREFIX = "job_running:";
    public static final String JWT_BLACKLIST_PREFIX = "jwt_blacklist:";
    public static final String RATE_LIMIT_PREFIX  = "rate_limit:";
    public static final String RATE_LIMIT_IP_PREFIX = "rate_limit:ip:";

    public static String jobStatusKey(String jobId) {
        return JOB_STATUS_PREFIX + jobId;
    }

    public static String jobRunningKey(String jobId) {
        return JOB_RUNNING_PREFIX + jobId;
    }

    public static String jwtBlacklistKey(String jti) {
        return JWT_BLACKLIST_PREFIX + jti;
    }
}