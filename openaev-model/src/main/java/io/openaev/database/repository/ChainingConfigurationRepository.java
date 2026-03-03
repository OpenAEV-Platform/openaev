package io.openaev.database.repository;

import io.openaev.database.model.ChainingConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChainingConfigurationRepository
    extends JpaRepository<ChainingConfiguration, String> {}
