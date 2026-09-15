package org.shoaeb.mcp.salesforce;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

/**
 * Calls the Apex REST endpoints exposed by
 * salesforce/force-app/main/default/classes/McpDemoService.cls (mapped at
 * /services/apexrest/mcp-demo/), re-authenticating once and retrying if the
 * cached access token has expired.
 */
@Component
public class SalesforceClient {

    private static final String APEX_REST_PATH = "/services/apexrest/mcp-demo/";

    private final SalesforceAuthService authService;
    private final ObjectMapper jsonMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public SalesforceClient(SalesforceAuthService authService, ObjectMapper jsonMapper) {
        this.authService = authService;
        this.jsonMapper = jsonMapper;
    }

    /** Calls the GET endpoint; throws {@link SalesforceNotFoundException} if no match. */
    public Map<String, Object> getAccountByName(String accountName) {
        String encodedName = URLEncoder.encode(accountName, StandardCharsets.UTF_8);
        String path = APEX_REST_PATH + "?name=" + encodedName;

        return sendWithRetry(
                session -> HttpRequest.newBuilder()
                        .uri(URI.create(session.instanceUrl() + path))
                        .header("Authorization", "Bearer " + session.accessToken())
                        .GET()
                        .build(),
                new TypeReference<Map<String, Object>>() {
                });
    }

    /** Calls the POST endpoint and returns the newly created Case Id. */
    public String createCase(String subject, String description, String priority) {
        String requestBody = jsonMapper.writeValueAsString(Map.of(
                "subject", subject,
                "description", description == null ? "" : description,
                "priority", priority == null ? "" : priority));

        return sendWithRetry(
                session -> HttpRequest.newBuilder()
                        .uri(URI.create(session.instanceUrl() + APEX_REST_PATH))
                        .header("Authorization", "Bearer " + session.accessToken())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build(),
                new TypeReference<String>() {
                });
    }

    private <T> T sendWithRetry(Function<SalesforceAuthService.Session, HttpRequest> requestBuilder,
            TypeReference<T> responseType) {
        try {
            SalesforceAuthService.Session session = authService.getSession();
            HttpResponse<String> response = httpClient.send(requestBuilder.apply(session),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401) {
                authService.invalidateSession();
                session = authService.refreshSession();
                response = httpClient.send(requestBuilder.apply(session), HttpResponse.BodyHandlers.ofString());
            }

            if (response.statusCode() == 404) {
                throw new SalesforceNotFoundException("Not found");
            }
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "Salesforce returned HTTP " + response.statusCode() + ": " + response.body());
            }
            if (response.body() == null || response.body().isBlank()) {
                return null;
            }

            return jsonMapper.readValue(response.body(), responseType);
        }
        catch (SalesforceNotFoundException | IllegalStateException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IllegalStateException("Salesforce request failed: " + e.getMessage(), e);
        }
    }
}
