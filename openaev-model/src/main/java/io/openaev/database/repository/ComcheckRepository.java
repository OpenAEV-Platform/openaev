package io.openaev.database.repository;

import io.openaev.database.model.Comcheck;
import io.openaev.database.raw.RawComcheckTenant;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ComcheckRepository
    extends CrudRepository<Comcheck, String>, JpaSpecificationExecutor<Comcheck> {

  @NotNull
  Optional<Comcheck> findById(@NotNull String id);

  /**
   * The running comchecks whose end date is past, with their simulation's tenant, null for a
   * comcheck with no simulation.
   */
  @Query(
      "select c.id as comcheck_id, e.tenant.id as tenant_id from Comcheck c left join c.exercise e"
          + " where c.state = 'RUNNING' and c.end <= :expirationDate")
  List<RawComcheckTenant> findComchecksToExpireWithTenant(
      @Param("expirationDate") Instant expirationDate);

  /**
   * Among {@code ids}, the comchecks running with an end date past, locked until the end of the
   * transaction. A comcheck finished by a player's answer after the gather for expiry is left out:
   * the lock waits for that answer to commit, then the state is read again. Ordered by id so two
   * runners lock in the same order.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select c from Comcheck c where c.id in :ids and c.state = 'RUNNING'"
          + " and c.end <= :expirationDate order by c.id")
  List<Comcheck> findRunningToExpireForUpdate(
      @Param("ids") List<String> ids, @Param("expirationDate") Instant expirationDate);
}
