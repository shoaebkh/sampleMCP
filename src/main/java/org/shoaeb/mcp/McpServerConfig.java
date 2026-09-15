package org.shoaeb.mcp;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires up the Model Context Protocol server: the HTTP transport, the servlet that
 * exposes it, and the tools the server offers to connected MCP clients (such as
 * Claude Desktop).
 *
 * <p>The transport used is "Streamable HTTP" - the current recommended MCP transport
 * for remote servers - exposed at the {@code /mcp} endpoint. This is the URL you point
 * a Claude Desktop custom connector at once the app is deployed, e.g.
 * {@code https://<your-app>.onrender.com/mcp}.
 */
@Configuration
public class McpServerConfig {

    /**
     * The MCP transport provider, implemented as a plain {@link jakarta.servlet.http.HttpServlet}.
     * It is registered manually below via {@link ServletRegistrationBean} rather than
     * relying on servlet-container component scanning.
     */
    @Bean
    public HttpServletStreamableServerTransportProvider mcpTransportProvider() {
        return HttpServletStreamableServerTransportProvider.builder()
                .mcpEndpoint("/mcp")
                .build();
    }

    /**
     * Registers the MCP transport servlet with the embedded Tomcat container.
     * Async support must be enabled explicitly because manual registration bypasses
     * the {@code @WebServlet(asyncSupported = true)} annotation on the servlet class.
     */
    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServletRegistration(
            HttpServletStreamableServerTransportProvider transportProvider) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
                new ServletRegistrationBean<>(transportProvider, "/mcp");
        registration.setAsyncSupported(true);
        registration.setLoadOnStartup(1);
        return registration;
    }

    /**
     * Builds the synchronous MCP server and registers the tools it exposes. The server
     * is closed gracefully on application shutdown.
     */
    @Bean(destroyMethod = "close")
    public McpSyncServer mcpSyncServer(HttpServletStreamableServerTransportProvider transportProvider) {
        return McpServer.sync(transportProvider)
                .serverInfo("sample-mcp-server", "1.0.0")
                .toolCall(echoTool(), this::handleEcho)
                .toolCall(currentTimeTool(), this::handleCurrentTime)
                .toolCall(addNumbersTool(), this::handleAddNumbers)
                .build();
    }

    // ---------------------------------------------------------------------
    // Tool: echo
    // ---------------------------------------------------------------------

    private Tool echoTool() {
        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "text", Map.of(
                                "type", "string",
                                "description", "The text to echo back.")),
                "required", List.of("text"));

        return Tool.builder("echo", inputSchema)
                .description("Echoes back the text you provide. Useful for verifying the connection works.")
                .build();
    }

    private CallToolResult handleEcho(McpSyncServerExchange exchange, CallToolRequest request) {
        Object text = request.arguments().get("text");
        return CallToolResult.builder()
                .addTextContent("Echo: " + text)
                .build();
    }

    // ---------------------------------------------------------------------
    // Tool: get_current_time
    // ---------------------------------------------------------------------

    private Tool currentTimeTool() {
        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "timezone", Map.of(
                                "type", "string",
                                "description",
                                "IANA time zone id, e.g. 'America/New_York' or 'Asia/Kolkata'. "
                                        + "Defaults to UTC if omitted.")),
                "required", List.of());

        return Tool.builder("get_current_time", inputSchema)
                .description("Returns the current date and time, optionally in a specific time zone.")
                .build();
    }

    private CallToolResult handleCurrentTime(McpSyncServerExchange exchange, CallToolRequest request) {
        Object timezoneArg = request.arguments().get("timezone");
        ZoneId zoneId;
        try {
            zoneId = (timezoneArg instanceof String s && !s.isBlank()) ? ZoneId.of(s) : ZoneId.of("UTC");
        }
        catch (Exception e) {
            return CallToolResult.builder()
                    .addTextContent("Unknown time zone: " + timezoneArg)
                    .isError(true)
                    .build();
        }

        String now = ZonedDateTime.now(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return CallToolResult.builder()
                .addTextContent("Current time in " + zoneId + ": " + now)
                .build();
    }

    // ---------------------------------------------------------------------
    // Tool: add_numbers
    // ---------------------------------------------------------------------

    private Tool addNumbersTool() {
        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "a", Map.of("type", "number", "description", "First number."),
                        "b", Map.of("type", "number", "description", "Second number.")),
                "required", List.of("a", "b"));

        return Tool.builder("add_numbers", inputSchema)
                .description("Adds two numbers together and returns the sum.")
                .build();
    }

    private CallToolResult handleAddNumbers(io.modelcontextprotocol.server.McpSyncServerExchange exchange,
            io.modelcontextprotocol.spec.McpSchema.CallToolRequest request) {
        try {
            double a = ((Number) request.arguments().get("a")).doubleValue();
            double b = ((Number) request.arguments().get("b")).doubleValue();
            double sum = a + b;
            String formatted = (sum == Math.rint(sum)) ? String.valueOf((long) sum) : String.valueOf(sum);
            return CallToolResult.builder()
                    .addTextContent(a + " + " + b + " = " + formatted)
                    .build();
        }
        catch (Exception e) {
            return CallToolResult.builder()
                    .addTextContent("Both 'a' and 'b' must be numbers.")
                    .isError(true)
                    .build();
        }
    }
}
