package io.openaev.database.repository;

import io.openaev.database.model.RuleAttribute;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RuleAttributeRepository extends CrudRepository<RuleAttribute, UUID> {}
