package io.openaev.aop.audit_log;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Encapsulates the application shutdown triggered by audit halt-on-failure. Extracted as a separate
 * bean so that tests can mock it without calling {@link System#exit(int)}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditShutdownService {

  private final ApplicationContext context;

  /**
   * Initiates a graceful shutdown on a daemon thread so the current transaction can rollback first.
   */
  public void initiateShutdown() {
    Thread shutdownThread =
        new Thread(
            () -> {
              int exitCode = SpringApplication.exit(context);
              System.exit(exitCode != 0 ? exitCode : 1);
            },
            "audit-halt-shutdown");
    shutdownThread.setDaemon(true);
    shutdownThread.start();
  }
}
