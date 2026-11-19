package io.openaev.database.repository;

import io.openaev.database.model.CatalogConnectorInjectorContract;
import io.openaev.database.model.ConnectorType;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogConnectorInjectorContractRepository
    extends JpaRepository<CatalogConnectorInjectorContract, String> {

  @Query(
      "SELECT contract FROM CatalogConnectorInjectorContract contract"
          + " JOIN FETCH contract.catalogConnector connector"
          + " WHERE connector.containerType = :injectorType"
          + " AND connector.deletedAt IS NULL"
          + " AND NOT EXISTS (SELECT 1 FROM Injector injector"
          + "   WHERE injector.type = connector.slug AND injector.tenantId = :tenantId)")
  List<CatalogConnectorInjectorContract> findAllOfInjectorsNotRegisteredInTenant(
      @Param("tenantId") @NotNull String tenantId,
      @Param("injectorType") @NotNull ConnectorType injectorType);
}
