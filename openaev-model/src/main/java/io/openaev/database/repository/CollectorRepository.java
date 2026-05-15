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

  @Query(
      """
              SELECT DISTINCT c FROM Collector c
              WHERE c.collectorType IN (
                  SELECT dr.collectorType FROM DetectionRemediation dr
                  JOIN dr.payload p
                  WHERE p.id = :payloadId
              )
          """)
  List<Collector> findByPayloadId(@Param("payloadId") String payloadId);

  @Query(
      """
              SELECT DISTINCT c FROM Collector c
              WHERE c.collectorType IN (
                  SELECT dr.collectorType
                  FROM Inject i
                  JOIN i.injectorContract ic
                  JOIN ic.payload p
                  JOIN p.detectionRemediations dr
                  WHERE i.id = :injectId
              )
          """)
  List<Collector> findByInjectId(@Param("injectId") String injectId);
}
