package io.openaev.database.repository;

import io.openaev.database.model.Tag;
import io.openaev.database.raw.RawTag;
import io.openaev.utils.Constants;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TagRepository extends CrudRepository<Tag, String>, JpaSpecificationExecutor<Tag> {

  @NotNull
  Optional<Tag> findById(@NotNull String id);

  @NotNull
  Optional<Tag> findByName(@NotNull final String name);

  @NotNull
  List<Tag> findByNameIgnoreCase(@NotNull final String name);

  @Query(
      value =
          "SELECT t.tag_id, t.tag_name, t.tag_color, "
              + "t.tag_created_at, t.tag_updated_at "
              + "FROM tags t "
              + "WHERE t.tag_updated_at > :from ORDER BY t.tag_updated_at LIMIT "
              + Constants.INDEXING_RECORD_SET_SIZE
              + ";",
      nativeQuery = true)
  List<RawTag> findForIndexing(@Param("from") Instant from);
}
