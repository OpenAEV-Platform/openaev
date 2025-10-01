package io.openaev.database.repository;

import io.openaev.database.model.DetectionRemediation;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DetectionRemediationRepository
    extends CrudRepository<DetectionRemediation, String>,
        JpaSpecificationExecutor<DetectionRemediation> {}
