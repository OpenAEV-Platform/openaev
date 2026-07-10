package io.openaev.database.repository.attackpath;

import io.openaev.database.model.attackpath.AttackPathExecutionFinding;
import io.openaev.database.model.attackpath.AttackPathExecutionFinding.AttackPathExecutionFindingId;
import org.springframework.data.repository.CrudRepository;

public interface AttackPathExecutionFindingRepository
    extends CrudRepository<AttackPathExecutionFinding, AttackPathExecutionFindingId> {}
