package io.openaev.database.repository;

import io.openaev.database.model.Communication;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CommunicationRepository
    extends CrudRepository<Communication, String>, JpaSpecificationExecutor<Communication> {

  @NotNull
  Optional<Communication> findById(@NotNull String id);

  List<Communication> findByInjectId(@NotNull String injectId);

  @Query(
      "select c from Communication c "
          + "join c.users as user "
          + "join user.tenants as tenant "
          + "where user.id = :userId and tenant.id = :#{#tenantContext.currentTenant} "
          + "order by c.receivedAt desc")
  List<Communication> findByUser(@Param("userId") String userId);

  boolean existsByIdentifier(String identifier);
}
