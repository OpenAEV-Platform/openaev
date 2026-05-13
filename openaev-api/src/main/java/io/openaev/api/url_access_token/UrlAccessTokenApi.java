package io.openaev.api.url_access_token;

import static io.openaev.api.url_access_token.UrlAccessTokenService.INVALID_TOKEN_MESSAGE;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.database.model.UrlAccessToken;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(UrlAccessTokenApi.URL_ACCESS_URI)
@RequiredArgsConstructor
public class UrlAccessTokenApi {

  public static final String URL_ACCESS_URI = "/api/url/access";
  public static final String URL_ACCESS_COOKIE_NAME = "url_access_token";

  private final UrlAccessTokenService urlAccessTokenService;
  private final PreviewFeatureService previewFeatureService;

  // -- READ --

  @GetMapping
  @LogExecutionTime
  @AccessControl(skipRBAC = true)
  @Operation(summary = "Validate URL access token, set secure cookie and redirect")
  public ResponseEntity<Void> access(@RequestParam("token") String rawToken) {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.URL_ACCESS_TOKEN)) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "URL access token feature disabled");
    }

    try {
      UrlAccessToken token = urlAccessTokenService.validateTokenExpiration(rawToken);
      urlAccessTokenService.updateLastUsed(token);

      ResponseCookie cookie =
          ResponseCookie.from(URL_ACCESS_COOKIE_NAME, rawToken)
              .httpOnly(true)
              .secure(true)
              .sameSite("Strict")
              .path("/")
              .build();

      return ResponseEntity.status(HttpStatus.FOUND)
          .header(HttpHeaders.SET_COOKIE, cookie.toString())
          .header(HttpHeaders.LOCATION, token.getUrl())
          .build();
    } catch (AccessDeniedException exception) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN_MESSAGE);
    }
  }
}
