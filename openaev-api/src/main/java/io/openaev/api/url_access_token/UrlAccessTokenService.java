package io.openaev.api.url_access_token;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.UrlAccessToken;
import io.openaev.database.model.User;
import io.openaev.database.repository.UrlAccessTokenRepository;
import io.openaev.service.UserService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class UrlAccessTokenService {

  private static final String INVALID_TOKEN_MESSAGE = "Invalid URL access token";
  private static final int TOKEN_BYTE_SIZE = 32;
  private static final BCryptPasswordEncoder TOKEN_HASHER = new BCryptPasswordEncoder();

  private final UserService userService;
  private final UrlAccessTokenRepository urlAccessTokenRepository;

  @Value("${openaev.url.access.token.expiry-margin-days:7}")
  private int expiryMarginDays;

  // -- CREATE --

  /**
   * Generates a URL access token, persists its hash and returns the raw token value.
   *
   * @param exercise exercise scope of the token
   * @param user user scope of the token
   * @param url final redirect URL associated with this token
   * @return raw token to embed in outgoing links
   */
  public String generateToken(
      @NotNull final Exercise exercise, @NotNull final User user, @NotBlank final String url) {
    String tokenSecret = generateRawToken();

    UrlAccessToken token = new UrlAccessToken();
    token.setId(UUID.randomUUID().toString());
    token.setTokenHash(TOKEN_HASHER.encode(tokenSecret));
    token.setUrl(url);
    token.setExercise(exercise);
    token.setUser(user);
    token.setExpiresAt(computeExpiration(exercise));
    token.setCreatorUser(resolveCreatorUser());
    urlAccessTokenRepository.save(token);
    return tokenSecret;
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
   * @param userId optional user ID scope check
   * @return the matching persisted token when valid
   */
  @Transactional(readOnly = true)
  public UrlAccessToken validateToken(
      @NotBlank final String rawToken, final String exerciseId, final String userId) {
    return validateTokenInternal(rawToken, exerciseId, userId);
  }

  private UrlAccessToken validateTokenInternal(
      @NotBlank final String rawToken, final String exerciseId, final String userId) {
    UrlAccessToken token = findByRawToken(rawToken).orElse(null);
    if (token == null
        || isExpiredOrRevoked(token)
        || (exerciseId != null && !exerciseId.equals(token.getExercise().getId()))
        || (userId != null && !userId.equals(token.getUser().getId()))) {
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
    return validateTokenInternal(rawToken, null, null).getUser().getId();
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

  private Optional<UrlAccessToken> findByRawToken(String rawToken) {
    String tokenHash = TOKEN_HASHER.encode(rawToken);
    return urlAccessTokenRepository.findByTokenHash(tokenHash);
  }

  private boolean isExpiredOrRevoked(UrlAccessToken token) {
    return token.getRevokedAt() != null || token.getExpiresAt().isBefore(Instant.now());
  }

  private Instant computeExpiration(Exercise exercise) {
    Instant expirationBase = exercise.getEnd().orElse(Instant.now());
    return expirationBase.plus(expiryMarginDays, ChronoUnit.DAYS);
  }

  private String generateRawToken() {
    byte[] tokenBytes = new byte[TOKEN_BYTE_SIZE];
    SecureRandomHolder.INSTANCE.nextBytes(tokenBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
  }

  private static final class SecureRandomHolder {
    private static final java.security.SecureRandom INSTANCE = new java.security.SecureRandom();

    private SecureRandomHolder() {}
  }
}
