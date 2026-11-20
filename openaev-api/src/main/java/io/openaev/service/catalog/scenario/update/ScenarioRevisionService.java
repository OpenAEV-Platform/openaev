package io.openaev.service.catalog.scenario.update;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.scenario.ScenarioRevision;
import io.openaev.database.repository.ScenarioRevisionRepository;
import io.openaev.database.repository.ScenarioRevisionRepository.RevisionRow;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.UserService;
import io.openaev.service.VariableService;
import io.openaev.service.scenario.ScenarioService;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The content of a catalogue scenario kept at install and before each update or rollback, to roll
 * back to.
 */
@Service
@RequiredArgsConstructor
public class ScenarioRevisionService {

  private final ScenarioRevisionRepository scenarioRevisionRepository;
  private final ScenarioService scenarioService;
  private final UserService userService;
  private final InjectService injectService;
  private final VariableService variableService;
  private final ScenarioContentReader reader;

  // -- LIST --

  /** The revisions of a scenario, newest first. */
  @Transactional(readOnly = true)
  public List<NumberedRevision> revisions(TxCtx ctx, String scenarioId) {
    Scenario scenario = scenarioService.scenario(ctx, scenarioId);
    List<RevisionRow> newestFirst =
        scenarioRevisionRepository.findRowsByTenantIdAndScenarioId(
            scenario.getTenant().getId(), scenario.getId());
    return IntStream.range(0, newestFirst.size())
        .mapToObj(index -> new NumberedRevision(newestFirst.size() - index, newestFirst.get(index)))
        .toList();
  }

  @Transactional(readOnly = true)
  public ScenarioRevision revision(TxCtx ctx, String scenarioId, String revisionId) {
    Scenario scenario = scenarioService.scenario(ctx, scenarioId);
    return scenarioRevisionRepository
        .findByTenantIdAndScenarioIdAndId(
            scenario.getTenant().getId(), scenario.getId(), revisionId)
        .orElseThrow(() -> new ElementNotFoundException("Revision not found"));
  }

  /** The content of a scenario as a revision keeps it and a merge compares it. */
  public ObjectNode content(Scenario scenario, Collection<Inject> injects) {
    return reader.fromEntities(
        scenario, variableService.variablesFromScenario(scenario.getId()), injects);
  }

  // -- CREATE --

  /** Keeps the content a scenario was installed with, to roll back to it. */
  @Transactional(rollbackFor = Exception.class)
  public void keepInstalled(Scenario scenario) {
    save(
        scenario,
        ScenarioRevision.Reason.INSTALL,
        content(scenario, injectService.findByScenarioId(scenario.getId())));
  }

  @Transactional(rollbackFor = Exception.class)
  public void keep(Scenario scenario, ScenarioRevision.Reason reason, ObjectNode content) {
    save(scenario, reason, content);
  }

  private void save(Scenario scenario, ScenarioRevision.Reason reason, ObjectNode content) {
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
