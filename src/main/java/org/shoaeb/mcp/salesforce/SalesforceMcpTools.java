package org.shoaeb.mcp.salesforce;

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import org.springframework.stereotype.Component;

/**
 * MCP tool definitions and handlers backed by the Apex REST service in
 * salesforce/force-app/main/default/classes/McpDemoService.cls. Registered
 * with the MCP server in McpServerConfig only when Salesforce credentials
 * are configured (see SalesforceProperties#isConfigured()).
 */
@Component
public class SalesforceMcpTools {

    private final SalesforceClient salesforceClient;

    public SalesforceMcpTools(SalesforceClient salesforceClient) {
        this.salesforceClient = salesforceClient;
    }

    // ---------------------------------------------------------------------
    // Tool: salesforce_get_account
    // ---------------------------------------------------------------------

    public Tool getAccountTool() {
        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of(
                                "type", "string",
                                "description", "Exact Salesforce Account name to look up.")),
                "required", List.of("name"));

        return Tool.builder("salesforce_get_account", inputSchema)
                .description("Looks up a Salesforce Account by exact name and returns its Id, industry, and phone.")
                .build();
    }

    public CallToolResult handleGetAccount(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = String.valueOf(request.arguments().get("name"));
        try {
            Map<String, Object> account = salesforceClient.getAccountByName(name);
            return CallToolResult.builder()
                    .addTextContent("Account found: " + account)
                    .build();
        }
        catch (SalesforceNotFoundException e) {
            return CallToolResult.builder()
                    .addTextContent("No Account found with name '" + name + "'.")
                    .isError(true)
                    .build();
        }
        catch (Exception e) {
            return CallToolResult.builder()
                    .addTextContent("Salesforce call failed: " + e.getMessage())
                    .isError(true)
                    .build();
        }
    }

    // ---------------------------------------------------------------------
    // Tool: salesforce_create_case
    // ---------------------------------------------------------------------

    public Tool createCaseTool() {
        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "subject", Map.of("type", "string", "description", "Case subject/title."),
                        "description", Map.of("type", "string", "description", "Case description (optional)."),
                        "priority", Map.of(
                                "type", "string",
                                "description", "Low, Medium, or High (optional, defaults to Medium).")),
                "required", List.of("subject"));

        return Tool.builder("salesforce_create_case", inputSchema)
                .description("Creates a Salesforce Case and returns its Id.")
                .build();
    }

    public CallToolResult handleCreateCase(McpSyncServerExchange exchange, CallToolRequest request) {
        Object subject = request.arguments().get("subject");
        Object description = request.arguments().get("description");
        Object priority = request.arguments().get("priority");
        try {
            String caseId = salesforceClient.createCase(
                    subject == null ? null : subject.toString(),
                    description == null ? null : description.toString(),
                    priority == null ? null : priority.toString());
            return CallToolResult.builder()
                    .addTextContent("Created Case with Id: " + caseId)
                    .build();
        }
        catch (Exception e) {
            return CallToolResult.builder()
                    .addTextContent("Salesforce call failed: " + e.getMessage())
                    .isError(true)
                    .build();
        }
    }
}
