package io.openaev.database.repository;

import io.openaev.database.model.Comcheck;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ComcheckRepository
    extends CrudRepository<Comcheck, String>, JpaSpecificationExecutor<Comcheck> {

  @NotNull
  Optional<Comcheck> findById(@NotNull String id);

  @Query(value = "select c from Comcheck c where c.state = 'RUNNING' and c.end <= :expirationDate")
  List<Comcheck> thatMustBeExpired(@Param("expirationDate") Instant expirationDate);
}
