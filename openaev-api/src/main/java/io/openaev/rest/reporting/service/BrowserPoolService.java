package io.openaev.rest.reporting.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Headless Chromium instances used for server-side report rendering.
 *
 * <p>Playwright Java is NOT thread-safe: a {@code Playwright} connection and every object created
 * from it must only ever be used by one thread (concurrent use corrupts the RPC dispatch with
 * errors like "Cannot find object to call ..."). Renders run on the renderer's small fixed thread
 * pool, so each render thread lazily gets its OWN Playwright driver + Chromium process
 * (thread-local), reused across its renders and relaunched if the browser process died. Every
 * render still gets a fresh isolated {@link BrowserContext} that is always closed.
 *
 * <p>A semaphore additionally bounds the number of simultaneous renders (and therefore of live
 * Chromium processes, since instances are per-thread and lazy) to keep memory usage predictable.
 *
 * <p>On dev machines Playwright downloads the browser bundle automatically on first use (no setup
 * needed). In the production images the bundle is pre-installed at PLAYWRIGHT_BROWSERS_PATH
 * (/ms-playwright) by the Dockerfiles, so no runtime download happens.
 */
@Slf4j
@Service
public class BrowserPoolService {

  /**
   * Chromium launch arguments suitable for containerized execution.
   *
   * <p>--no-sandbox is required because the production images run the JVM as root (the Dockerfiles
   * never drop privileges) and Chromium refuses to start its sandbox as root. Deployments running
   * the container as a dedicated non-root user still work: the flag merely disables an extra
   * isolation layer around a browser that only ever navigates to the platform itself.
   *
   * <p>--disable-dev-shm-usage avoids crashes with the tiny default /dev/shm of containers.
   */
  private static final List<String> LAUNCH_ARGS =
      List.of(
          "--no-sandbox",
          "--disable-dev-shm-usage",
          "--disable-gpu",
          "--force-color-profile=srgb",
          "--hide-scrollbars");

  /** Playwright driver + browser owned by a single render thread. */
  private static final class BrowserSession {
    private Playwright playwright;
    private Browser browser;
  }

  private final Semaphore renderPermits;

  private final ThreadLocal<BrowserSession> threadSession = new ThreadLocal<>();

  /** All sessions ever created, for best-effort cleanup at shutdown. */
  private final List<BrowserSession> sessions = new CopyOnWriteArrayList<>();

  private volatile boolean shutdown = false;

  public BrowserPoolService(
      @Value("${openaev.reporting.max-concurrent-renders:2}") final int maxConcurrentRenders) {
    this.renderPermits = new Semaphore(Math.max(1, maxConcurrentRenders));
  }

  /**
   * Runs the given action with a fresh isolated {@link BrowserContext}, closing it afterwards.
   *
   * @param options the context options (auth headers, viewport, TLS behavior)
   * @param action the render action
   * @return the action result
   * @throws InterruptedException if interrupted while waiting for a render permit
   */
  public <T> T withContext(
      final Browser.NewContextOptions options, final Function<BrowserContext, T> action)
      throws InterruptedException {
    this.renderPermits.acquire();
    try {
      Browser liveBrowser = acquireBrowser();
      try (BrowserContext context = liveBrowser.newContext(options)) {
        return action.apply(context);
      }
    } finally {
      this.renderPermits.release();
    }
  }

  /**
   * Returns the calling thread's connected Browser, lazily launching its own Playwright/Chromium on
   * first use and relaunching if the browser process died since the thread's last render. All state
   * is thread-local: no other thread ever touches this session (except the best-effort shutdown
   * cleanup), which is what the Playwright Java threading model requires.
   */
  private Browser acquireBrowser() {
    if (this.shutdown) {
      throw new IllegalStateException("Browser pool is shut down");
    }
    BrowserSession session = this.threadSession.get();
    if (session == null) {
      session = new BrowserSession();
      this.threadSession.set(session);
      this.sessions.add(session);
    }
    if (session.browser != null && session.browser.isConnected()) {
      return session.browser;
    }
    if (session.browser != null) {
      log.warn("Headless Chromium process of {} died; relaunching", Thread.currentThread());
      closeSessionBrowserQuietly(session);
    }
    if (session.playwright == null) {
      session.playwright = Playwright.create();
    }
    session.browser =
        session
            .playwright
            .chromium()
            .launch(new BrowserType.LaunchOptions().setHeadless(true).setArgs(LAUNCH_ARGS));
    log.info("Headless Chromium launched for report rendering on {}", Thread.currentThread());
    return session.browser;
  }

  private void closeSessionBrowserQuietly(final BrowserSession session) {
    try {
      if (session.browser != null) {
        session.browser.close();
      }
    } catch (Exception e) {
      log.debug("Could not close browser cleanly", e);
    } finally {
      session.browser = null;
    }
  }

  /**
   * Best-effort cleanup. The renderer bean (which depends on this service) is destroyed first and
   * interrupts its render threads, so no render is pumping these connections anymore; closing from
   * this thread is then safe enough for shutdown, and any failure only leaves a child process that
   * dies with the JVM.
   */
  @PreDestroy
  public void shutdown() {
    this.shutdown = true;
    for (BrowserSession session : this.sessions) {
      closeSessionBrowserQuietly(session);
      try {
        if (session.playwright != null) {
          session.playwright.close();
        }
      } catch (Exception e) {
        log.debug("Could not close Playwright cleanly", e);
      } finally {
        session.playwright = null;
      }
    }
    this.sessions.clear();
  }
}
