package io.openaev.database.repository;

import io.openaev.database.model.FindingOccurrence;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FindingOccurrenceRepository extends CrudRepository<FindingOccurrence, String> {

  /** Ids of the given findings that already have an occurrence on the inject. */
  // The join on the finding is what scopes the read: finding_occurrences has no tenant of its own
  @Query(
      "SELECT f.id FROM FindingOccurrence o JOIN o.finding f"
          + " WHERE o.inject.id = :injectId AND f.id IN :findingIds")
  List<String> findFindingIdsWithOccurrenceOnInject(
      @Param("injectId") String injectId, @Param("findingIds") Collection<String> findingIds);

  // The join on the finding is what scopes the read: finding_occurrences has no tenant of its own
  @Query(
      "SELECT o FROM FindingOccurrence o JOIN o.finding f"
          + " WHERE f.id = :findingId AND o.inject.id = :injectId")
  Optional<FindingOccurrence> findByFindingIdAndInjectId(
      @Param("findingId") String findingId, @Param("injectId") String injectId);
}
