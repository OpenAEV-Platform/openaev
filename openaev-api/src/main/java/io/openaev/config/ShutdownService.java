package io.openaev.config;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Initiates a graceful JVM shutdown on a daemon thread. Extracted as a standalone bean so that (1)
 * the current call stack can unwind (e.g. transaction rollback) before the shutdown runs, and (2)
 * tests can mock it without calling {@link System#exit(int)}.
 *
 * <p>Idempotent — only the first call spawns the shutdown thread; subsequent calls are no-ops.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShutdownService {

  private final ApplicationContext context;
  private final AtomicBoolean shutdownTriggered = new AtomicBoolean(false);

  /**
   * Initiates a graceful shutdown on a daemon thread so the current transaction can rollback first.
   * Idempotent — only the first call spawns the shutdown thread; subsequent calls are no-ops.
   */
  public void initiateShutdown() {
    if (!shutdownTriggered.compareAndSet(false, true)) {
      log.debug("Shutdown already triggered by another thread.");
      return;
    }

    Thread shutdownThread =
        new Thread(
            () -> {
              int exitCode = SpringApplication.exit(context);
              System.exit(exitCode != 0 ? exitCode : 1);
            },
            "graceful-shutdown");
    shutdownThread.setDaemon(true);
    shutdownThread.start();
  }
}

