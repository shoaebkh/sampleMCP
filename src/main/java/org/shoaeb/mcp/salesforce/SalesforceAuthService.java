package org.shoaeb.mcp.salesforce;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

/**
 * Authenticates with Salesforce using the OAuth 2.0 JWT Bearer flow: a JWT is
 * signed locally with a private key and exchanged for an access token, with
 * no interactive login, no stored password, and no client secret. This
 * requires a Connected App configured for digital signatures in the target
 * org — see README.md for the setup steps.
 *
 * <p>The JWT is built by hand with plain JDK APIs (no JWT library dependency)
 * since its shape is fixed: a two-segment header/payload, base64url-encoded
 * and signed with RS256.
 */
@Component
public class SalesforceAuthService {

    public record Session(String accessToken, String instanceUrl) {
    }

    private final SalesforceProperties properties;
    private final ObjectMapper jsonMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final AtomicReference<Session> cachedSession = new AtomicReference<>();

    public SalesforceAuthService(SalesforceProperties properties, ObjectMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    /** Returns the cached session, authenticating for the first time if needed. */
    public Session getSession() {
        Session existing = cachedSession.get();
        return existing != null ? existing : refreshSession();
    }

    /** Forces a fresh token exchange. Call after a 401 from a downstream Apex REST call. */
    public synchronized Session refreshSession() {
        try {
            String jwt = buildSignedJwt();
            String requestBody = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=" + jwt;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.loginUrl() + "/services/oauth2/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Salesforce token request failed (HTTP " + response.statusCode() + "): " + response.body());
            }

            Map<String, Object> parsed = jsonMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {
            });
            Session session = new Session((String) parsed.get("access_token"), (String) parsed.get("instance_url"));
            cachedSession.set(session);
            return session;
        }
        catch (IllegalStateException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to authenticate with Salesforce: " + e.getMessage(), e);
        }
    }

    /** Discards the cached session so the next call re-authenticates from scratch. */
    public void invalidateSession() {
        cachedSession.set(null);
    }

    private String buildSignedJwt() throws Exception {
        long expiresAt = Instant.now().getEpochSecond() + 300;

        String header = jsonMapper.writeValueAsString(Map.of("alg", "RS256"));
        String payload = jsonMapper.writeValueAsString(Map.of(
                "iss", properties.clientId(),
                "sub", properties.username(),
                "aud", properties.loginUrl(),
                "exp", expiresAt));

        String signingInput = base64Url(header) + "." + base64Url(payload);

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(loadPrivateKey());
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));

        return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private PrivateKey loadPrivateKey() throws Exception {
        String pem = properties.privateKeyPem()
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
