package io.openaev.database.repository;

import io.openaev.database.model.Collector;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CollectorRepository
    extends CrudRepository<Collector, String>, JpaSpecificationExecutor<Collector> {

  @NotNull
  Optional<Collector> findById(@NotNull String id);

  @NotNull
  Optional<Collector> findByType(@NotNull String type);

  @Query(
      """
              SELECT DISTINCT dr.collector FROM DetectionRemediation dr
              JOIN dr.payload p
              WHERE p.id = :payloadId
          """)
  List<Collector> findByPayloadId(@Param("payloadId") String payloadId);

  @Query(
      """
              SELECT DISTINCT dr.collector
              FROM Inject i
              JOIN i.injectorContract ic
              JOIN ic.payload p
              JOIN p.detectionRemediations dr
              WHERE i.id = :injectId
          """)
  List<Collector> findByInjectId(@Param("injectId") String injectId);
}
