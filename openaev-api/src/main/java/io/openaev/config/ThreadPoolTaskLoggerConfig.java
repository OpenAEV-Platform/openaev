package io.openaev.config;

import io.openaev.utils.HttpReqRespUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.slf4j.MDC;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class ThreadPoolTaskLoggerConfig {

    private ThreadPoolTaskExecutor createBaseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        //TODO: find a better way to configure this variables dynamically - maybe through properties file.

        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("AuditLogger-");

        return executor;
    }

    @Bean(name = "accessControlAuditLoggerExecutor")
    public Executor contextAwareExecutor() {
        ThreadPoolTaskExecutor executor = createBaseExecutor();

        executor.setTaskDecorator(runnable -> {
            //CAPTURE REQUEST HEADERS AND IP (PARENT THREAD)
            var requestAttributes = RequestContextHolder.getRequestAttributes();
            HttpServletRequest request = requestAttributes instanceof ServletRequestAttributes attrs ? attrs.getRequest() : null;
            Map<String, String> headers;
            String remoteAddress;
            String method;

            if (request != null) {
                headers = HttpReqRespUtils.extractHeaders(request);
                remoteAddress = request.getRemoteAddr();
                method = request.getMethod();
            } else {
                headers = null;
                remoteAddress = null;
                method = null;
            }

            // CAPTURE LOGs CONTEXT
            var mdcContext = MDC.getCopyOfContextMap();

            // CAPTURE AUTHENTICATION CONTEXT (PARENT THREAD)
            var originalSecurityContext = SecurityContextHolder.getContext();
            var authentication = originalSecurityContext.getAuthentication();

            SecurityContext securityContextCopy = SecurityContextHolder.createEmptyContext(); // SAFE COPY (IMPORTANT)

            if (authentication != null) {
                securityContextCopy.setAuthentication(authentication);
            }

            // CAPTURE LOCALE CONTEXT (PARENT THREAD)
            var localeContext = LocaleContextHolder.getLocaleContext();

            return () -> {
                try {
                    // STORE HEADERS AND REMOTE ADDRESS
                    ThreadRequestContextHolder.setHeaders(headers);
                    ThreadRequestContextHolder.setRemoteAddress(remoteAddress);
                    ThreadRequestContextHolder.setMethod(method);

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

        public static void setHeaders(Map<String, String> headers) {
            set("HEADERS", headers);
        }
        public static Map<String, String> getHeaders() {
            return (Map<String, String>) get("HEADERS");
        }

        public static void setRemoteAddress(String remoteAddress) {
            set("REMOTE_ADDR", remoteAddress);
        }
        public static String getRemoteAddress() {
            return (String) get("REMOTE_ADDR");
        }

        public static void setMethod(String method) {
            set("METHOD", method);
        }
        public static String getMethod() {
            return (String) get("METHOD");
        }

        public static void clear() {
            CONTEXT.remove();
        }
    }
}
