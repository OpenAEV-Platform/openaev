package io.openaev.database.repository;

import io.openaev.database.model.CatalogConnectorInjectorContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogConnectorInjectorContractRepository
    extends JpaRepository<CatalogConnectorInjectorContract, String>,
        JpaSpecificationExecutor<CatalogConnectorInjectorContract> {}
