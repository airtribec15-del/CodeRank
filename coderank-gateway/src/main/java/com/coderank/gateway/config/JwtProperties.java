package com.coderank.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * HMAC-SHA256 shared secret — must be identical to the secret in coderank-auth.
     * Minimum 32 characters to satisfy HS256 key length requirement.
     */
    private String secret;
}