package io.openaev.database.repository;

import io.openaev.database.model.TagRule;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TagRuleRepository
    extends CrudRepository<TagRule, String>, JpaSpecificationExecutor<TagRule> {

  // TODO v2: once tag_rules get v2 activated
  // https://github.com/OpenAEV-Platform/openaev/issues/6407, the SQL rewriter will scope
  // findById automatically - remove findByIdAndTenantId/existsByIdAndTenantId and switch callers
  // to plain findById/existsById
  @NotNull
  Optional<TagRule> findByIdAndTenantId(@NotNull String id, @NotNull String tenantId);

  boolean existsByIdAndTenantId(@NotNull String id, @NotNull String tenantId);

  @NotNull
  Optional<TagRule> findTagRuleByTagName(@NotNull String name);

  @Query("select tr from TagRule tr where tr.tag.id IN :tagids")
  List<TagRule> findByTags(@Param("tagids") List<String> tagIds);

  // TODO v2: once tag_rules get v2 activated
  // https://github.com/OpenAEV-Platform/openaev/issues/6407, drop the explicit
  // "tr.tenant.id = :#{#tenantContext.currentTenant}" predicate - the v1 TenantContext SpEL lookup
  // is no longer the source of truth once the SQL rewriter scopes this query
  @Query(
      "select tr from TagRule tr "
          + "where tr.tag.name IN :tagnames AND tr.tenant.id = :#{#tenantContext.currentTenant}")
  List<TagRule> findByTagNames(@Param("tagnames") List<String> tagNames);
}
