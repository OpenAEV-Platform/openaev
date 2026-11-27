package io.openaev.database.repository;

import io.openaev.database.model.payload.PayloadRevocation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PayloadRevocationRepository extends JpaRepository<PayloadRevocation, String> {

  // -- ONE VERSION --

  boolean existsByTenantIdAndPayloadId(String tenantId, String payloadId);

  Optional<PayloadRevocation> findByTenantIdAndPayloadId(String tenantId, String payloadId);

  // -- SEVERAL VERSIONS --

  boolean existsByTenantIdAndPayloadIdIn(String tenantId, Collection<String> payloadIds);

  List<PayloadRevocation> findAllByTenantIdAndPayloadIdIn(
      String tenantId, Collection<String> payloadIds);

  // -- SIMULATION --

  /**
   * Whether an enabled inject of the simulation runs a payload version revoked in the payload's
   * tenant, answered in one statement instead of loading the injects.
   */
  @Query(
      """
      SELECT count(r) > 0 FROM Inject i
      JOIN i.injectorContract ic
      JOIN ic.payload p
      JOIN PayloadRevocation r ON r.payload = p AND r.tenant = p.tenant
      WHERE i.exercise.id = :simulationId AND i.enabled = true
      """)
  boolean existsForEnabledInjectOfSimulation(@Param("simulationId") String simulationId);

  // -- WHOLE TENANT --

  List<PayloadRevocation> findAllByTenantId(String tenantId);
}
