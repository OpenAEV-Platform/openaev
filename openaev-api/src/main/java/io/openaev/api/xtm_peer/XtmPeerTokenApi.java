package io.openaev.api.xtm_peer;

import io.openaev.aop.AccessControl;
import io.openaev.api.xtm_peer.dto.XtmPeerTokenInput;
import io.openaev.api.xtm_peer.dto.XtmPeerTokenOutput;
import io.openaev.api.xtm_peer.dto.XtmPeerTokenRevokeInput;
import io.openaev.api.xtm_peer.dto.XtmPeerTokenRevokeOutput;
import io.openaev.config.OpenAEVAdminConfig;
import io.openaev.context.TxCtx;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.account.XtmPeerPrivilegeService;
import io.openaev.service.account.XtmPeerPrivilegeService.IssuedPeerToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The provisioning API a deployment manager (SaaS Manager) calls to give a sibling XTM product a
 * service account and a token on this platform (XTM peer links). Authenticated by the platform
 * admin token ({@code OPENAEV_ADMIN_TOKEN}) alone: any other bearer, a valid user token included,
 * is refused with 401. Same contract as the OpenCRQ endpoint of the same path.
 *
 * <ul>
 *   <li>{@code POST /api/xtm/peer-tokens} {consumer, name, email, token_name}: the account keyed by
 *       {@code email} in the default tenant, created or converged; its tokens revoked and a
 *       non-expiring one issued. 200 {user_id, email, name, token_name, token}.
 *   <li>{@code POST /api/xtm/peer-tokens/revoke} {email, token_name}: its tokens revoked, the
 *       account kept. 200 {revoked}.
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "XTM peer tokens", description = "Service accounts of the XTM Platform peer links")
public class XtmPeerTokenApi extends RestBehavior {

  public static final String XTM_PEER_TOKENS_URI = "/api/xtm/peer-tokens";

  private static final String BEARER_PREFIX = "bearer ";

  private final OpenAEVAdminConfig adminConfig;
  private final XtmPeerPrivilegeService xtmPeerPrivilegeService;

  @PostMapping(
      value = XTM_PEER_TOKENS_URI,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Issue the token of an XTM peer service account",
      description =
          "Creates or converges the service account named by the email, revokes its tokens and"
              + " issues a new one. Platform admin token only.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Token issued"),
    @ApiResponse(responseCode = "401", description = "Not the platform admin token")
  })
  @AccessControl(skipRBAC = true)
  @Transactional(rollbackFor = Exception.class)
  public XtmPeerTokenOutput issue(
      TxCtx ctx, HttpServletRequest request, @Valid @RequestBody XtmPeerTokenInput input) {
    requireAdminToken(request);
    IssuedPeerToken issued =
        xtmPeerPrivilegeService.issueToken(ctx, input.getEmail(), input.getName());
    return XtmPeerTokenOutput.builder()
        .userId(issued.userId())
        .email(XtmPeerPrivilegeService.normalizePeerEmail(input.getEmail()))
        .name(input.getName())
        .tokenName(input.getTokenName())
        .token(issued.token())
        .build();
  }

  @PostMapping(
      value = XTM_PEER_TOKENS_URI + "/revoke",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Revoke the tokens of an XTM peer service account",
      description =
          "Revokes the tokens of the account; keeps the account. Platform admin token only.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Tokens revoked (or none to revoke)"),
    @ApiResponse(responseCode = "401", description = "Not the platform admin token")
  })
  @AccessControl(skipRBAC = true)
  @Transactional(rollbackFor = Exception.class)
  public XtmPeerTokenRevokeOutput revoke(
      TxCtx ctx, HttpServletRequest request, @Valid @RequestBody XtmPeerTokenRevokeInput input) {
    requireAdminToken(request);
    return new XtmPeerTokenRevokeOutput(
        xtmPeerPrivilegeService.revokeTokens(ctx, input.getEmail()));
  }

  /** Constant-time comparison of the bearer against the configured platform admin token. */
  private void requireAdminToken(HttpServletRequest request) {
    String adminToken = adminConfig.getToken();
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (!StringUtils.hasText(adminToken)
        || header == null
        || !header.toLowerCase(Locale.ROOT).startsWith(BEARER_PREFIX)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Platform admin token required");
    }
    byte[] presented =
        header.substring(BEARER_PREFIX.length()).trim().getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(presented, adminToken.getBytes(StandardCharsets.UTF_8))) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Platform admin token required");
    }
  }
}
