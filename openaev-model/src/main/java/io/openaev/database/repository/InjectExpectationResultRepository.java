package io.openaev.database.repository;

import io.openaev.database.model.InjectExpectationResult;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InjectExpectationResultRepository
    extends CrudRepository<InjectExpectationResult, String>,
        JpaSpecificationExecutor<InjectExpectationResult> {}
