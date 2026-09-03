package io.openaev.rest.reporting.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.Margin;
import com.microsoft.playwright.options.Media;
import com.microsoft.playwright.options.WaitUntilState;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Document;
import io.openaev.database.model.Reporting;
import io.openaev.database.model.ReportingFormat;
import io.openaev.database.model.ReportingGeneration;
import io.openaev.database.model.ReportingGenerationStatus;
import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.database.repository.ReportingGenerationRepository;
import io.openaev.database.repository.TokenRepository;
import io.openaev.rest.document.DocumentService;
import io.openaev.service.FileService;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Real rendering engine of the reporting module: drives an in-container headless Chromium
 * (Playwright) against the platform's own print-ready SPA route ({@code
 * /reporting/{reportingId}/render}) and captures the result as PDF or self-contained HTML.
 *
 * <p>Marked {@code @Primary} so Spring wires this implementation into {@link
 * io.openaev.rest.reporting.ReportingService}; {@link NoopReportingRenderer} remains as an explicit
 * fallback bean. When Playwright/Chromium is unavailable at render time the generation is flipped
 * to ERROR with a clear message (equivalent to the Noop behavior, but per-generation).
 *
 * <p>Rendering is asynchronous: {@link #render} captures the request-scoped context (tenant, acting
 * user's API token) on the caller thread, then dispatches the actual browser work on a small
 * dedicated executor AFTER the surrounding transaction commits, so the API request returns the
 * PENDING generation immediately and the frontend polls for completion.
 */
@Slf4j
@Service
@Primary
public class PlaywrightReportingRenderer implements ReportingRenderer {

  private static final DateTimeFormatter FILE_TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC);

  /** Settle delay after the readiness flag flips, letting last paints/fonts complete. */
  private static final double SETTLE_DELAY_MS = 300;

  /** Cap on the initial network-idle wait; the readiness flag below is the real contract. */
  private static final double NETWORK_IDLE_CAP_MS = 30_000;

  /**
   * Full page-load attempts before giving up: the render page retries every data query itself (with
   * backoff) and reports the number of sections that STILL settled in error through {@code
   * window.OPENAEV_REPORT_SECTION_ERRORS}; a fresh reload re-runs the whole pipeline, which clears
   * transient backend hiccups that outlived the in-page retries.
   */
  static final int MAX_PAGE_ATTEMPTS = 3;

  /** Pause between two page attempts, giving a transient backend condition time to clear. */
  private static final double PAGE_RETRY_PAUSE_MS = 2_000;

  /**
   * Extracts a reasonably self-contained HTML snapshot of the rendered page.
   *
   * <p>Approach: clone the DOM, drop scripts and stylesheet links, inline all readable (i.e.
   * same-origin) CSS rules into a single style tag, and absolutize img src attributes.
   *
   * <p>Known limitations (pragmatic choice, documented): images are NOT converted to data URLs -
   * they keep absolute URLs pointing at the platform, so they only load for viewers who can reach
   * (and are authorized on) the platform; url(...) references inside CSS rules (fonts, background
   * images) are kept as written and may not resolve outside the platform origin.
   */
  private static final String SELF_CONTAINED_HTML_SNIPPET =
      """
      () => {
        const clone = document.documentElement.cloneNode(true);
        clone.querySelectorAll('script').forEach((el) => el.remove());
        clone.querySelectorAll('link[rel="stylesheet"]').forEach((el) => el.remove());
        clone.querySelectorAll('img').forEach((img) => {
          const src = img.getAttribute('src');
          if (src) {
            try {
              img.setAttribute('src', new URL(src, document.baseURI).href);
            } catch (e) {
              // Keep the original src when it cannot be resolved
            }
          }
        });
        let css = '';
        for (const sheet of document.styleSheets) {
          try {
            for (const rule of sheet.cssRules) {
              css += rule.cssText + '\\n';
            }
          } catch (e) {
            // Cross-origin stylesheet: not readable, skipped
          }
        }
        const style = document.createElement('style');
        style.textContent = css;
        (clone.querySelector('head') || clone).appendChild(style);
        return '<!DOCTYPE html>\\n' + clone.outerHTML;
      }
      """;

  private final ReportingGenerationRepository reportingGenerationRepository;
  private final TokenRepository tokenRepository;
  private final DocumentService documentService;
  private final FileService fileService;
  private final BrowserPoolService browserPoolService;

  private final long renderTimeoutMs;
  private final String renderBaseUrl;
  private final int serverPort;
  private final String contextPath;
  private final boolean sslEnabled;

  private final ExecutorService renderExecutor;

  public PlaywrightReportingRenderer(
      final ReportingGenerationRepository reportingGenerationRepository,
      final TokenRepository tokenRepository,
      final DocumentService documentService,
      final FileService fileService,
      final BrowserPoolService browserPoolService,
      @Value("${openaev.reporting.render-timeout-seconds:90}") final long renderTimeoutSeconds,
      @Value("${openaev.reporting.max-concurrent-renders:2}") final int maxConcurrentRenders,
      @Value("${openaev.reporting.render-base-url:}") final String renderBaseUrl,
      @Value("${server.port:8080}") final int serverPort,
      @Value("${server.servlet.context-path:/}") final String contextPath,
      @Value("${server.ssl.enabled:false}") final boolean sslEnabled) {
    this.reportingGenerationRepository = reportingGenerationRepository;
    this.tokenRepository = tokenRepository;
    this.documentService = documentService;
    this.fileService = fileService;
    this.browserPoolService = browserPoolService;
    this.renderTimeoutMs = Math.max(1, renderTimeoutSeconds) * 1000;
    this.renderBaseUrl = renderBaseUrl;
    this.serverPort = serverPort;
    this.contextPath = contextPath;
    this.sslEnabled = sslEnabled;
    // Executor sized to the browser concurrency cap: extra threads would only block on the
    // BrowserPoolService semaphore anyway.
    AtomicInteger threadIndex = new AtomicInteger(1);
    this.renderExecutor =
        Executors.newFixedThreadPool(
            Math.max(1, maxConcurrentRenders),
            runnable -> {
              Thread thread =
                  new Thread(runnable, "reporting-render-" + threadIndex.getAndIncrement());
              thread.setDaemon(true);
              return thread;
            });
  }

  /** Immutable snapshot of everything the background render thread needs. */
  private record RenderJob(
      String generationId,
      String reportingId,
      String reportingName,
      ReportingFormat format,
      String tenantId,
      String tokenValue) {}

  private record CapturedOutput(byte[] bytes, String extension, String contentType) {}

  @Override
  public void render(final ReportingGeneration generation, final User actingUser) {
    // Everything request-scoped is captured HERE, on the caller thread, while the surrounding
    // transaction is still open: tenant id (ThreadLocal) and the acting user's API token.
    String tokenValue =
        actingUser == null
            ? null
            : this.tokenRepository
                .findFirstByUserIdOrderByCreatedAsc(actingUser.getId())
                .map(Token::getValue)
                .orElse(null);
    if (tokenValue == null) {
      // Managed entity, still inside the caller transaction: fail synchronously.
      generation.setStatus(ReportingGenerationStatus.ERROR);
      generation.setErrorMessage(
          actingUser == null
              ? "No acting user available for the render"
              : "Acting user has no API token to authenticate the render");
      generation.setCompletedAt(Instant.now());
      this.reportingGenerationRepository.save(generation);
      return;
    }
    Reporting reporting = generation.getReporting();
    RenderJob job =
        new RenderJob(
            generation.getId(),
            reporting.getId(),
            reporting.getName(),
            generation.getFormat(),
            TenantContext.getCurrentTenant(),
            tokenValue);
    // Dispatch only after the caller transaction commits, otherwise the background thread could
    // start before the PENDING row is visible.
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              submit(job);
            }
          });
    } else {
      submit(job);
    }
  }

  private void submit(final RenderJob job) {
    try {
      this.renderExecutor.submit(() -> execute(job));
    } catch (Exception e) {
      log.error("Could not dispatch render of generation {}", job.generationId(), e);
      TenantContext.setCurrentTenant(job.tenantId());
      try {
        completeWithError(job, "Could not dispatch the render: " + e.getMessage());
      } finally {
        TenantContext.clearCurrentTenant();
      }
    }
  }

  /** Full render lifecycle, executed on a dedicated render thread. */
  private void execute(final RenderJob job) {
    TenantContext.setCurrentTenant(job.tenantId());
    try {
      markRunning(job);
      CapturedOutput output = capture(job);
      Document document = storeDocument(job, output);
      completeWithSuccess(job, document);
      log.info(
          "Rendered generation {} of reporting {} ({} bytes, {})",
          job.generationId(),
          job.reportingId(),
          output.bytes().length,
          output.contentType());
    } catch (Exception e) {
      log.error("Render of generation {} failed", job.generationId(), e);
      completeWithError(job, e.getMessage() != null ? e.getMessage() : e.getClass().getName());
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  private CapturedOutput capture(final RenderJob job) throws InterruptedException {
    // The Authorization header authenticates BOTH the SPA page load and every API call the SPA
    // makes: TokenAuthenticationFilter accepts "Bearer <plain token>" statelessly, and since the
    // fresh context carries no cookies these requests are also CSRF-exempt. The token therefore
    // never appears in the URL (and thus never in access logs).
    Browser.NewContextOptions contextOptions =
        new Browser.NewContextOptions()
            .setExtraHTTPHeaders(Map.of("Authorization", "Bearer " + job.tokenValue()))
            .setViewportSize(1280, 900)
            // Loopback TLS uses the bundled self-signed certificate
            .setIgnoreHTTPSErrors(this.sslEnabled);
    String url = buildRenderUrl(job);
    return this.browserPoolService.withContext(
        contextOptions,
        context -> {
          context.setDefaultTimeout(this.renderTimeoutMs);
          Page page = context.newPage();
          loadUntilClean(page, url, job);
          page.waitForTimeout(SETTLE_DELAY_MS);
          if (ReportingFormat.HTML.equals(job.format())) {
            String html = (String) page.evaluate(SELF_CONTAINED_HTML_SNIPPET);
            return new CapturedOutput(html.getBytes(StandardCharsets.UTF_8), "html", "text/html");
          }
          // The render page is designed for print: emulate print media explicitly so screen-only
          // artifacts (scrollbars, hover affordances) never leak into the document. page.pdf()
          // would default to print media anyway; being explicit keeps the behavior pinned.
          page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.PRINT));
          // Printer margins stay at zero AND the render route declares zero @page margins: the
          // document owns the full paper, so full-bleed elements (cover, running footer) really
          // reach the edges and dark themes are never framed by unpainted white paper. All page
          // spacing (horizontal gutters, per-page top gap, footer band) is laid out by the render
          // route itself via body padding and repeating table spacers - never reintroduce margins
          // here.
          byte[] pdf =
              page.pdf(
                  new Page.PdfOptions()
                      .setFormat("A4")
                      .setPrintBackground(true)
                      .setPreferCSSPageSize(true)
                      .setMargin(
                          new Margin().setTop("0").setBottom("0").setLeft("0").setRight("0")));
          return new CapturedOutput(pdf, "pdf", "application/pdf");
        });
  }

  /**
   * Loads the render page and waits until it is BOTH ready and clean (no section settled in error),
   * reloading up to {@link #MAX_PAGE_ATTEMPTS} times otherwise.
   *
   * <p>Reliability contract of a generation: a captured report must never contain a broken section.
   * The render page already retries every data query with backoff before flagging it as error; a
   * full reload on top re-runs that whole pipeline for hiccups that outlived the in-page retries.
   * If sections still fail after every attempt the generation is FAILED with an explicit message -
   * an honest error the user can retry beats silently distributing a broken report (scheduled
   * generations are emailed unseen).
   */
  private void loadUntilClean(final Page page, final String url, final RenderJob job) {
    for (int attempt = 1; ; attempt++) {
      boolean lastAttempt = attempt >= MAX_PAGE_ATTEMPTS;
      Response response = null;
      try {
        Page.NavigateOptions navigateOptions =
            new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.NETWORKIDLE)
                .setTimeout(Math.min(NETWORK_IDLE_CAP_MS, this.renderTimeoutMs));
        Page.ReloadOptions reloadOptions =
            new Page.ReloadOptions()
                .setWaitUntil(WaitUntilState.NETWORKIDLE)
                .setTimeout(Math.min(NETWORK_IDLE_CAP_MS, this.renderTimeoutMs));
        response = attempt == 1 ? page.navigate(url, navigateOptions) : page.reload(reloadOptions);
      } catch (TimeoutError e) {
        // Network-idle is best-effort (polling can keep the network busy); the readiness
        // flag below is the authoritative contract with the render page.
      }
      // Fail fast on an error page instead of waiting out the readiness timeout. Typical
      // cause: the base URL does not serve the SPA (e.g. dev API without a frontend build);
      // openaev.reporting.render-base-url must then point at the server hosting the SPA.
      // A configuration problem, not a transient one - never retried.
      if (response != null && response.status() >= 400) {
        throw new IllegalStateException(
            "Render page "
                + url
                + " returned HTTP "
                + response.status()
                + " - the render base URL must serve the platform SPA"
                + " (configure openaev.reporting.render-base-url)");
      }
      try {
        page.waitForFunction(
            "() => window.OPENAEV_REPORT_READY === true",
            null,
            new Page.WaitForFunctionOptions()
                .setTimeout(this.renderTimeoutMs)
                .setPollingInterval(250));
      } catch (TimeoutError e) {
        if (lastAttempt) {
          throw e;
        }
        log.warn(
            "Render page of generation {} did not become ready (attempt {}/{}), reloading",
            job.generationId(),
            attempt,
            MAX_PAGE_ATTEMPTS);
        page.waitForTimeout(PAGE_RETRY_PAUSE_MS);
        continue;
      }
      int sectionErrors = sectionErrors(page);
      if (sectionErrors == 0) {
        return;
      }
      if (lastAttempt) {
        throw new IllegalStateException(
            sectionErrors
                + " report section(s) failed to load after "
                + MAX_PAGE_ATTEMPTS
                + " attempts - please retry the generation");
      }
      log.warn(
          "Render page of generation {} has {} errored section(s) (attempt {}/{}), reloading",
          job.generationId(),
          sectionErrors,
          attempt,
          MAX_PAGE_ATTEMPTS);
      page.waitForTimeout(PAGE_RETRY_PAUSE_MS);
    }
  }

  /** Number of module queries the render page flagged as persistently failed. */
  private static int sectionErrors(final Page page) {
    Object value = page.evaluate("() => window.OPENAEV_REPORT_SECTION_ERRORS || 0");
    return value instanceof Number number ? number.intValue() : 0;
  }

  /**
   * Base URL the headless browser uses to reach the platform itself. Defaults to loopback
   * (http(s)://localhost:{server.port}{context-path}) so the render never depends on external DNS,
   * reverse proxies or TLS termination; openaev.reporting.render-base-url overrides it for setups
   * where loopback does not serve the SPA.
   */
  private String buildRenderUrl(final RenderJob job) {
    String base;
    if (this.renderBaseUrl != null && !this.renderBaseUrl.isBlank()) {
      base = this.renderBaseUrl.trim();
    } else {
      String scheme = this.sslEnabled ? "https" : "http";
      String path = this.contextPath == null ? "" : this.contextPath.trim();
      if (path.equals("/")) {
        path = "";
      }
      base = scheme + "://localhost:" + this.serverPort + path;
    }
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    String format = ReportingFormat.HTML.equals(job.format()) ? "html" : "pdf";
    // The SPA router runs under a tenant-prefixed basename ({base}/{tenantId}/...). Without the
    // prefix, root.tsx client-side-redirects to the acting USER's default tenant, which is not
    // necessarily the tenant owning this reporting - so the segment must be explicit here.
    String tenantSegment =
        job.tenantId() == null || job.tenantId().isBlank() ? "" : "/" + job.tenantId();
    return base + tenantSegment + "/reporting/" + job.reportingId() + "/render?format=" + format;
  }

  private Document storeDocument(final RenderJob job, final CapturedOutput output)
      throws Exception {
    String fileName =
        "report_"
            + slugify(job.reportingName())
            + "_"
            + FILE_TIMESTAMP.format(Instant.now())
            + "."
            + output.extension();
    // Same content-addressed target scheme as DocumentService.upsert
    String target = DigestUtils.md5Hex(output.bytes()) + "." + output.extension();
    this.fileService.uploadFile(
        target,
        new ByteArrayInputStream(output.bytes()),
        output.bytes().length,
        output.contentType());
    Document document = new Document();
    document.setName(fileName);
    document.setTarget(target);
    document.setType(output.contentType());
    document.setDescription("Generated report of reporting template: " + job.reportingName());
    return this.documentService.save(document);
  }

  private void markRunning(final RenderJob job) {
    this.reportingGenerationRepository
        .findByIdAndTenantId(job.generationId(), job.tenantId())
        .ifPresent(
            generation -> {
              generation.setStatus(ReportingGenerationStatus.RUNNING);
              this.reportingGenerationRepository.save(generation);
            });
  }

  private void completeWithSuccess(final RenderJob job, final Document document) {
    this.reportingGenerationRepository
        .findByIdAndTenantId(job.generationId(), job.tenantId())
        .ifPresent(
            generation -> {
              generation.setStatus(ReportingGenerationStatus.SUCCESS);
              generation.setDocument(document);
              generation.setErrorMessage(null);
              generation.setCompletedAt(Instant.now());
              this.reportingGenerationRepository.save(generation);
            });
  }

  private void completeWithError(final RenderJob job, final String message) {
    this.reportingGenerationRepository
        .findByIdAndTenantId(job.generationId(), job.tenantId())
        .ifPresent(
            generation -> {
              generation.setStatus(ReportingGenerationStatus.ERROR);
              generation.setErrorMessage(truncate(message, 1000));
              generation.setCompletedAt(Instant.now());
              this.reportingGenerationRepository.save(generation);
            });
  }

  private static String slugify(final String name) {
    if (name == null) {
      return "report";
    }
    String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    return slug.isBlank() ? "report" : slug;
  }

  private static String truncate(final String value, final int max) {
    if (value == null) {
      return null;
    }
    return value.length() <= max ? value : value.substring(0, max);
  }

  @PreDestroy
  public void shutdown() {
    // Give in-flight renders a short grace period before interrupting them: an interrupt
    // mid-Playwright-call kills the RPC pump and produces noisy TargetClosedError cascades.
    this.renderExecutor.shutdown();
    try {
      if (!this.renderExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
        this.renderExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      this.renderExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
