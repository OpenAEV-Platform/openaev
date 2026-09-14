package io.openaev.api.url_access_token;

import static io.openaev.api.users.dto.UserMapper.fromUserContract;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.UrlAccessToken;
import io.openaev.database.model.User;
import io.openaev.database.repository.UrlAccessTokenRepository;
import io.openaev.injector_contract.variables.contract.UserContract;
import io.openaev.service.UserService;
import io.openaev.utils.RandomUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class, noRollbackFor = AccessDeniedException.class)
public class UrlAccessTokenService {

  public static final String INVALID_TOKEN_MESSAGE = "Invalid URL access token";
  public static final String FRONT_URL_ACCESS_URI = "/url/access";

  private static final int TOKEN_SIZE = 32;

  private final RandomUtils randomUtils;
  private final UserService userService;
  private final UrlAccessTokenRepository urlAccessTokenRepository;
  private final OpenAEVConfig openAEVConfig;

  @Value("${openaev.url.access.token.expiry-margin-days:7}")
  private int expiryMarginDays;

  @Value("${openaev.url.access.token.retention-days:30}")
  private int retentionDays;

  // -- CREATE --

  /**
   * Generates a URL access token, persists its hash and returns the raw token value.
   *
   * @param exercise exercise scope of the token
   * @param user user scope of the token
   * @param url final redirect URL associated with this token
   * @return raw token to embed in outgoing links
   */
  public String generateTokenUrl(
      @NotNull final Exercise exercise, @NotNull final User user, @NotBlank final String url) {
    String tokenSecret = generateRawToken();

    UrlAccessToken token = new UrlAccessToken();
    token.setTokenHash(hashToken(tokenSecret));
    token.setUrl(url);
    token.setExercise(exercise);
    token.setUser(user);
    token.setExpiresAt(computeExpiration(exercise));
    token.setCreatorUser(resolveCreatorUser());
    urlAccessTokenRepository.save(token);

    return this.openAEVConfig.getBaseUrl() + FRONT_URL_ACCESS_URI + "?token=" + tokenSecret;
  }

  /**
   * Generates a URL access token, persists its hash and returns the raw token value.
   *
   * @param exercise exercise scope of the token
   * @param protectUser protect user scope of the token
   * @param url final redirect URL associated with this token
   * @return raw token to embed in outgoing links
   */
  public String generateTokenUrl(
      @NotNull final Exercise exercise,
      @NotNull final UserContract protectUser,
      @NotBlank final String url) {
    User user = fromUserContract(protectUser);
    return this.generateTokenUrl(exercise, user, url);
  }

  private User resolveCreatorUser() {
    try {
      return userService.currentUser();
    } catch (Exception ignored) {
      return null;
    }
  }

  // -- READ --

  /**
   * Validates a raw token against expiration, revocation and optional scope constraints.
   *
   * @param rawToken raw token value received from query/cookie
   * @param exerciseId optional exercise ID scope check
   * @return the matching persisted token when valid
   */
  @Transactional(readOnly = true)
  public UrlAccessToken validateToken(@NotBlank final String rawToken, final String exerciseId) {
    return validateTokenInternal(rawToken, exerciseId);
  }

  private UrlAccessToken validateTokenInternal(
      @NotBlank final String rawToken, final String exerciseId) {
    UrlAccessToken token = findByRawToken(rawToken).orElse(null);
    if (token == null
        || isExpiredOrRevoked(token)
        || (exerciseId != null && !exerciseId.equals(token.getExercise().getId()))) {
      throw new AccessDeniedException(INVALID_TOKEN_MESSAGE);
    }
    return token;
  }

  /**
   * Validates token expiration/revocation status and removes invalid tokens immediately.
   *
   * @param rawToken raw token value received from query/cookie
   * @return the matching persisted token when valid
   */
  public UrlAccessToken validateTokenExpiration(@NotBlank final String rawToken) {
    UrlAccessToken token = findByRawToken(rawToken).orElse(null);
    if (token == null) {
      throw new AccessDeniedException(INVALID_TOKEN_MESSAGE);
    }
    if (isExpiredOrRevoked(token)) {
      urlAccessTokenRepository.delete(token);
      throw new AccessDeniedException(INVALID_TOKEN_MESSAGE);
    }
    return token;
  }

  /**
   * Validates a token and returns the linked user identifier.
   *
   * @param rawToken raw token value received from cookie
   * @return user identifier linked to the token
   */
  @Transactional(readOnly = true)
  public String validateTokenAndFindUserId(@NotBlank final String rawToken) {
    return validateTokenInternal(rawToken, null).getUser().getId();
  }

  // -- UPDATE --

  /**
   * Marks a token as used by updating the {@code lastUsedAt} audit timestamp.
   *
   * @param token token to update
   */
  public void updateLastUsed(@NotNull UrlAccessToken token) {
    token.setLastUsedAt(Instant.now());
    urlAccessTokenRepository.save(token);
  }

  /**
   * Revokes a token by identifier.
   *
   * @param tokenId token identifier
   */
  public void revokeToken(@NotBlank final String tokenId) {
    UrlAccessToken token =
        urlAccessTokenRepository
            .findById(tokenId)
            .orElseThrow(() -> new AccessDeniedException(INVALID_TOKEN_MESSAGE));
    token.setRevokedAt(Instant.now());
    urlAccessTokenRepository.save(token);
  }

  /**
   * Revokes all active tokens associated with an exercise.
   *
   * @param exerciseId exercise identifier
   * @return number of revoked tokens
   */
  public int revokeAllForExercise(@NotBlank final String exerciseId) {
    return urlAccessTokenRepository.revokeAllByExerciseId(exerciseId);
  }

  /**
   * Purges expired or revoked URL access tokens older than the configured retention window.
   *
   * @return number of deleted tokens
   */
  public int purgeExpiredAndRevokedTokens() {
    Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
    return urlAccessTokenRepository.deleteExpiredAndRevokedBefore(cutoff);
  }

  private Optional<UrlAccessToken> findByRawToken(String rawToken) {
    return urlAccessTokenRepository.findByTokenHash(hashToken(rawToken));
  }

  private String hashToken(String rawToken) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 algorithm is not available", exception);
    }
  }

  private boolean isExpiredOrRevoked(UrlAccessToken token) {
    return token.getRevokedAt() != null || token.getExpiresAt().isBefore(Instant.now());
  }

  private Instant computeExpiration(Exercise exercise) {
    Instant expirationBase = exercise.getEnd().orElse(Instant.now());
    return expirationBase.plus(expiryMarginDays, ChronoUnit.DAYS);
  }

  private String generateRawToken() {
    return randomUtils.getRandomAlphanumeric(TOKEN_SIZE);
  }
}
