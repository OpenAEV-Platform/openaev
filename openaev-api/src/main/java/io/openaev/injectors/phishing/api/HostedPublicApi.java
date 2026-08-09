package io.openaev.injectors.phishing.api;

import io.openaev.aop.AccessControl;
import io.openaev.api.custom_domain.CustomDomainService;
import io.openaev.context.TenantContext;
import io.openaev.database.model.PhishingLandingPage;
import io.openaev.database.model.PhishingResult;
import io.openaev.injectors.phishing.form.PhishingSubmitInput;
import io.openaev.injectors.phishing.response.PhishingLandingPageReader;
import io.openaev.injectors.phishing.service.PhishingTrackingService;
import io.openaev.rest.helper.RestBehavior;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.Collections;
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
 * the token and set on the {@link TenantContext} before any tenant-filtered work runs.
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

  /** Open-tracking pixel embedded (invisibly) in the lure email. */
  @GetMapping(HOSTED_URI + "/o/{token}")
  @Transactional
  @AccessControl(skipRBAC = true)
  public ResponseEntity<byte[]> open(@PathVariable String token, HttpServletRequest request) {
    if (!bindTenant(token)) {
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
  public PhishingLandingPageReader page(@PathVariable String token, HttpServletRequest request) {
    if (!bindTenant(token)) {
      return null;
    }
    PhishingResult result = phishingTrackingService.resolveByToken(token).orElse(null);
    if (result == null || result.getLandingPage() == null) {
      return null;
    }
    phishingTrackingService.markClicked(token, clientIp(request), request.getHeader("User-Agent"));
    PhishingLandingPage landingPage = result.getLandingPage();
    return new PhishingLandingPageReader(landingPage);
  }

  /** Records the credentials submitted by the victim and returns the configured redirect URL. */
  @PostMapping(HOSTED_URI + "/s/{token}")
  @Transactional
  @AccessControl(skipRBAC = true)
  public Map<String, String> submit(
      @PathVariable String token,
      @RequestBody PhishingSubmitInput input,
      HttpServletRequest request) {
    if (!bindTenant(token)) {
      return Collections.singletonMap("redirect_url", null);
    }
    Optional<PhishingResult> result =
        phishingTrackingService.markSubmitted(
            token,
            resolveUsername(input),
            resolvePassword(input),
            clientIp(request),
            request.getHeader("User-Agent"));
    String redirectUrl =
        result
            .map(PhishingResult::getLandingPage)
            .map(PhishingLandingPage::getRedirectUrl)
            .orElse(null);
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
  public ResponseEntity<Void> domainCheck(@RequestParam("domain") String domain) {
    return customDomainService.isHostnameVerified(domain)
        ? ResponseEntity.ok().build()
        : ResponseEntity.notFound().build();
  }

  private boolean bindTenant(final String token) {
    Optional<String> tenantId = phishingTrackingService.resolveTenantIdByToken(token);
    tenantId.ifPresent(TenantContext::setCurrentTenant);
    return tenantId.isPresent();
  }

  private ResponseEntity<byte[]> pixelResponse() {
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_GIF)
        .header("Cache-Control", "no-store, no-cache, must-revalidate, private")
        .body(TRACKING_PIXEL);
  }

  private String resolveUsername(PhishingSubmitInput input) {
    if (input.getUsername() != null && !input.getUsername().isBlank()) {
      return input.getUsername();
    }
    if (input.getData() != null) {
      for (String key : new String[] {"username", "email", "user", "login"}) {
        String value = input.getData().get(key);
        if (value != null && !value.isBlank()) {
          return value;
        }
      }
    }
    return null;
  }

  private String resolvePassword(PhishingSubmitInput input) {
    if (input.getPassword() != null && !input.getPassword().isBlank()) {
      return input.getPassword();
    }
    if (input.getData() != null) {
      for (String key : new String[] {"password", "passwd", "pass"}) {
        String value = input.getData().get(key);
        if (value != null && !value.isBlank()) {
          return value;
        }
      }
    }
    return null;
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
