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

  @NotNull
  Optional<TagRule> findById(@NotNull String id);

  @NotNull
  Optional<TagRule> findTagRuleByTagName(@NotNull String name);

  @Query("select tr from TagRule tr where tr.tag.id IN :tagids")
  List<TagRule> findByTags(@Param("tagids") List<String> tagIds);

  @Query(
      "select tr from TagRule tr "
          + "where tr.tag.name IN :tagnames AND tr.tenant.id = :#{#tenantContext.currentTenant}")
  List<TagRule> findByTagNames(@Param("tagnames") List<String> tagNames);
}
