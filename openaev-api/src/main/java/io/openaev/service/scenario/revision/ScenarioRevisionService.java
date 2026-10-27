package io.openaev.service.scenario.revision;

import static io.openaev.service.scenario.revision.ScenarioContentReader.INJECT_IDS;
import static io.openaev.service.scenario.revision.ScenarioMerge.INJECTS;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.ScenarioRevision;
import io.openaev.database.repository.ScenarioRevisionRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.UserService;
import io.openaev.service.VariableService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Revisions of a catalogue scenario: photos of its content and of its base, in our format. */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ScenarioRevisionService {

  public static final String CONTENT = "content";
  public static final String BASE = "base";

  private final ScenarioRevisionRepository scenarioRevisionRepository;
  private final VariableService variableService;
  private final InjectService injectService;
  private final ScenarioContentReader reader;
  private final UserService userService;
  private final ObjectMapper mapper;

  @Transactional(readOnly = true)
  public List<ScenarioRevision> findAll(String scenarioId) {
    return scenarioRevisionRepository.findAllByScenarioIdOrderByNumberDesc(scenarioId);
  }

  @Transactional(readOnly = true)
  public ScenarioRevision find(String scenarioId, String revisionId) {
    return scenarioRevisionRepository
        .findById(revisionId)
        .filter(r -> r.getScenario().getId().equals(scenarioId))
        .orElseThrow(() -> new ElementNotFoundException("Revision not found"));
  }

  @Transactional(readOnly = true)
  public int lastNumber(String scenarioId) {
    return scenarioRevisionRepository.lastNumber(scenarioId);
  }

  /** Photo of the current content and base, taken before anything changes them. */
  public ScenarioRevision take(Scenario scenario, ScenarioRevision.Reason reason) {
    Collection<Inject> injects = injectService.findByScenarioId(scenario.getId());
    ObjectNode snapshot = mapper.createObjectNode();
    snapshot.set(CONTENT, local(scenario, injects));
    snapshot.set(BASE, base(scenario, injects));
    ScenarioRevision revision = new ScenarioRevision();
    revision.setScenario(scenario);
    revision.setTenant(scenario.getTenant());
    revision.setNumber(scenarioRevisionRepository.lastNumber(scenario.getId()) + 1);
    revision.setCatalogVersion(scenario.getVersion());
    revision.setReason(reason);
    revision.setAuthor(userService.currentUser());
    revision.setSnapshot(snapshot);
    return scenarioRevisionRepository.save(revision);
  }

  /** Revisions are taken on catalogue scenarios only: local ones are not versioned in step 1. */
  public void takeIfCatalog(Collection<Scenario> scenarios, ScenarioRevision.Reason reason) {
    scenarios.stream()
        .filter(s -> s.getCatalogId() != null && !s.isUninstalled())
        .collect(Collectors.toMap(Scenario::getId, Function.identity(), (a, b) -> a))
        .values()
        .forEach(s -> take(s, reason));
  }

  public ObjectNode local(Scenario scenario, Collection<Inject> injects) {
    return reader.fromEntities(
        scenario, injects, variableService.variablesFromScenario(scenario.getId()));
  }

  /**
   * The base: scenario blocks and the catalogue IDs it held, each inject's own base. An inject the
   * client deleted is only known by its ID, which is all the merge needs.
   */
  public ObjectNode base(Scenario scenario, Collection<Inject> injects) {
    ObjectNode base =
        scenario.getBaseContent() instanceof ObjectNode stored
            ? stored.deepCopy()
            : mapper.createObjectNode();
    Map<String, Inject> byCatalogId =
        injects.stream()
            .filter(i -> i.getCatalogId() != null)
            .collect(Collectors.toMap(Inject::getCatalogId, Function.identity(), (a, b) -> a));
    ObjectNode baseInjects = mapper.createObjectNode();
    base.path(INJECT_IDS)
        .forEach(
            id -> {
              Inject inject = byCatalogId.get(id.asText());
              JsonNode element = inject == null ? null : inject.getBaseContent();
              baseInjects.set(
                  id.asText(), element instanceof ObjectNode o ? o : mapper.createObjectNode());
            });
    base.set(INJECTS, baseInjects);
    base.remove(INJECT_IDS);
    return reader.canonical(base);
  }
}
