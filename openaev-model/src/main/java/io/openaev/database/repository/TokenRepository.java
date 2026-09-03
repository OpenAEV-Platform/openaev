package io.openaev.database.repository;

import io.openaev.database.model.Token;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TokenRepository
    extends org.springframework.data.repository.Repository<Token, String> {

  Optional<Token> findByIdAndDeletedAtIsNull(String id);

  /**
   * Oldest (most stable) API token of a user. Used by the reporting renderer to authenticate the
   * headless browser as the acting user; the token value never leaves the server process.
   */
  Optional<Token> findFirstByUserIdAndDeletedAtIsNullOrderByCreatedAsc(String userId);

  List<Token> findByUserIdAndDeletedAtIsNullOrderByCreatedAsc(String userId);

  boolean existsByUserIdAndDeletedAtIsNull(String userId);

  Token save(Token token);

  /** Bootstrap only: resolves the admin token even when soft-deleted, so it can be restored. */
  @Query("SELECT t FROM Token t WHERE t.id = :id")
  Optional<Token> findByIdIncludingDeleted(@Param("id") String id);

  // -- ADMIN --

  // Custom query to bypass ID generator on Token property
  @Modifying
  @Query(
      value =
          "insert into tokens(token_id, token_user, token_value, token_created_at) "
              + "values (:id, :user, :value, :createdAt)",
      nativeQuery = true)
  void createToken(
      @Param("id") String tokenId,
      @Param("user") String adminUser,
      @Param("value") String tokenValue,
      @Param("createdAt") Instant createdAt);
}
