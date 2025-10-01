package io.openaev.database.repository;

import io.openaev.database.model.Token;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TokenRepository
    extends CrudRepository<Token, String>, JpaSpecificationExecutor<Token> {

  @NotNull
  Optional<Token> findById(@NotNull String id);

  Optional<Token> findByValue(String value);

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
