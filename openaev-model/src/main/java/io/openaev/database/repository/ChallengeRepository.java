package io.openaev.database.repository;

import io.openaev.database.model.Challenge;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChallengeRepository
    extends CrudRepository<Challenge, String>, JpaSpecificationExecutor<Challenge> {

  // challenges is fully on v2 isolation (TenantStatementInspector): CrudRepository#findById()
  // is scoped like any other query, no JPQL override needed to dodge a v1 @Filter bypass.

  @NotNull
  List<Challenge> findByNameIgnoreCase(@NotNull final String name);

  /**
   * Loads challenges by id with their {@code challenge_documents} fetched in the same query. The
   * find endpoint returns raw entities whose lazy {@code documents} would otherwise be serialized
   * open-in-view after the scoped transaction commits and fail closed to an empty array once {@code
   * documents} is v2-active. Fetching the association inside the scoped query keeps the links
   * visible with a single statement, instead of one lazy-initialization SELECT per challenge. The
   * {@code documents} table stays scoped by the statement inspector, which wraps each joined table
   * in a filtered sub-query, so the outer join still returns a challenge that carries no in-scope
   * document.
   */
  @Query("select distinct c from Challenge c left join fetch c.documents where c.id in :ids")
  @NotNull
  List<Challenge> findAllByIdInFetchingDocuments(@Param("ids") @NotNull final List<String> ids);

  /**
   * Loads every in-scope challenge with its {@code challenge_documents} fetched in the same query,
   * for the same reason as {@link #findAllByIdInFetchingDocuments(List)}. A challenge carrying
   * several documents is still returned once: Hibernate removes the duplicate root entities a
   * collection fetch join produces, so the query needs no {@code distinct}.
   */
  @Query("select c from Challenge c left join fetch c.documents")
  @NotNull
  List<Challenge> findAllFetchingDocuments();

  /**
   * Per-tenant business-key lookup for find-or-create paths (e.g. import): looking up by the bare
   * name under a multi-tenant read scope could match one row per in-scope tenant and silently reuse
   * another tenant's challenge. Callers must resolve the write tenant first and look up scoped to
   * it.
   */
  @NotNull
  List<Challenge> findByNameIgnoreCaseAndTenantId(
      @NotNull final String name, @NotNull final String tenantId);
}
