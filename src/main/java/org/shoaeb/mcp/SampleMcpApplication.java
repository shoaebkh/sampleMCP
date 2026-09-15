package org.shoaeb.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the sample MCP (Model Context Protocol) server.
 *
 * <p>This is a plain Spring Boot application whose only job is to host the MCP
 * server transport over HTTP so it can be deployed remotely (e.g. on Render) and
 * added to Claude Desktop as a custom connector. The actual MCP wiring lives in
 * {@link McpServerConfig}.
 *
 * @author khans
 */
@SpringBootApplication
public class SampleMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleMcpApplication.class, args);
    }
}
