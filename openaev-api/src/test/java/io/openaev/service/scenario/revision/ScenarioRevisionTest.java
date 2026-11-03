package io.openaev.service.scenario.revision;

import static io.openaev.service.scenario.revision.ScenarioMerge.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.catalog.CatalogInstallService;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Revisions and rollback of a catalogue scenario (ADR-018)")
class ScenarioRevisionTest extends IntegrationTest {

  @Autowired private ScenarioApplyService scenarioApplyService;
  @Autowired private ScenarioRevisionService scenarioRevisionService;
  @Autowired private CatalogInstallService catalogInstallService;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectService injectService;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private ObjectMapper mapper;

  private Scenario scenario;
  private Inject phishing;
  private InjectorContract contract;

  /** A scenario as an install leaves it: its base is the content the catalogue wrote. */
  @BeforeEach
  void installed() {
    contract =
        injectorContractComposer
            .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
            .persist()
            .get();
    Scenario s = ScenarioFixture.getScenario();
    s.setName("Ransomware drill");
    s.setCatalogId("scenario-" + UUID.randomUUID());
    s.setCatalogRepository("filigran/openaev-scenarios");
    s.setVersion("1.0.0");
    s.setTenant(new Tenant(TenantContext.getCurrentTenant()));
    scenario = scenarioRepository.save(s);
    phishing = inject("a", "Phishing");
    inject("b", "Follow-up");
    ObjectNode local = scenarioRevisionService.local(scenario, injects());
    ObjectNode base = mapper.createObjectNode();
    base.set(SCENARIO, local.get(SCENARIO));
    base.set(MATERIALS, local.get(MATERIALS));
    base.set(VARIABLES, local.get(VARIABLES));
    base.putArray(ScenarioContentReader.INJECT_IDS).add("a").add("b");
    scenario.setBaseContent(base);
    scenarioRepository.save(scenario);
    for (Inject inject : injects()) {
      inject.setBaseContent(local.path(INJECTS).get(inject.getCatalogId()));
      injectRepository.save(inject);
    }
    scenarioRevisionService.take(scenario, ScenarioRevision.Reason.INSTALL);
  }

  private Inject inject(String catalogId, String title) {
    Inject inject = InjectFixture.getDefaultInject();
    inject.setScenario(scenario);
    inject.setTenant(scenario.getTenant());
    inject.setCatalogId(catalogId);
    inject.setTitle(title);
    inject.setInjectorContract(contract);
    inject.setContent(mapper.createObjectNode().put("subject", title));
    return injectRepository.save(inject);
  }

  private List<Inject> injects() {
    return List.copyOf(injectService.findByScenarioId(scenario.getId()));
  }

  /** What an update to a release changing the title of {@code a} leaves behind. */
  private ScenarioRevision updatedTitle() {
    ScenarioRevision before =
        scenarioRevisionService.take(scenario, ScenarioRevision.Reason.UPDATE);
    phishing.setTitle("Phishing v2");
    phishing.setBaseContent(
        scenarioRevisionService.local(scenario, List.of(phishing)).at("/injects/a"));
    injectRepository.save(phishing);
    scenario.setVersion("1.1.0");
    scenarioRepository.save(scenario);
    return before;
  }

  @Test
  @DisplayName("A rollback restores the content, keeps the configuration and the user's injects")
  void given_rollback_should_restoreContentOnly() throws Exception {
    // Arrange
    ScenarioRevision beforeUpdate = updatedTitle();
    phishing.setAllTeams(true);
    injectRepository.save(phishing);
    Inject mine = inject(null, "My own inject");

    // Act
    scenarioApplyService.apply(
        TxCtx.forTenant(TenantContext.getCurrentTenant()),
        scenario.getId(),
        new ScenarioApplyService.Target(null, beforeUpdate.getId()),
        Map.of(),
        Map.of(),
        null);

    // Assert
    Inject restored = injectRepository.findById(phishing.getId()).orElseThrow();
    assertThat(restored.getTitle()).isEqualTo("Phishing");
    assertThat(restored.isAllTeams()).isTrue();
    assertThat(injectRepository.findById(mine.getId())).isPresent();
    assertThat(scenarioRepository.findById(scenario.getId()).orElseThrow().getVersion())
        .isEqualTo("1.0.0");
    assertThat(scenarioRevisionService.findAll(scenario.getId()))
        .extracting(ScenarioRevision::getReason)
        .containsExactly(
            ScenarioRevision.Reason.ROLLBACK,
            ScenarioRevision.Reason.UPDATE,
            ScenarioRevision.Reason.INSTALL);
  }

