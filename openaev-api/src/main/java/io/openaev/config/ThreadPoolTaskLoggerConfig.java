package io.openaev.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.slf4j.MDC;

@Configuration
public class ThreadPoolTaskLoggerConfig {

    private ThreadPoolTaskExecutor createBaseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

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
            //CAPTURE REQUEST CONTEXT (PARENT THREAD)
            var requestAttributes = RequestContextHolder.getRequestAttributes();
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
                    // RESTORE REQUEST CONTEXT
                    if (requestAttributes != null) {
                        RequestContextHolder.setRequestAttributes(requestAttributes, true);
                    }

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
                    RequestContextHolder.resetRequestAttributes();
                    SecurityContextHolder.clearContext();
                    LocaleContextHolder.resetLocaleContext();
                }
            };
        });

        executor.initialize();

        return executor;
    }
}
