package io.openaev.database.repository;

import io.openaev.database.model.Channel;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChannelRepository
    extends CrudRepository<Channel, String>, JpaSpecificationExecutor<Channel> {

  List<Channel> findByNameIgnoreCase(String name);

  List<Channel> findByNameIgnoreCaseAndTenantId(String name, String tenantId);

  List<Channel> findDistinctByArticlesExerciseId(String simulationId);

  List<Channel> findDistinctByArticlesScenarioId(String scenarioId);
}
