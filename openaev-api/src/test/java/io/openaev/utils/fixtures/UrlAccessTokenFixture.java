package io.openaev.utils.fixtures;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.UrlAccessToken;
import io.openaev.database.model.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class UrlAccessTokenFixture {

  private static final BCryptPasswordEncoder HASHER = new BCryptPasswordEncoder();

  public static final String DEFAULT_RAW_TOKEN = "test-raw-token-" + UUID.randomUUID();
  public static final String DEFAULT_TARGET_URL = "/api/exercises";

  /**
   * Creates a valid, non-expired {@link UrlAccessToken} with the given exercise, user and target
   * URL.
   *
   * @param exercise the exercise scope of the token
   * @param user the user scope of the token
   * @param url the redirect URL embedded in the token
   * @return a new {@link UrlAccessToken} ready to persist
   */
  public static UrlAccessToken createValidToken(Exercise exercise, User user, String url) {
    UrlAccessToken token = new UrlAccessToken();
    token.setId(UUID.randomUUID().toString());
    token.setTokenHash(HASHER.encode(DEFAULT_RAW_TOKEN));
    token.setUrl(url);
    token.setExercise(exercise);
    token.setUser(user);
    token.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
    return token;
  }

  /**
   * Creates an expired {@link UrlAccessToken}.
   *
   * @param exercise the exercise scope of the token
   * @param user the user scope of the token
   * @return a new expired {@link UrlAccessToken} ready to persist
   */
  public static UrlAccessToken createExpiredToken(Exercise exercise, User user) {
    UrlAccessToken token = new UrlAccessToken();
    token.setId(UUID.randomUUID().toString());
    token.setTokenHash(HASHER.encode("expired-token-" + UUID.randomUUID()));
    token.setUrl(DEFAULT_TARGET_URL);
    token.setExercise(exercise);
    token.setUser(user);
    token.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
    return token;
  }

  /**
   * Creates a revoked {@link UrlAccessToken}.
   *
   * @param exercise the exercise scope of the token
   * @param user the user scope of the token
   * @return a new revoked {@link UrlAccessToken} ready to persist
   */
  public static UrlAccessToken createRevokedToken(Exercise exercise, User user) {
    UrlAccessToken token = createValidToken(exercise, user, DEFAULT_TARGET_URL);
    token.setRevokedAt(Instant.now().minus(1, ChronoUnit.HOURS));
    return token;
  }
}