  @Test
  @DisplayName("A conflict without a choice refuses the rollback; a choice settles it")
  void given_conflict_should_needChoice() throws Exception {
    // Arrange
    ScenarioRevision beforeUpdate = updatedTitle();
    phishing.setTitle("Phishing (ours)");
    injectRepository.save(phishing);
    ScenarioApplyService.Target target =
        new ScenarioApplyService.Target(null, beforeUpdate.getId());
    TxCtx ctx = TxCtx.forTenant(TenantContext.getCurrentTenant());

    // Act & Assert
    assertThrows(
        BadRequestException.class,
        () -> scenarioApplyService.apply(ctx, scenario.getId(), target, Map.of(), Map.of(), null));
    scenarioApplyService.apply(
        ctx, scenario.getId(), target, Map.of("injects:a:text", Choice.KEEP_MINE), Map.of(), null);
    assertThat(injectRepository.findById(phishing.getId()).orElseThrow().getTitle())
        .isEqualTo("Phishing (ours)");
  }

  @Test
  @DisplayName("A conflict settled by a typed value takes that value")
  void given_editedConflict_should_useTypedValue() throws Exception {
    // Arrange
    ScenarioRevision beforeUpdate = updatedTitle();
    phishing.setTitle("Phishing (ours)");
    injectRepository.save(phishing);
    ObjectNode text = mapper.createObjectNode().put("title", "Phishing (merged)");
    text.putNull("description");

    // Act
    scenarioApplyService.apply(
        TxCtx.forTenant(TenantContext.getCurrentTenant()),
        scenario.getId(),
        new ScenarioApplyService.Target(null, beforeUpdate.getId()),
        Map.of("injects:a:text", Choice.EDIT),
        Map.of("injects:a:text", text),
        null);

    // Assert
    assertThat(injectRepository.findById(phishing.getId()).orElseThrow().getTitle())
        .isEqualTo("Phishing (merged)");
  }

  @Test
  @DisplayName("A preview made before a change is refused")
  void given_stalePreview_should_refuse() {
    // Arrange
    ScenarioRevision beforeUpdate = updatedTitle();
    ScenarioApplyService.Target target =
        new ScenarioApplyService.Target(null, beforeUpdate.getId());
    String hash =
        scenarioApplyService.preview(scenario.getId(), target, Map.of(), Map.of()).localHash();
    phishing.setDescription("changed meanwhile");
    injectRepository.save(phishing);

    // Act & Assert
    assertThrows(
        ResponseStatusException.class,
        () ->
            scenarioApplyService.apply(
                TxCtx.forTenant(TenantContext.getCurrentTenant()),
                scenario.getId(),
                target,
                Map.of(),
                Map.of(),
                hash));
  }

  @Test
  @DisplayName("Editing an inject through the API keeps its catalogue ID")
  void given_injectEdit_should_keepCatalogId() {
    // Arrange
    InjectInput input = new InjectInput();
    input.setTitle("Phishing (edited)");
    input.setInjectorContract(contract.getId());
    input.setDependsDuration(0L);

    // Act
    injectService.updateInject(phishing.getId(), input);

    // Assert
    assertThat(injectRepository.findById(phishing.getId()).orElseThrow().getCatalogId())
        .isEqualTo("a");
  }

  @Test
  @DisplayName("Uninstall keeps the instance and its revisions, read-only")
  void given_uninstall_should_keepInstance() {
    // Act
    catalogInstallService.uninstall(scenario.getId());

    // Assert
    Scenario kept = scenarioRepository.findById(scenario.getId()).orElseThrow();
    assertThat(kept.isUninstalled()).isTrue();
    assertThat(scenarioRevisionService.findAll(scenario.getId())).hasSize(1);
    assertThrows(
        BadRequestException.class,
        () ->
            scenarioApplyService.apply(
                TxCtx.forTenant(TenantContext.getCurrentTenant()),
                scenario.getId(),
                new ScenarioApplyService.Target(
                    null, scenarioRevisionService.findAll(scenario.getId()).getFirst().getId()),
                Map.of(),
                Map.of(),
                null));
  }
}
