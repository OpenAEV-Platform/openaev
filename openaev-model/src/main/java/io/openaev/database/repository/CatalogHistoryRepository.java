package io.openaev.database.repository;

import io.openaev.database.model.catalog.CatalogHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogHistoryRepository extends JpaRepository<CatalogHistory, String> {}
