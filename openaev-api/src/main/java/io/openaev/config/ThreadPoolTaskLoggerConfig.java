package io.openaev.config;

import io.openaev.utils.HttpReqRespUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
@RequiredArgsConstructor
public class ThreadPoolTaskLoggerConfig {

  private final AuditLogProperties auditLogProperties;

  private ThreadPoolTaskExecutor createBaseExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    // TODO AUDIT: find a better way to configure this variables dynamically - maybe through
    // properties file.

    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(50);
    executor.setQueueCapacity(1000);
    executor.setThreadNamePrefix("TaskLogger-");

    return executor;
  }

  @Bean(name = "taskLoggerExecutor")
  public Executor contextAwareExecutor() {
    // If halt on failure, we switch to a sync task executor instead of an async one
    if (auditLogProperties.isHaltOnFailure()) {
      return new SyncTaskExecutor();
    }

    ThreadPoolTaskExecutor executor = createBaseExecutor();

    executor.setTaskDecorator(
        runnable -> {
          // CAPTURE REQUEST (PARENT THREAD)
          var requestAttributes = RequestContextHolder.getRequestAttributes();
          HttpServletRequest request =
              requestAttributes instanceof ServletRequestAttributes attrs
                  ? attrs.getRequest()
                  : null;

          // CAPTURE LOGs CONTEXT
          var mdcContext = MDC.getCopyOfContextMap();

          // CAPTURE AUTHENTICATION CONTEXT (PARENT THREAD)
          var originalSecurityContext = SecurityContextHolder.getContext();
          Authentication authentication = originalSecurityContext.getAuthentication();

          SecurityContext securityContextCopy =
              SecurityContextHolder.createEmptyContext(); // SAFE COPY (IMPORTANT)

          if (authentication != null) {
            securityContextCopy.setAuthentication(authentication);
          }

          // CAPTURE LOCALE CONTEXT (PARENT THREAD)
          var localeContext = LocaleContextHolder.getLocaleContext();

          // CREATE REQUEST CONTEXT HOLDER DATA WITH REQUEST HEADERS AND IP, REQUEST URI...
          ThreadRequestContextHolder.RequestContextData rcd =
              buildThreadRequestContextHolder(request, authentication);

          return () -> {
            try {
              // STORE HEADERS AND REMOTE ADDRESS
              ThreadRequestContextHolder.setRequestContextData(rcd);

              // RESTORE MDC
              if (mdcContext != null) {
                MDC.setContextMap(mdcContext);
              } else {
                MDC.clear();
              }

              // RESTORE SECURITY CONTEXT (SAFE COPY)
              SecurityContextHolder.setContext(securityContextCopy);

              // RESTORE LOCALE CONTEXT
              if (localeContext != null) {
                LocaleContextHolder.setLocaleContext(localeContext);
              }

              runnable.run();
            } finally {
              MDC.clear();
              ThreadRequestContextHolder.clear();
              SecurityContextHolder.clearContext();
              LocaleContextHolder.resetLocaleContext();
            }
          };
        });

    executor.initialize();

    return executor;
  }

  public static ThreadRequestContextHolder.RequestContextData buildThreadRequestContextHolder(
      HttpServletRequest request, Authentication authentication) {
    Map<String, String> headers;
    String remoteAddress, method, url, sessionId;

    if (request != null) {
      headers = HttpReqRespUtils.extractHeaders(request);
      remoteAddress = request.getRemoteAddr();
      method = request.getMethod();
      url = request.getRequestURL().toString();
      var session = request.getSession(false);
      sessionId = session != null ? session.getId() : null;
    } else {
      headers = null;
      remoteAddress = method = url = sessionId = null;
    }

    return new ThreadRequestContextHolder.RequestContextData(
        headers, remoteAddress, method, url, sessionId, authentication);
  }

  public static class ThreadRequestContextHolder {

    public record RequestContextData(
        Map<String, String> headers,
        String remoteAddress,
        String method,
        String url,
        String sessionId,
        Authentication authentication) {}

    private static final ThreadLocal<Map<String, Object>> CONTEXT = new ThreadLocal<>();

    public static void set(String name, Object value) {
      Map<String, Object> ctx = CONTEXT.get();

      if (ctx == null) {
        ctx = new HashMap<>();
        CONTEXT.set(ctx);
      }

      ctx.put(name, value);
    }

    public static Object get(String name) {
      Map<String, Object> ctx = CONTEXT.get();

      if (ctx == null) {
        return null;
      }

      return ctx.get(name);
    }

    public static void setRequestContextData(RequestContextData data) {
      set("RequestContextData", data);
    }

    public static RequestContextData getRequestContextData() {
      return get("RequestContextData") instanceof RequestContextData data ? data : null;
    }

    public static void clear() {
      CONTEXT.remove();
    }
  }
}
