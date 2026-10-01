package io.openaev.injectors.phishing.api;

import io.openaev.aop.AccessControl;
import io.openaev.api.custom_domain.CustomDomainService;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.PhishingLandingPage;
import io.openaev.database.model.PhishingResult;
import io.openaev.injectors.phishing.form.PhishingSubmitInput;
import io.openaev.injectors.phishing.response.PhishingLandingPageReader;
import io.openaev.injectors.phishing.service.PhishingTrackingService;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.phishing.PhishingLandingPagePublicLookupService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, unauthenticated endpoints hit by the victim's browser, using a benign, tenant-less URL
 * shape so the address bar and email link read like {@code https://&lt;host&gt;/auth/&lt;token&gt;}
 * rather than exposing the word "phishing" and a duplicated tenant id. Every route is authenticated
 * solely by the opaque, globally-unique per-recipient token: the owning tenant is recovered from
 * the token and set on the legacy {@link TenantContext} before any {@code TenantBaseListener}-era
 * work runs.
 *
 * <p>{@code TenantContext} is NOT the v2 {@link TxCtx}: none of these routes carry a {@code
 * {tenantId}} path segment, so {@code TxCtxArgumentResolver} resolves every {@code ctx} parameter
 * here to {@link TxCtx#missing()} regardless of {@link #bindTenant}, and the transaction aspect
 * locks that empty scope for the whole request. A lazy load of {@code phishing_landing_pages} (v2
 * isolation) must therefore never be touched directly from these methods: {@link #page} and {@link
 * #submit} resolve it explicitly through {@link PhishingLandingPagePublicLookupService}, scoped to
 * the one tenant {@link #bindTenant} already recovered from the token - same shape as {@code
 * domain-check} below, but narrowed to that single tenant since the token already resolved it.
 *
 * <p>Registered under the {@code /api/hosted/**} prefix that {@code AppSecurityConfig} permits and
 * exempts from CSRF. The legacy {@code /api/phishing/tracking/**} endpoints remain for links in
 * already-sent emails.
 */
@RestController
@RequiredArgsConstructor
public class HostedPublicApi extends RestBehavior {

  public static final String HOSTED_URI = "/api/hosted";

  /** Benign, human-readable path segment used in the victim-facing landing URL. */
  public static final String LANDING_PATH_PREFIX = "auth";

  /** 1x1 transparent GIF returned by the open-tracking pixel. */
  private static final byte[] TRACKING_PIXEL =
      Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");

  private final PhishingTrackingService phishingTrackingService;
  private final CustomDomainService customDomainService;
  private final PhishingLandingPagePublicLookupService phishingLandingPageLookupService;

  /** Open-tracking pixel embedded (invisibly) in the lure email. */
  @GetMapping(HOSTED_URI + "/o/{token}")
  @Transactional
  @AccessControl(skipRBAC = true)
  public ResponseEntity<byte[]> open(
      TxCtx ctx, @PathVariable String token, HttpServletRequest request) {
    if (bindTenant(token).isEmpty()) {
      return pixelResponse();
    }
    phishingTrackingService.markOpened(token, clientIp(request), request.getHeader("User-Agent"));
    return pixelResponse();
  }

  /**
   * Serves the sanitized landing page content for the token and records the visit as a click (a
   * loaded landing page is a stronger "fell for phishing" signal than an image pixel, and this is
   * the first request the SPA makes after the recipient follows the email link).
   */
  @GetMapping(HOSTED_URI + "/page/{token}")
  @Transactional
  @AccessControl(skipRBAC = true)
  public PhishingLandingPageReader page(
      TxCtx ctx, @PathVariable String token, HttpServletRequest request) {
    Optional<String> tenantId = bindTenant(token);
    if (tenantId.isEmpty()) {
      return null;
    }
    PhishingResult result = phishingTrackingService.resolveAndBackfillByToken(token).orElse(null);
    if (result == null) {
      return null;
    }
    // Id-only access on the lazy proxy: never triggers a DB load (same idiom as MonoIdSerializer),
    // so it is safe to read here even though this request's own scope is TxCtx.missing().
    String landingPageId = result.getLandingPage() == null ? null : result.getLandingPage().getId();
    PhishingLandingPage landingPage =
        phishingLandingPageLookupService.byId(tenantId.get(), landingPageId).orElse(null);
    if (landingPage == null) {
      return null;
    }
    phishingTrackingService.markClicked(token, clientIp(request), request.getHeader("User-Agent"));
    return new PhishingLandingPageReader(landingPage);
  }

  /** Records the credentials submitted by the victim and returns the configured redirect URL. */
  @PostMapping(HOSTED_URI + "/s/{token}")
  @Transactional
  @AccessControl(skipRBAC = true)
  public Map<String, String> submit(
      TxCtx ctx,
      @PathVariable String token,
      @RequestBody PhishingSubmitInput input,
      HttpServletRequest request) {
    Optional<String> tenantId = bindTenant(token);
    if (tenantId.isEmpty()) {
      return Collections.singletonMap("redirect_url", null);
    }
    PhishingResult result = phishingTrackingService.resolveAndBackfillByToken(token).orElse(null);
    // Id-only access on the lazy proxy: never triggers a DB load (same idiom as MonoIdSerializer),
    // so it is safe to read here even though this request's own scope is TxCtx.missing().
    String landingPageId =
        result == null || result.getLandingPage() == null ? null : result.getLandingPage().getId();
    PhishingLandingPage landingPage =
        phishingLandingPageLookupService.byId(tenantId.get(), landingPageId).orElse(null);
    phishingTrackingService.markSubmitted(
        token,
        submittedFields(input),
        clientIp(request),
        request.getHeader("User-Agent"),
        landingPage);
    String redirectUrl = landingPage != null ? landingPage.getRedirectUrl() : null;
    return Collections.singletonMap("redirect_url", redirectUrl);
  }

  /**
   * Ownership check for a hostname, used by an on-demand-TLS edge (e.g. Caddy {@code on_demand_tls
   * ask}) to decide whether it may obtain a certificate for an inbound custom domain: 200 only for
   * a VERIFIED custom domain, 404 otherwise. No token, no tenant - a pure hostname allow-list gate.
   */
  @GetMapping(HOSTED_URI + "/domain-check")
  @Transactional
  @AccessControl(skipRBAC = true)
  public ResponseEntity<Void> domainCheck(TxCtx ctx, @RequestParam("domain") String domain) {
    return customDomainService.isHostnameVerified(domain)
        ? ResponseEntity.ok().build()
        : ResponseEntity.notFound().build();
  }

  private Optional<String> bindTenant(final String token) {
    Optional<String> tenantId = phishingTrackingService.resolveTenantIdByToken(token);
    tenantId.ifPresent(TenantContext::setCurrentTenant);
    return tenantId;
  }

  private ResponseEntity<byte[]> pixelResponse() {
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_GIF)
        .header("Cache-Control", "no-store, no-cache, must-revalidate, private")
        .body(TRACKING_PIXEL);
  }

  /**
   * Flattens the submitted payload into a single field map: the free-form {@code data} map plus the
   * explicit {@code username} / {@code password} fields (when present). The tracking service
   * resolves the credential out of this map and keeps every field as the completeness record. Blank
   * {@code data} values are dropped so an empty {@code data.username} / {@code data.password} never
   * blocks the non-blank dedicated fields from being captured.
   */
  private Map<String, String> submittedFields(PhishingSubmitInput input) {
    Map<String, String> fields = new LinkedHashMap<>();
    if (input.getData() != null) {
      input
          .getData()
          .forEach(
              (key, value) -> {
                if (value != null && !value.isBlank()) {
                  fields.put(key, value);
                }
              });
    }
    if (input.getUsername() != null && !input.getUsername().isBlank()) {
      fields.putIfAbsent("username", input.getUsername());
    }
    if (input.getPassword() != null && !input.getPassword().isBlank()) {
      fields.putIfAbsent("password", input.getPassword());
    }
    return fields;
  }

  /** Best-effort client IP, honoring a single X-Forwarded-For hop. */
  private String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
