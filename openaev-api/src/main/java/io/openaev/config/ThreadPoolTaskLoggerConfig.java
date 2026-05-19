package io.openaev.config;

import io.openaev.utils.HttpReqRespUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class ThreadPoolTaskLoggerConfig {

  private ThreadPoolTaskExecutor createBaseExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    // TODO: find a better way to configure this variables dynamically - maybe through properties
    // file.

    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(50);
    executor.setQueueCapacity(1000);
    executor.setThreadNamePrefix("AuditLogger-");

    return executor;
  }

  @Bean(name = "accessControlAuditLoggerExecutor")
  public Executor contextAwareExecutor() {
    ThreadPoolTaskExecutor executor = createBaseExecutor();

    executor.setTaskDecorator(
        runnable -> {
          // CAPTURE REQUEST HEADERS AND IP, REQUEST URI and BODY (PARENT THREAD)
          var requestAttributes = RequestContextHolder.getRequestAttributes();
          HttpServletRequest request =
              requestAttributes instanceof ServletRequestAttributes attrs
                  ? attrs.getRequest()
                  : null;
          Map<String, String> headers;
          String remoteAddress, method, requestUri, queryString, fullUrl;

          if (request != null) {
            headers = HttpReqRespUtils.extractHeaders(request);
            remoteAddress = request.getRemoteAddr();
            method = request.getMethod();

            requestUri = request.getRequestURI();
            queryString = request.getQueryString();
            fullUrl = queryString == null ? requestUri : requestUri + "?" + queryString;
          } else {
            headers = null;
            remoteAddress = method = requestUri = queryString = fullUrl = null;
          }

          // CAPTURE LOGs CONTEXT
          var mdcContext = MDC.getCopyOfContextMap();

          // CAPTURE AUTHENTICATION CONTEXT (PARENT THREAD)
          var originalSecurityContext = SecurityContextHolder.getContext();
          var authentication = originalSecurityContext.getAuthentication();

          SecurityContext securityContextCopy =
              SecurityContextHolder.createEmptyContext(); // SAFE COPY (IMPORTANT)

          if (authentication != null) {
            securityContextCopy.setAuthentication(authentication);
          }

          // CAPTURE LOCALE CONTEXT (PARENT THREAD)
          var localeContext = LocaleContextHolder.getLocaleContext();

          return () -> {
            try {
              // STORE HEADERS AND REMOTE ADDRESS
              ThreadRequestContextHolder.setRequestContextData(
                  new ThreadRequestContextHolder.RequestContextData(
                      headers, remoteAddress, method, requestUri, queryString, fullUrl));

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

  public class ThreadRequestContextHolder {

    public record RequestContextData(
        Map<String, String> headers,
        String remoteAddress,
        String method,
        String uri,
        String queryString,
        String url) {}

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
