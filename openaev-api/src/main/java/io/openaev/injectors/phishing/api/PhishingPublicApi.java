package io.openaev.injectors.phishing.api;

import io.openaev.aop.AccessControl;
import io.openaev.context.TenantContext;
import io.openaev.database.model.PhishingLandingPage;
import io.openaev.database.model.PhishingResult;
import io.openaev.injectors.phishing.form.PhishingSubmitInput;
import io.openaev.injectors.phishing.response.PhishingLandingPageReader;
import io.openaev.injectors.phishing.service.PhishingTrackingService;
import io.openaev.rest.helper.RestBehavior;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, unauthenticated tracking endpoints hit by the victim's browser. Every route is
 * authenticated solely by the opaque, unguessable per-recipient token (never RBAC, never an
 * exercise player session), so it is registered under the {@code /api/phishing/tracking/**} prefix
 * that {@code AppSecurityConfig} permits. The {@code tenantId} path variable is named so the {@code
 * TenantInterceptor} sets the tenant automatically; it is also set explicitly here as defense in
 * depth.
 */
@RestController
@RequiredArgsConstructor
public class PhishingPublicApi extends RestBehavior {

  public static final String PHISHING_TRACKING_URI = "/api/phishing/tracking";

  /** 1x1 transparent GIF returned by the open-tracking pixel. */
  private static final byte[] TRACKING_PIXEL =
      Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");

  private final PhishingTrackingService phishingTrackingService;

  @GetMapping(PHISHING_TRACKING_URI + "/{tenantId}/o/{token}")
  @Transactional
  @AccessControl(skipRBAC = true)
  public ResponseEntity<byte[]> open(
      @PathVariable String tenantId, @PathVariable String token, HttpServletRequest request) {
    try {
      TenantContext.setCurrentTenant(tenantId);
      phishingTrackingService.markOpened(token, clientIp(request), request.getHeader("User-Agent"));
    } finally {
      TenantContext.clearCurrentTenant();
    }
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_GIF)
        .header("Cache-Control", "no-store, no-cache, must-revalidate, private")
        .body(TRACKING_PIXEL);
  }

  @GetMapping(PHISHING_TRACKING_URI + "/{tenantId}/c/{token}")
  @Transactional
  @AccessControl(skipRBAC = true)
  public ResponseEntity<Void> click(
      @PathVariable String tenantId, @PathVariable String token, HttpServletRequest request) {
    try {
      TenantContext.setCurrentTenant(tenantId);
      phishingTrackingService.markClicked(
          token, clientIp(request), request.getHeader("User-Agent"));
    } finally {
      TenantContext.clearCurrentTenant();
    }
    // Hand off to the themed public SPA renderer, which fetches the page content and posts creds.
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create("/phishing/" + tenantId + "/" + token))
        .build();
  }

  @GetMapping(PHISHING_TRACKING_URI + "/{tenantId}/page/{token}")
  @Transactional
  @AccessControl(skipRBAC = true)
  public PhishingLandingPageReader page(
      @PathVariable String tenantId, @PathVariable String token, HttpServletRequest request) {
    try {
      TenantContext.setCurrentTenant(tenantId);
      PhishingResult result = phishingTrackingService.resolveByToken(token).orElse(null);
      if (result == null || result.getLandingPage() == null) {
        return null;
      }
      // A rendered page is also an open signal (the pixel may be blocked by the mail client).
      phishingTrackingService.markOpened(token, clientIp(request), request.getHeader("User-Agent"));
      PhishingLandingPage landingPage = result.getLandingPage();
      return new PhishingLandingPageReader(landingPage);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  @PostMapping(PHISHING_TRACKING_URI + "/{tenantId}/s/{token}")
  @Transactional
  @AccessControl(skipRBAC = true)
  public Map<String, String> submit(
      @PathVariable String tenantId,
      @PathVariable String token,
      @RequestBody PhishingSubmitInput input,
      HttpServletRequest request) {
    try {
      TenantContext.setCurrentTenant(tenantId);
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
      return java.util.Collections.singletonMap("redirect_url", redirectUrl);
    } finally {
      TenantContext.clearCurrentTenant();
    }
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
