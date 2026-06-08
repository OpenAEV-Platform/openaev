package io.openaev.database.repository;

import io.openaev.database.model.UrlAccessToken;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link UrlAccessToken} entities. Provides token lookup by hash, bulk revocation by
 * exercise scope, and purge operations for expired or revoked entries.
 */
@Repository
public interface UrlAccessTokenRepository extends JpaRepository<UrlAccessToken, String> {

  /**
   * Retrieves a URL access token by its SHA-256 hash.
   *
   * @param tokenHash SHA-256 hash of the raw URL token
   * @return an {@link Optional} containing the matching token when found
   */
  Optional<UrlAccessToken> findByTokenHash(@NotBlank String tokenHash);

  /**
   * Revokes all active URL access tokens linked to a specific exercise. Only non-revoked tokens are
   * updated.
   *
   * @param exerciseId exercise identifier used to scope revocation
   * @return number of rows updated
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
    UPDATE UrlAccessToken t SET t.revokedAt = CURRENT_TIMESTAMP
    WHERE t.exercise.id = :exerciseId AND t.revokedAt IS NULL
  """)
  int revokeAllByExerciseId(@Param("exerciseId") @NotBlank String exerciseId);

  /**
   * Deletes tokens that are no longer valid and older than the retention cutoff. A token is
   * eligible for deletion when it is expired or already revoked, and its creation date is older
   * than {@code cutoff}.
   *
   * @param cutoff retention cutoff instant
   * @return number of rows deleted
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
    DELETE FROM UrlAccessToken t
    WHERE (t.expiresAt < CURRENT_TIMESTAMP OR t.revokedAt IS NOT NULL)
      AND t.createdAt < :cutoff
  """)
  int deleteExpiredAndRevokedBefore(@Param("cutoff") @NotNull Instant cutoff);
}
