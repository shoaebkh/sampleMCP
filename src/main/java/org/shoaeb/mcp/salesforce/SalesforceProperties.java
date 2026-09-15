package org.shoaeb.mcp.salesforce;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Salesforce JWT Bearer flow credentials, sourced from environment variables
 * (see application.properties for the mapping). All fields are optional at
 * the Spring wiring level so the app can still start without them; callers
 * should check {@link #isConfigured()} before relying on Salesforce tools.
 */
@Component
public class SalesforceProperties {

    private final String loginUrl;
    private final String clientId;
    private final String username;
    private final String privateKeyPem;

    public SalesforceProperties(
            @Value("${salesforce.login-url}") String loginUrl,
            @Value("${salesforce.client-id}") String clientId,
            @Value("${salesforce.username}") String username,
            @Value("${salesforce.private-key-pem}") String privateKeyPem) {
        this.loginUrl = loginUrl;
        this.clientId = clientId;
        this.username = username;
        this.privateKeyPem = privateKeyPem;
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !username.isBlank() && !privateKeyPem.isBlank();
    }

    public String loginUrl() {
        return loginUrl;
    }

    public String clientId() {
        return clientId;
    }

    public String username() {
        return username;
    }

    public String privateKeyPem() {
        return privateKeyPem;
    }
}
