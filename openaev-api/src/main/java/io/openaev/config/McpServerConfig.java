package io.openaev.config;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.openaev.rest.mcp.McpToolProvider;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextListener;

/**
 * Configures the embedded MCP (Model Context Protocol) server.
 *
 * <p>Registers an {@link HttpServletStreamableServerTransportProvider} as a Servlet mapped to
 * {@code /api/mcp/*}. Authentication is handled by the existing {@link
 * io.openaev.security.TokenAuthenticationFilter} which Spring Boot auto-registers for all URL
 * patterns.
 *
 * <p>The MCP server always starts, but tool execution checks the
 * {@code platform_mcp_enabled} DB setting at runtime (see {@link McpToolProvider}).
 */
// TODO: Consider adding rate limiting to prevent excessive MCP tool calls
@Configuration
public class McpServerConfig {

  private final OpenAEVConfig openAEVConfig;

  public McpServerConfig(OpenAEVConfig openAEVConfig) {
    this.openAEVConfig = openAEVConfig;
  }

  @Bean
  public ServletListenerRegistrationBean<RequestContextListener> requestContextListener() {
    return new ServletListenerRegistrationBean<>(new RequestContextListener());
  }

  @Bean
  public HttpServletStreamableServerTransportProvider mcpTransportProvider() {
    return HttpServletStreamableServerTransportProvider.builder()
        .mcpEndpoint("/api/mcp")
        .build();
  }

  @Bean
  public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
      HttpServletStreamableServerTransportProvider transportProvider) {
    ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
        new ServletRegistrationBean<>(transportProvider, "/api/mcp/*");
    registration.setLoadOnStartup(1);
    return registration;
  }

  @Bean
  public McpSyncServer mcpServer(
      HttpServletStreamableServerTransportProvider transportProvider,
      McpToolProvider toolProvider) {
    String version =
        openAEVConfig.getVersion() != null ? openAEVConfig.getVersion() : "unknown";
    return McpServer.sync(transportProvider)
        .serverInfo("openaev", version)
        .capabilities(ServerCapabilities.builder().tools(true).build())
        .tools(toolProvider.getToolSpecifications())
        .build();
  }
}
