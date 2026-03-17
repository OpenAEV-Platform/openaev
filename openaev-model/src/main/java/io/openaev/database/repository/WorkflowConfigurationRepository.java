package io.openaev.database.repository;

import io.openaev.database.model.WorkflowConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowConfigurationRepository
    extends JpaRepository<WorkflowConfiguration, String> {}
