package org.shoaeb.mcp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A tiny landing/health endpoint. Render pings the root path (or a configured
 * health check path) to know the service is alive; this also gives a human
 * something sensible to see if they open the app URL in a browser instead of
 * pointing an MCP client at it.
 */
@RestController
public class HealthController {

    @GetMapping("/")
    public String index() {
        return "sample-mcp-server is running. Point an MCP client at /mcp";
    }
}
