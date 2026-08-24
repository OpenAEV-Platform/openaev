package io.openaev.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.EncodedResourceResolver;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
@EnableWebMvc
public class MvcConfig implements WebMvcConfigurer {

  private static final int CACHE_PERIOD = 3600;

  /**
   * Outer bound on async request processing. This is the container-wide default, so it covers every
   * async return type the platform exposes — the reactive streams ({@code StreamApi#streamFlux},
   * {@code AiApi#queryAi}) as well as the chatbot SSE streams ({@code XtmOneChatApi#sendMessage}
   * and {@code XtmOneProxyApi#postAgentStream}, the only {@code StreamingResponseBody} endpoints).
   * The chatbot streams are merely what forces the value this high; nothing here is scoped to them.
   *
   * <p>Must stay <b>above</b> {@code XtmOneClient#CHAT_STREAM_RESPONSE_TIMEOUT} so the upstream
   * read times out first and unwinds the blocked reader thread itself; if this fires first, Spring
   * completes the response while that thread is still parked in {@code read()} with no way to
   * interrupt it. A turn paused for human tool approval is silent for as long as the reviewer
   * takes, so both values have to clear XTM One's own 30-minute abandonment bound — keep the three
   * in that order when changing any of them.
   */
  private static final int ASYNC_REQUEST_TIMEOUT_MS = 45 * 60 * 1000;

  @Resource private ObjectMapper objectMapper;
  @Resource private TenantInterceptor tenantInterceptor;
  @Resource private OrchestratorRunTenantInterceptor orchestratorRunTenantInterceptor;
  @Resource private TxCtxArgumentResolver txCtxArgumentResolver;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(tenantInterceptor).addPathPatterns("/api/tenants/**");
    // Bridges the legacy v1 TenantContext for the orchestrator callbacks on the non-prefixed
    // autonomous route (the prefixed route names its tenant and is caller-scoped). Without it, the
    // 8 callbacks that read/write v1 @Filter entities (Exercise, Team, Finding, Inject, Endpoint)
    // silently hit the DEFAULT tenant for a run owned by a non-default tenant. See
    // OrchestratorRunTenantInterceptor for why v1 must be established at request entry.
    registry
        .addInterceptor(orchestratorRunTenantInterceptor)
        .addPathPatterns("/api/autonomous-runs/**");
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(txCtxArgumentResolver);
  }

  @Bean
  public MappingJackson2HttpMessageConverter customJackson2HttpMessageConverter() {
    MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
    jsonConverter.setObjectMapper(this.objectMapper);
    return jsonConverter;
  }

  @Override
  public void configureMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
    // https://springdoc.org/#why-am-i-getting-an-error-swagger-ui-unable-to-render-definition-when-overriding-the-default-spring-registered-httpmessageconverter
    messageConverters.add(new ByteArrayHttpMessageConverter());
    messageConverters.add(new StringHttpMessageConverter());
    // Required for streamed binary responses (InputStreamResource on agent/implant downloads)
    messageConverters.add(new ResourceHttpMessageConverter());
    messageConverters.add(customJackson2HttpMessageConverter());
  }

  @Override
  public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
    configurer.setTaskExecutor(getTaskExecutor());
    configurer.setDefaultTimeout(ASYNC_REQUEST_TIMEOUT_MS);
  }

  @Bean
  protected ConcurrentTaskExecutor getTaskExecutor() {
    return new ConcurrentTaskExecutor(Executors.newFixedThreadPool(20));
  }

  private void addPathStaticResolver(
      ResourceHandlerRegistry registry, String pattern, String location) {
    registry
        .addResourceHandler(pattern)
        .addResourceLocations(location)
        .setCachePeriod(CACHE_PERIOD)
        .resourceChain(false)
        .addResolver(new EncodedResourceResolver())
        .addResolver(new PathResourceResolver());
  }

  @Override
  public void addResourceHandlers(@NotNull ResourceHandlerRegistry registry) {
    // React statics
    addPathStaticResolver(registry, "/static/**", "classpath:/build/static/");
    // React assets (js & css)
    addPathStaticResolver(registry, "/assets/**", "classpath:/build/assets/");
    // Specific application images
    addPathStaticResolver(registry, "/media/**", "classpath:/build/static/media/");
  }
}
