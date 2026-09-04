package io.openaev.api.url_access_token;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.config.OpenAEVConfig;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.UrlAccessToken;
import io.openaev.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class UrlAccessTokenApi {

  public static final String URL_ACCESS_URI = "/api/url/access";
  public static final String TENANT_URL_ACCESS_URI = TENANT_PREFIX + "/url/access";
  public static final String URL_ACCESS_COOKIE_NAME = "url_access_token";

  private final UrlAccessTokenService urlAccessTokenService;
  private final OpenAEVConfig openAEVConfig;
  private final UserService userService;

  @GetMapping({URL_ACCESS_URI, TENANT_URL_ACCESS_URI})
  @LogExecutionTime
  @AccessControl(skipRBAC = true)
  @Operation(summary = "Validate URL access token, set secure cookie and redirect")
  @Transactional
  public ResponseEntity<Void> access(TxCtx ctx, @RequestParam("token") String rawToken) {
    try {
      UrlAccessToken token = urlAccessTokenService.validateTokenExpiration(rawToken);
      urlAccessTokenService.updateLastUsed(token);

      ResponseCookie cookie =
          ResponseCookie.from(URL_ACCESS_COOKIE_NAME, rawToken)
              .httpOnly(true)
              .secure(openAEVConfig.isCookieSecure())
              .sameSite("Strict")
              .path("/")
              .build();

      return ResponseEntity.status(HttpStatus.FOUND)
          .header(HttpHeaders.SET_COOKIE, cookie.toString())
          .header(HttpHeaders.LOCATION, token.getUrl())
          .build();
    } catch (AccessDeniedException exception) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
  }

  @DeleteMapping({URL_ACCESS_URI + "/{tokenId}", TENANT_URL_ACCESS_URI + "/{tokenId}"})
  @LogExecutionTime
  @AccessControl(actionPerformed = Action.DELETE, resourceType = ResourceType.PLATFORM_SETTING)
  @Operation(summary = "Revoke a URL access token by id (admin only)")
  @Transactional
  public ResponseEntity<Void> revokeByTokenId(TxCtx ctx, @PathVariable("tokenId") String tokenId) {
    ensureCurrentUserIsAdmin();
    urlAccessTokenService.revokeToken(tokenId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping({
    URL_ACCESS_URI + "/exercise/{exerciseId}",
    TENANT_URL_ACCESS_URI + "/exercise/{exerciseId}"
  })
  @LogExecutionTime
  @AccessControl(actionPerformed = Action.DELETE, resourceType = ResourceType.PLATFORM_SETTING)
  @Operation(summary = "Revoke all URL access tokens for an exercise (admin only)")
  @Transactional
  public ResponseEntity<Void> revokeByExerciseId(
      TxCtx ctx, @PathVariable("exerciseId") String exerciseId) {
    ensureCurrentUserIsAdmin();
    urlAccessTokenService.revokeAllForExercise(exerciseId);
    return ResponseEntity.noContent().build();
  }

  private void ensureCurrentUserIsAdmin() {
    if (!userService.currentUser().isAdmin()) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only administrators can revoke URL access tokens");
    }
  }
}
