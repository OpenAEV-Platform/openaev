package io.openaev.service.catalog.scenario.update;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.scenario.ScenarioRevision;
import io.openaev.database.repository.ScenarioRevisionRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.UserService;
import io.openaev.service.scenario.ScenarioService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The content of a catalogue scenario kept before each update or rollback, to roll back to. */
@Service
@RequiredArgsConstructor
public class ScenarioRevisionService {

  private final ScenarioRevisionRepository scenarioRevisionRepository;
  private final ScenarioService scenarioService;
  private final UserService userService;

  // -- LIST --

  @Transactional(readOnly = true)
  public List<ScenarioRevision> revisions(TxCtx ctx, String scenarioId) {
    Scenario scenario = scenarioService.scenario(ctx, scenarioId);
    return scenarioRevisionRepository.findAllByTenantIdAndScenarioIdOrderByCreatedAtDesc(
        scenario.getTenant().getId(), scenario.getId());
  }

  @Transactional(readOnly = true)
  public ScenarioRevision revision(TxCtx ctx, String scenarioId, String revisionId) {
    Scenario scenario = scenarioService.scenario(ctx, scenarioId);
    return scenarioRevisionRepository
        .findByTenantIdAndScenarioIdAndId(
            scenario.getTenant().getId(), scenario.getId(), revisionId)
        .orElseThrow(() -> new ElementNotFoundException("Revision not found"));
  }

  // -- CREATE --

  @Transactional(rollbackFor = Exception.class)
  public void keep(Scenario scenario, ScenarioRevision.Reason reason, ObjectNode content) {
    ScenarioRevision revision = new ScenarioRevision();
    revision.setTenant(scenario.getTenant());
    revision.setScenario(scenario);
    revision.setReason(reason);
    revision.setCatalogVersion(scenario.getCatalogVersion());
    revision.setContent(content);
    revision.setUser(userService.currentUserOrNull());
    scenarioRevisionRepository.save(revision);
  }
}
