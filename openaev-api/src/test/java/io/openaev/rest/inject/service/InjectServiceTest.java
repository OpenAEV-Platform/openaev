package io.openaev.rest.inject.service;

import static java.time.Instant.now;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.executors.utils.ExecutorUtils;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.healthcheck.enums.ExternalServiceDependency;
import io.openaev.healthcheck.utils.HealthCheckUtils;
import io.openaev.injectors.email.service.ImapService;
import io.openaev.injectors.email.service.SmtpService;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.form.*;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.tag.TagService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.AssetService;
import io.openaev.service.EndpointService;
import io.openaev.service.InjectorService;
import io.openaev.service.TagRuleService;
import io.openaev.service.UserService;
import io.openaev.service.chaining.ConditionService;
import io.openaev.service.chaining.StepTargetingService;
import io.openaev.service.threat_arsenal.ThreatArsenalService;
import io.openaev.utils.InjectUtils;
import io.openaev.utils.TargetType;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.injector_contract.InjectorContractContentUtils;
import io.openaev.utils.mapper.InjectExpectationMapper;
import io.openaev.utils.mapper.InjectMapper;
import io.openaev.utils.mapper.InjectStatusMapper;
import io.openaev.utils.mapper.PayloadMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InjectServiceTest {

  private static final String INJECT_ID = "injectid";

  @Mock private InjectRepository injectRepository;

  @Mock private AssetService assetService;

  @Mock private AssetGroupService assetGroupService;

  @Mock private TeamRepository teamRepository;

  @Mock private ExecutionTraceRepository executionTraceRepository;

  @Mock private InjectStatusRepository injectStatusRepository;

  @Mock private InjectDocumentRepository injectDocumentRepository;

  @Mock private InjectUtils injectUtils;

  @Mock private InjectStatusMapper injectStatusMapper;

  @Mock private PayloadMapper payloadMapper;

  @Mock private InjectExpectationMapper injectExpectationMapper;

  @Mock private InjectorContractService injectorContractService;

  @Mock private UserService userService;

  @Mock private EnterpriseEditionService enterpriseEditionService;

  @Mock private EndpointService endpointService;

  @Mock private MethodSecurityExpressionHandler methodSecurityExpressionHandler;

  @Mock private TagRuleService tagRuleService;

  @Mock private TagService tagService;

  @Mock private DocumentService documentService;

  @Mock private TagRepository tagRepository;

  @Mock private DocumentRepository documentRepository;

  @Mock private PayloadRepository payloadRepository;

  @Mock private ThreatArsenalService threatArsenalService;

  @Mock private LicenseCacheManager licenseCacheManager;

  @Mock private SmtpService smtpService;

  @Mock private ImapService imapService;

  @Mock private CollectorService collectorService;

  @Mock private InjectorService injectorService;

  @Mock private AssetAgentJobRepository assetAgentJobRepository;

  @Mock private StepTargetingService stepTargetingService;

  @Mock private ConditionService conditionService;

  @Spy private InjectorContractContentUtils injectorContractContentUtils;

  @Mock private ApplicationEventPublisher eventPublisher;

  ObjectMapper mapper;

  @InjectMocks private InjectService injectService;
  @InjectMocks private InjectStatusService injectStatusService;

  @BeforeEach
  void setUp() {
    // InjectStatusService serializes the inject (Instant / Optional fields) into the SSE
    // BaseEvent payload on status transitions, so the mapper needs the JSR-310/JDK8 modules.
    mapper = new ObjectMapper().findAndRegisterModules();
    ReflectionTestUtils.setField(injectService, "mapper", mapper);
    ReflectionTestUtils.setField(injectStatusService, "mapper", mapper);
    ReflectionTestUtils.setField(injectStatusService, "auditLogger", Optional.empty());
    ReflectionTestUtils.setField(
        injectService,
        "healthCheckUtils",
        new HealthCheckUtils(
            new ExecutorUtils(assetAgentJobRepository), stepTargetingService, conditionService));
    ReflectionTestUtils.setField(
        injectService,
        "injectMapper",
        new InjectMapper(
            injectStatusMapper,
            payloadMapper,
            injectExpectationMapper,
            injectUtils,
            new HealthCheckUtils(
                new ExecutorUtils(assetAgentJobRepository),
                stepTargetingService,
                conditionService)));
    ReflectionTestUtils.setField(
        injectService, "injectorContractContentUtils", injectorContractContentUtils);
  }

  @Test
  public void testApplyDefaultAssetGroupsToInject_WITH_unexisting_inject() {
    doReturn(Optional.empty()).when(injectRepository).findById(INJECT_ID);
    assertThrows(
        ElementNotFoundException.class,
        () -> injectService.applyDefaultAssetGroupsToInject(INJECT_ID, List.of()));
  }

  @Test
  public void testApplyDefaultAssetGroupsToInject_WITH_default_assets_to_add() {
    AssetGroup assetGroup1 = getAssetGroup("assetgroup1");
    AssetGroup assetGroup2 = getAssetGroup("assetgroup2");
    AssetGroup assetGroup3 = getAssetGroup("assetgroup3");
    AssetGroup assetGroup4 = getAssetGroup("assetgroup4");
    Inject inject = new Inject();
    inject.setId(INJECT_ID);
    inject.setAssetGroups(List.of(assetGroup1, assetGroup2, assetGroup3));
    doReturn(Optional.of(inject)).when(injectRepository).findById(INJECT_ID);

    injectService.applyDefaultAssetGroupsToInject(INJECT_ID, List.of(assetGroup4));

    ArgumentCaptor<Inject> injectCaptor = ArgumentCaptor.forClass(Inject.class);
    verify(injectRepository).save(injectCaptor.capture());
    Inject capturedInject = injectCaptor.getValue();
    assertEquals(INJECT_ID, capturedInject.getId());
    assertEquals(
        new HashSet<>(List.of(assetGroup1, assetGroup2, assetGroup3, assetGroup4)),
        new HashSet<>(capturedInject.getAssetGroups()));
  }

  @Test
  public void testApplyDefaultAssetGroupsToInject_WITH_no_change() {
    AssetGroup assetGroup1 = getAssetGroup("assetgroup1");
    AssetGroup assetGroup2 = getAssetGroup("assetgroup2");
    AssetGroup assetGroup3 = getAssetGroup("assetgroup3");
    Inject inject = new Inject();
    inject.setId(INJECT_ID);
    inject.setAssetGroups(List.of(assetGroup1, assetGroup2, assetGroup3));
    doReturn(Optional.of(inject)).when(injectRepository).findById(INJECT_ID);

    injectService.applyDefaultAssetGroupsToInject(INJECT_ID, List.of(assetGroup1));

    verify(injectRepository, never()).save(any());
  }

  private AssetGroup getAssetGroup(String name) {
    AssetGroup assetGroup = AssetGroupFixture.createDefaultAssetGroup(name);
    assetGroup.setId(name);
    return assetGroup;
  }

  @DisplayName("Test get inject specification with valid search input")
  @Test
  void getInjectSpecificationWithValidSearchInput() {
    // Arrange
    InjectBulkProcessingInput input = new InjectBulkProcessingInput();
    input.setSearchPaginationInput(new SearchPaginationInput());
    input.getSearchPaginationInput().setFilterGroup(new Filters.FilterGroup());
    input.getSearchPaginationInput().setTextSearch("test");

    when(userService.currentUser()).thenReturn(new User());

    // Act
    Specification<Inject> specification =
        injectService.getInjectSpecification(input, Grant.GRANT_TYPE.OBSERVER);

    // Assert
    assertNotNull(specification);
  }

  @DisplayName("Test get inject specification with inject IDs to process")
  @Test
  void getInjectSpecificationWithInjectIDsToProcess() {
    // Arrange
    InjectBulkProcessingInput input = new InjectBulkProcessingInput();
    input.setInjectIDsToProcess(List.of("id1", "id2"));

    when(userService.currentUser()).thenReturn(new User());

    // Act
    Specification<Inject> specification =
        injectService.getInjectSpecification(input, Grant.GRANT_TYPE.OBSERVER);

    // Assert
    assertNotNull(specification);
  }

  @DisplayName("Test get inject specification with inject IDs to ignore")
  @Test
  void getInjectSpecificationWithInjectIDsToIgnore() {
    // Arrange
    InjectBulkProcessingInput input = new InjectBulkProcessingInput();
    input.setInjectIDsToProcess(List.of("id1", "id2"));
    input.setInjectIDsToIgnore(List.of("id3"));

    when(userService.currentUser()).thenReturn(new User());

    // Act
    Specification<Inject> specification =
        injectService.getInjectSpecification(input, Grant.GRANT_TYPE.OBSERVER);

    // Assert
    assertNotNull(specification);
  }

  @DisplayName("Test get inject specification with null input")
  @Test
  void getInjectSpecificationWithNullInput() {
    // Arrange
    InjectBulkProcessingInput input = new InjectBulkProcessingInput();

    // Act & assert
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () -> injectService.getInjectSpecification(input, Grant.GRANT_TYPE.OBSERVER));

    // Assert
    assertEquals(
        "Either inject_ids_to_process or search_pagination_input must be provided, and not both at the same time",
        exception.getMessage());
  }

  @DisplayName("Test bulk update injects with valid operations")
  @Test
  void bulkUpdateInjectsWithValidOperations() {
    // Arrange
    Team t0 = new Team();
    t0.setId("team0");
    Asset a0 = new Asset();
    a0.setId("asset0");
    InjectorContract technicalContract =
        buildContractWithContentFields("teams", "assets", "asset_groups");
    Inject i1 = new Inject();
    i1.setId("inject1");
    i1.setInjectorContract(technicalContract);
    Inject i2 = new Inject();
    i2.setId("inject2");
    i2.setInjectorContract(technicalContract);
    i1.setTeams(new ArrayList<>(List.of(t0)));
    i1.setAssets(new ArrayList<>(List.of(a0)));

    List<Inject> injectsToUpdate = List.of(i1, i2);

    InjectBulkUpdateOperation ope1 = new InjectBulkUpdateOperation();
    ope1.setField(InjectBulkUpdateSupportedFields.TEAMS);
    ope1.setOperation(InjectBulkUpdateSupportedOperations.ADD);
    ope1.setValues(List.of("team1", "team2"));
    InjectBulkUpdateOperation ope2 = new InjectBulkUpdateOperation();
    ope2.setField(InjectBulkUpdateSupportedFields.ASSETS);
    ope2.setOperation(InjectBulkUpdateSupportedOperations.REPLACE);
    ope2.setValues(List.of("asset1", "asset2"));

    List<InjectBulkUpdateOperation> operations = List.of(ope1, ope2);

    Team t1 = new Team();
    t1.setId("team1");
    Team t2 = new Team();
    t2.setId("team2");
    List<Team> tList = List.of(t1, t2);

    Asset a1 = new Asset();
    a1.setId("asset1");
    Asset a2 = new Asset();
    a2.setId("asset2");
    List<Asset> aList = List.of(a1, a2);

    when(teamRepository.findAllById(any())).thenReturn(tList);
    when(assetService.assets(anyList())).thenReturn(aList);

    // Expected results
    Inject i1updated = new Inject();
    i1updated.setId("inject1");
    Inject i2updated = new Inject();
    i2updated.setId("inject2");
    i1updated.setTeams(new ArrayList<>(List.of(t0)));
    i1updated.getTeams().addAll(tList);
    i1updated.setAssets(aList);
    i2updated.setTeams(tList);
    i2updated.setAssets(aList);

    List<Inject> expectedUpdatedInjects = List.of(i1updated, i2updated);

    when(injectRepository.saveAll(expectedUpdatedInjects)).thenReturn(expectedUpdatedInjects);

    // Act
    List<Inject> updatedInjects = injectService.bulkUpdateInject(injectsToUpdate, operations);

    // Assert
    assertNotNull(updatedInjects);
    assertEquals(2, updatedInjects.size());
    // test that we added the teams and replaced the assets to the existing lists
    assertEquals(1 + tList.size(), updatedInjects.getFirst().getTeams().size());
    assertEquals(aList.size(), updatedInjects.getFirst().getAssets().size());
    assertTrue(updatedInjects.getFirst().getTeams().containsAll(tList));
    assertTrue(updatedInjects.getFirst().getAssets().containsAll(aList));
    assertTrue(updatedInjects.get(1).getTeams().containsAll(tList));
    assertTrue(updatedInjects.get(1).getAssets().containsAll(aList));
  }

  @DisplayName(
      "Test bulk update injects with mixed contract types only applies asset operations to contracts declaring asset fields")
  @Test
  void given_injects_with_mixed_contract_types_should_apply_asset_operations_per_contract_fields() {
    // Arrange
    Team t1 = new Team();
    t1.setId("team1");
    Asset a0 = new Asset();
    a0.setId("asset0");
    Asset a1 = new Asset();
    a1.setId("asset1");
    AssetGroup ag0 = new AssetGroup();
    ag0.setId("assetGroup0");
    AssetGroup ag1 = new AssetGroup();
    ag1.setId("assetGroup1");

    // (a) Executor-backed payload contract declaring assets and asset_groups fields
    InjectorContract payloadContract =
        buildContractWithContentFields("assets", "asset_groups", "expectations");
    payloadContract.setNeedsExecutor(true);
    Inject payloadInject = buildInjectForBulkUpdate("payloadInject", payloadContract, a0, ag0);

    // (b) Agentless technical contract (Nmap/Nuclei-like): needs_executor is false but the
    // contract content declares assets and asset_groups fields, so asset operations MUST apply
    InjectorContract agentlessContract =
        buildContractWithContentFields("assets", "asset_groups", "expectations");
    agentlessContract.setNeedsExecutor(false);
    Inject agentlessInject =
        buildInjectForBulkUpdate("agentlessInject", agentlessContract, a0, ag0);

    // (c) Email-like contract without any asset field: asset operations must be skipped
    InjectorContract emailContract =
        buildContractWithContentFields("teams", "subject", "body", "expectations");
    emailContract.setNeedsExecutor(false);
    Inject emailInject = buildInjectForBulkUpdate("emailInject", emailContract, a0, ag0);

    // (d) Inject without any contract: asset operations must be skipped
    Inject contractlessInject = buildInjectForBulkUpdate("contractlessInject", null, a0, ag0);

    List<Inject> injectsToUpdate =
        List.of(payloadInject, agentlessInject, emailInject, contractlessInject);

    InjectBulkUpdateOperation teamsOp = new InjectBulkUpdateOperation();
    teamsOp.setField(InjectBulkUpdateSupportedFields.TEAMS);
    teamsOp.setOperation(InjectBulkUpdateSupportedOperations.ADD);
    teamsOp.setValues(List.of("team1"));
    InjectBulkUpdateOperation assetsOp = new InjectBulkUpdateOperation();
    assetsOp.setField(InjectBulkUpdateSupportedFields.ASSETS);
    assetsOp.setOperation(InjectBulkUpdateSupportedOperations.REPLACE);
    assetsOp.setValues(List.of("asset1"));
    InjectBulkUpdateOperation assetGroupsOp = new InjectBulkUpdateOperation();
    assetGroupsOp.setField(InjectBulkUpdateSupportedFields.ASSET_GROUPS);
    assetGroupsOp.setOperation(InjectBulkUpdateSupportedOperations.REPLACE);
    assetGroupsOp.setValues(List.of("assetGroup1"));

    List<InjectBulkUpdateOperation> operations = List.of(teamsOp, assetsOp, assetGroupsOp);

    when(teamRepository.findAllById(any())).thenReturn(List.of(t1));
    when(assetService.assets(anyList())).thenReturn(List.of(a1));
    when(assetGroupService.assetGroups(anyList())).thenReturn(List.of(ag1));
    when(injectRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    List<Inject> updatedInjects = injectService.bulkUpdateInject(injectsToUpdate, operations);

    // Assert
    assertNotNull(updatedInjects);
    assertEquals(4, updatedInjects.size());

    Inject updatedPayload = updatedInjects.getFirst();
    Inject updatedAgentless = updatedInjects.get(1);
    Inject updatedEmail = updatedInjects.get(2);
    Inject updatedContractless = updatedInjects.get(3);

    // Teams are applied to every inject, whatever the contract type
    assertEquals(List.of(t1), updatedPayload.getTeams());
    assertEquals(List.of(t1), updatedAgentless.getTeams());
    assertEquals(List.of(t1), updatedEmail.getTeams());
    assertEquals(List.of(t1), updatedContractless.getTeams());

    // Assets and asset groups are applied to the executor-backed payload inject...
    assertEquals(List.of(a1), updatedPayload.getAssets());
    assertEquals(List.of(ag1), updatedPayload.getAssetGroups());

    // ... AND to the agentless technical inject, since its contract declares asset fields
    assertEquals(List.of(a1), updatedAgentless.getAssets());
    assertEquals(List.of(ag1), updatedAgentless.getAssetGroups());

    // ... but skipped for the email inject (no asset fields) and the contract-less inject
    assertEquals(List.of(a0), updatedEmail.getAssets());
    assertEquals(List.of(ag0), updatedEmail.getAssetGroups());
    assertEquals(List.of(a0), updatedContractless.getAssets());
    assertEquals(List.of(ag0), updatedContractless.getAssetGroups());
  }

  /** Builds an injector contract whose content declares the given field keys. */
  private InjectorContract buildContractWithContentFields(String... fieldKeys) {
    InjectorContract contract = new InjectorContract();
    String fields =
        Arrays.stream(fieldKeys)
            .map(key -> "{\"key\":\"" + key + "\",\"type\":\"text\"}")
            .collect(java.util.stream.Collectors.joining(","));
    contract.setContent("{\"fields\":[" + fields + "]}");
    return contract;
  }

  /** Builds an inject pre-populated with one asset and one asset group for bulk update tests. */
  private Inject buildInjectForBulkUpdate(
      String id, InjectorContract contract, Asset initialAsset, AssetGroup initialAssetGroup) {
    Inject inject = new Inject();
    inject.setId(id);
    inject.setInjectorContract(contract);
    inject.setTeams(new ArrayList<>());
    inject.setAssets(new ArrayList<>(List.of(initialAsset)));
    inject.setAssetGroups(new ArrayList<>(List.of(initialAssetGroup)));
    return inject;
  }

  @DisplayName("Test bulk update injects with empty operations")
  @Test
  void bulkUpdateInjectsWithEmptyOperations() {
    // Arrange
    List<Inject> injectsToUpdate = List.of(new Inject(), new Inject());
    List<InjectBulkUpdateOperation> operations = List.of();

    when(injectRepository.saveAll(injectsToUpdate)).thenReturn(injectsToUpdate);

    // Act
    List<Inject> updatedInjects = injectService.bulkUpdateInject(injectsToUpdate, operations);

    // Assert
    assertNotNull(updatedInjects);
    assertEquals(2, updatedInjects.size());
    assertTrue(updatedInjects.getFirst().getTeams().isEmpty());
    assertTrue(updatedInjects.getFirst().getAssets().isEmpty());
    assertTrue(updatedInjects.getFirst().getAssetGroups().isEmpty());
  }

  @DisplayName("Test bulk update injects with non-existing team")
  @Test
  void bulkUpdateInjectsWithNonExistingEntity() {
    // Arrange
    Inject i1 = new Inject();
    i1.setId("inject1");
    Inject i2 = new Inject();
    i2.setId("inject2");
    List<Inject> injectsToUpdate = List.of(i1, i2);

    InjectBulkUpdateOperation ope = new InjectBulkUpdateOperation();
    ope.setField(InjectBulkUpdateSupportedFields.TEAMS);
    ope.setOperation(InjectBulkUpdateSupportedOperations.ADD);
    ope.setValues(List.of("nonExistingTeam"));

    List<InjectBulkUpdateOperation> operations = List.of(ope);

    when(teamRepository.findAllById(any())).thenReturn(List.of());

    // Expected results
    Inject i1updated = new Inject();
    i1updated.setId("inject1");
    Inject i2updated = new Inject();
    i2updated.setId("inject2");

    List<Inject> expectedUpdatedInjects = List.of(i1updated, i2updated);

    when(injectRepository.saveAll(expectedUpdatedInjects)).thenReturn(expectedUpdatedInjects);

    // Act
    List<Inject> updatedInjects = injectService.bulkUpdateInject(injectsToUpdate, operations);

    // Assert
    assertNotNull(updatedInjects);
    assertEquals(expectedUpdatedInjects.size(), updatedInjects.size());
    assertTrue(updatedInjects.getFirst().getTeams().isEmpty());
    assertTrue(updatedInjects.get(1).getTeams().isEmpty());
  }

  @DisplayName("Test get injects and check is planner with valid input")
  @Test
  void getInjectsAndCheckPermissionWithValidInput() {
    // Arrange
    InjectBulkProcessingInput input = new InjectBulkProcessingInput();
    input.setSearchPaginationInput(new SearchPaginationInput());
    input.getSearchPaginationInput().setFilterGroup(new Filters.FilterGroup());
    input.getSearchPaginationInput().setTextSearch("test");

    List<Inject> injects = List.of(new Inject(), new Inject());
    //noinspection unchecked
    when(injectRepository.findAll(any(Specification.class))).thenReturn(injects);

    when(userService.currentUser()).thenReturn(new User());

    // Act
    List<Inject> result =
        injectService.getInjectsAndCheckPermission(input, Grant.GRANT_TYPE.PLANNER);

    // Assert
    assertNotNull(result);
    assertEquals(2, result.size());
  }

  @DisplayName("Test get injects and check is planner with inject IDs to process")
  @Test
  void getInjectsAndCheckPermissionWithInjectIDsToProcess() {
    // Arrange
    InjectBulkProcessingInput input = new InjectBulkProcessingInput();
    input.setInjectIDsToProcess(List.of("id1", "id2"));

    List<Inject> injects = List.of(new Inject(), new Inject());

    when(userService.currentUser()).thenReturn(new User());

    //noinspection unchecked
    when(injectRepository.findAll(any(Specification.class))).thenReturn(injects);

    // Act
    List<Inject> result =
        injectService.getInjectsAndCheckPermission(input, Grant.GRANT_TYPE.PLANNER);

    // Assert
    assertNotNull(result);
    assertEquals(2, result.size());
  }

  @DisplayName("Test get injects and check is planner with inject IDs to ignore")
  @Test
  void getInjectsAndCheckPermissionWithInjectIDsToIgnore() {
    // Arrange
    InjectBulkProcessingInput input = new InjectBulkProcessingInput();
    input.setInjectIDsToProcess(List.of("id1", "id2"));
    input.setInjectIDsToIgnore(List.of("id3"));

    when(userService.currentUser()).thenReturn(new User());

    List<Inject> injects = List.of(new Inject(), new Inject());

    //noinspection unchecked
    when(injectRepository.findAll(any(Specification.class))).thenReturn(injects);

    // Act
    List<Inject> result =
        injectService.getInjectsAndCheckPermission(input, Grant.GRANT_TYPE.PLANNER);

    // Assert
    assertNotNull(result);
    assertEquals(2, result.size());
  }

  @DisplayName("Test get injects and check is planner with null input")
  @Test
  void getInjectsAndCheckPermissionWithNullInput() {
    // Arrange
    InjectBulkProcessingInput input = new InjectBulkProcessingInput();

    // Act & assert
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () -> injectService.getInjectsAndCheckPermission(input, Grant.GRANT_TYPE.PLANNER));

    // Assert
    assertEquals(
        "Either inject_ids_to_process or search_pagination_input must be provided, and not both at the same time",
        exception.getMessage());
  }

  @DisplayName("Test delete all injects by valid IDs")
  @Test
  void deleteAllInjectsByValidIds() {
    // Arrange
    List<String> injectIds = List.of("id1", "id2");

    doNothing().when(injectRepository).deleteByAllIdsNative(injectIds);

    // Act
    injectService.deleteAllByIds(injectIds);

    // Assert
    verify(injectRepository, times(1)).deleteByAllIdsNative(injectIds);
  }

  @DisplayName("Test delete all injects by empty IDs list")
  @Test
  void deleteAllInjectsByEmptyIdsList() {
    // Arrange
    List<String> injectIds = List.of();

    // Act
    injectService.deleteAllByIds(injectIds);

    // Assert
    verify(injectRepository, never()).deleteByAllIdsNative(any());
  }

  @DisplayName("Test delete all injects by null IDs list")
  @Test
  void deleteAllInjectsByNullIdsList() {
    // Arrange
    List<String> injectIds = null;

    // Act
    injectService.deleteAllByIds(injectIds);

    // Assert
    verify(injectRepository, never()).deleteByAllIdsNative(any());
  }

  @DisplayName("Test canApplyTargetType with manual inject")
  @Test
  void testCanApplyAssetToInject_WITH_no_assetGroup() throws JsonProcessingException {
    InjectorContract injectorContract = new InjectorContract();
    injectorContract.setContent(
        "{\"manual\":true,\"fields\":[{\"key\":\"content\",\"label\":\"Content\",\"mandatory\":true,\"readOnly\":false,\"mandatoryGroups\":null,\"linkedFields\":[],\"linkedValues\":[],\"defaultValue\":\"\",\"richText\":false,\"type\":\"textarea\"}]}");
    injectorContract.setConvertedContent(
        (ObjectNode) mapper.readTree(injectorContract.getContent()));
    Inject inject = new Inject();
    doCallRealMethod().when(injectorContractService).checkTargetSupport(any(), any());
    inject.setInjectorContract(injectorContract);

    assertFalse(injectService.canApplyTargetType(inject, TargetType.ASSETS_GROUPS));
  }

  @DisplayName("Test canApplyTargetType with inject with asset group")
  @Test
  void testCanApplyAssetGroupToInject_WITH_assets() throws JsonProcessingException {
    InjectorContract injectorContract = new InjectorContract();
    injectorContract.setContent(
        "{\"manual\":true,\"fields\":[{\"key\":\"assetgroups\",\"label\":\"Content\",\"mandatory\":true,\"readOnly\":false,\"mandatoryGroups\":null,\"linkedFields\":[],\"linkedValues\":[],\"defaultValue\":\"\",\"richText\":false,\"type\":\"asset-group\"}]}");
    injectorContract.setConvertedContent(
        (ObjectNode) mapper.readTree(injectorContract.getContent()));
    Inject inject = new Inject();
    doCallRealMethod().when(injectorContractService).checkTargetSupport(any(), any());
    inject.setInjectorContract(injectorContract);

    assertTrue(injectService.canApplyTargetType(inject, TargetType.ASSETS_GROUPS));
  }

  @Test
  void given_valid_input_initializeInjectStatus_SHOULD_save_the_injectstatus() {
    ExecutionStatus executionStatus = ExecutionStatus.EXECUTING;
    String injectId = "injectid";
    String injectStatusID = "injectStatusID";
    InjectStatus injectStatus = new InjectStatus();
    injectStatus.setId(injectStatusID);
    Inject inject = new Inject();
    inject.setId(injectId);
    inject.setStatus(injectStatus);
    injectStatus.setInject(inject);
    StatusPayload statusPayload = new StatusPayload();

    when(injectUtils.getStatusPayloadFromInject(inject)).thenReturn(statusPayload);
    when(injectRepository.findById(injectId)).thenReturn(Optional.of(inject));

    injectStatusService.initializeInjectStatus(injectId, executionStatus);

    ArgumentCaptor<InjectStatus> statusCaptor = ArgumentCaptor.forClass(InjectStatus.class);
    verify(injectStatusRepository).save(statusCaptor.capture());
    InjectStatus savedStatus = statusCaptor.getValue();
    assertNotNull(savedStatus);
    assertEquals(inject, savedStatus.getInject());
    assertEquals(executionStatus, savedStatus.getName());
    assertEquals(statusPayload, savedStatus.getPayloadOutput());
  }

  @Test
  void given_inject_without_injectcontent_SHOULD_take_default() throws JsonProcessingException {
    InjectInput injectInput = new InjectInput();
    Scenario scenario = new Scenario();
    String injectorContractId = "injectorContractId";
    String injectorContractString =
        """
              {
                "fields": [
                  {
                  "type": "defaultValue1",
                  "key": "value1",
                  "defaultValue": ["defaultValue1"],
                   "cardinality":"1"
                  },
                  {
                  "type": "asset",
                  "key": "value2",
                  "defaultValue": ["defaultValue2"],
                  "cardinality":"1"
                  }
                ]
              }
            """;
    InjectorContract injectorContract = new InjectorContract();
    injectorContract.setId(injectorContractId);
    injectorContract.setContent(injectorContractString);
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode injectorContractJson = (ObjectNode) mapper.readTree(injectorContractString);

    injectorContract.setConvertedContent(injectorContractJson);

    injectInput.setInjectorContract(injectorContractId);
    when(injectorContractService.injectorContract(injectorContractId)).thenReturn(injectorContract);

    injectService.createAndSaveInject(null, scenario, injectInput);

    ArgumentCaptor<Inject> injectCaptor = ArgumentCaptor.forClass(Inject.class);
    verify(injectRepository).save(injectCaptor.capture());
    Inject capturedInject = injectCaptor.getValue();

    assertEquals("defaultValue1", capturedInject.getContent().get("value1").asText());
    assertEquals("defaultValue2", capturedInject.getContent().get("value2").asText());
  }

  @Test
  @DisplayName(
      "createAndSaveInject should resolve injector from explicit injectorId instead of contract")
  void createAndSaveInject_withExplicitInjectorId_shouldResolveFromRepository() {
    // -- ARRANGE --
    String injectorId = "explicit-injector-id";
    String injectorContractId = "contract-id";

    Injector contractInjector = InjectorFixture.createDefaultPayloadInjector();
    contractInjector.setId("contract-injector-id");

    InjectorContract injectorContract = new InjectorContract();
    injectorContract.setId(injectorContractId);
    injectorContract.addInjector(contractInjector);
    ObjectNode contractContent = mapper.createObjectNode();
    contractContent.set("fields", mapper.createArrayNode());
    injectorContract.setConvertedContent(contractContent);

    Injector explicitInjector = InjectorFixture.createDefaultPayloadInjector();
    explicitInjector.setId(injectorId);

    InjectInput injectInput = new InjectInput();
    injectInput.setTitle("Test inject");
    injectInput.setInjectorContract(injectorContractId);
    injectInput.setInjectorId(injectorId);
    injectInput.setDependsDuration(0L);

    Scenario scenario = new Scenario();

    when(injectorContractService.injectorContract(injectorContractId)).thenReturn(injectorContract);
    when(injectUtils.resolveInjector(injectorId, injectorContract)).thenReturn(explicitInjector);

    // -- ACT --
    injectService.createAndSaveInject(null, scenario, injectInput);

    // -- ASSERT --
    ArgumentCaptor<Inject> injectCaptor = ArgumentCaptor.forClass(Inject.class);
    verify(injectRepository).save(injectCaptor.capture());
    Inject capturedInject = injectCaptor.getValue();

    assertNotNull(capturedInject.getInjector());
    assertEquals(injectorId, capturedInject.getInjector().getId());
    assertNotEquals(
        contractInjector.getId(),
        capturedInject.getInjector().getId(),
        "Injector should come from explicit ID, not from the contract");
    verify(injectUtils).resolveInjector(injectorId, injectorContract);
  }

  @Test
  @DisplayName("createAndSaveInject without injectorId should auto-resolve injector from contract")
  void createAndSaveInject_withoutInjectorId_shouldFallbackToContractInjector() {
    // -- ARRANGE --
    String injectorContractId = "contract-id";

    Injector contractInjector = InjectorFixture.createDefaultPayloadInjector();
    contractInjector.setId("contract-injector-id");

    InjectorContract injectorContract = new InjectorContract();
    injectorContract.setId(injectorContractId);
    injectorContract.addInjector(contractInjector);
    ObjectNode contractContent = mapper.createObjectNode();
    contractContent.set("fields", mapper.createArrayNode());
    injectorContract.setConvertedContent(contractContent);

    InjectInput injectInput = new InjectInput();
    injectInput.setTitle("Test inject");
    injectInput.setInjectorContract(injectorContractId);
    // injectorId is NOT set - auto-resolve from contract
    injectInput.setDependsDuration(0L);

    Scenario scenario = new Scenario();

    when(injectorContractService.injectorContract(injectorContractId)).thenReturn(injectorContract);
    when(injectUtils.resolveInjector(null, injectorContract)).thenReturn(contractInjector);

    // -- ACT --
    injectService.createAndSaveInject(null, scenario, injectInput);

    // -- ASSERT --
    ArgumentCaptor<Inject> injectCaptor = ArgumentCaptor.forClass(Inject.class);
    verify(injectRepository).save(injectCaptor.capture());
    Inject capturedInject = injectCaptor.getValue();

    assertNotNull(capturedInject.getInjector());
    assertEquals(contractInjector.getId(), capturedInject.getInjector().getId());
    verify(injectUtils).resolveInjector(null, injectorContract);
  }

  @Test
  @DisplayName("createAndSaveInject with unknown injectorId should throw ElementNotFoundException")
  void createAndSaveInject_withUnknownInjectorId_shouldThrow() {
    // -- ARRANGE --
    String unknownInjectorId = "unknown-injector-id";
    String injectorContractId = "contract-id";

    InjectorContract injectorContract = new InjectorContract();
    injectorContract.setId(injectorContractId);
    injectorContract.setInjectors(List.of());
    ObjectNode contractContent = mapper.createObjectNode();
    contractContent.set("fields", mapper.createArrayNode());
    injectorContract.setConvertedContent(contractContent);

    InjectInput injectInput = new InjectInput();
    injectInput.setTitle("Test inject");
    injectInput.setInjectorContract(injectorContractId);
    injectInput.setInjectorId(unknownInjectorId);
    injectInput.setDependsDuration(0L);

    Scenario scenario = new Scenario();

    when(injectorContractService.injectorContract(injectorContractId)).thenReturn(injectorContract);
    when(injectUtils.resolveInjector(unknownInjectorId, injectorContract))
        .thenThrow(
            new ElementNotFoundException("Injector not found with id: " + unknownInjectorId));

    // -- ACT & ASSERT --
    assertThrows(
        ElementNotFoundException.class,
        () -> injectService.createAndSaveInject(null, scenario, injectInput));
    verify(injectUtils).resolveInjector(unknownInjectorId, injectorContract);
    verify(injectRepository, never()).save(any());
  }

  @Test
  public void testRunChecksWhenInjectIsNull() {

    // RUN
    List<HealthCheck> healtchChecks = injectService.runChecks(null);

    // VERIFY
    assertNull(healtchChecks);
  }

  @Test
  public void testRunChecksForSmtpIssue() throws JsonProcessingException {
    // PREPARE
    Inject inject =
        InjectFixture.getInjectForEmailContract(
            InjectorContractFixture.createPayloadInjectorContractWithFieldsContent(
                InjectorFixture.createDefaultPayloadInjector(), null, List.of()));
    inject.setTenant(new Tenant("test-tenant-id"));

    inject.setInjector(inject.getInjectorContract().get().getFirstInjector());

    inject
        .getInjector()
        .setDependencies(new ExternalServiceDependency[] {ExternalServiceDependency.SMTP});

    // MOCK
    when(smtpService.isServiceAvailable()).thenReturn(false);
    when(collectorService.securityPlatformCollectors(any())).thenReturn(List.of());
    when(injectorService.findAll()).thenReturn(List.of());

    // RUN
    List<HealthCheck> healtchChecks = injectService.runChecks(inject);

    // VERIFY
    assertNotNull(healtchChecks);
    assertFalse(healtchChecks.isEmpty());

    HealthCheck healthCheckToVerify =
        healtchChecks.stream()
            .filter(hc -> HealthCheck.Type.SMTP.equals(hc.getType()))
            .findFirst()
            .orElse(new HealthCheck(null, null, null, now()));
    assertEquals(HealthCheck.Type.SMTP, healthCheckToVerify.getType());
    assertEquals(HealthCheck.Detail.SERVICE_UNAVAILABLE, healthCheckToVerify.getDetail());
    assertEquals(HealthCheck.Status.ERROR, healthCheckToVerify.getStatus());
  }

  @Test
  public void testRunChecksForImapIssue() throws JsonProcessingException {
    // PREPARE
    Inject inject =
        InjectFixture.getInjectForEmailContract(
            InjectorContractFixture.createPayloadInjectorContractWithFieldsContent(
                InjectorFixture.createDefaultPayloadInjector(), null, List.of()));
    inject.setTenant(new Tenant("test-tenant-id"));

    inject.setInjector(inject.getInjectorContract().get().getFirstInjector());

    inject
        .getInjector()
        .setDependencies(new ExternalServiceDependency[] {ExternalServiceDependency.IMAP});

    // MOCK
    when(imapService.isServiceAvailable()).thenReturn(false);
    when(collectorService.securityPlatformCollectors(any())).thenReturn(List.of());
    when(injectorService.findAll()).thenReturn(List.of());

    // RUN
    List<HealthCheck> healtchChecks = injectService.runChecks(inject);
    // VERIFY
    assertNotNull(healtchChecks);
    assertFalse(healtchChecks.isEmpty());

    HealthCheck healthCheckToVerify =
        healtchChecks.stream()
            .filter(hc -> HealthCheck.Type.IMAP.equals(hc.getType()))
            .findFirst()
            .orElse(new HealthCheck(null, null, null, now()));
    assertEquals(HealthCheck.Type.IMAP, healthCheckToVerify.getType());
    assertEquals(HealthCheck.Detail.SERVICE_UNAVAILABLE, healthCheckToVerify.getDetail());
    assertEquals(HealthCheck.Status.WARNING, healthCheckToVerify.getStatus());
  }

  @Test
  public void testRunChecksForExecutorIssue() throws JsonProcessingException {
    // PREPARE
    Inject inject =
        InjectFixture.getInjectForEmailContract(
            InjectorContractFixture.createPayloadInjectorContractWithFieldsContent(
                InjectorFixture.createDefaultPayloadInjector(), null, List.of()));
    inject.setTenant(new Tenant("test-tenant-id"));
    inject.getInjectorContract().get().setNeedsExecutor(true);

    // MOCK
    when(collectorService.securityPlatformCollectors(any())).thenReturn(List.of());
    when(injectorService.findAll()).thenReturn(List.of());

    // RUN
    List<HealthCheck> healtchChecks = injectService.runChecks(inject);

    // VERIFY
    assertNotNull(healtchChecks);
    assertFalse(healtchChecks.isEmpty());

    HealthCheck healthCheckToVerify =
        healtchChecks.stream()
            .filter(hc -> HealthCheck.Type.AGENT_OR_EXECUTOR.equals(hc.getType()))
            .findFirst()
            .orElse(new HealthCheck(null, null, null, now()));
    assertEquals(HealthCheck.Type.AGENT_OR_EXECUTOR, healthCheckToVerify.getType());
    assertEquals(HealthCheck.Detail.EMPTY, healthCheckToVerify.getDetail());
    assertEquals(HealthCheck.Status.ERROR, healthCheckToVerify.getStatus());
  }

  @Test
  public void testRunChecksForCollectorIssue() throws JsonProcessingException {
    // PREPARE
    Inject inject =
        InjectFixture.getInjectForEmailContract(
            InjectorContractFixture.createPayloadInjectorContractWithFieldsContent(
                InjectorFixture.createDefaultPayloadInjector(), null, List.of()));
    inject.setTenant(new Tenant("test-tenant-id"));

    ObjectNode expectationDetection = mapper.createObjectNode();
    expectationDetection.put(
        "expectation_type", BaseInjectExpectation.EXPECTATION_TYPE.DETECTION.toString());

    ObjectNode expectationPrevention = mapper.createObjectNode();
    expectationPrevention.put(
        "expectation_type", BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION.toString());

    ArrayNode expectationsArray = mapper.createArrayNode();
    expectationsArray.add(expectationDetection);
    expectationsArray.add(expectationPrevention);

    ObjectNode content = mapper.createObjectNode();
    content.set("expectations", expectationsArray);
    inject.setContent(content);

    // MOCK
    when(collectorService.securityPlatformCollectors(any())).thenReturn(List.of());
    when(injectorService.findAll()).thenReturn(List.of());

    // RUN
    List<HealthCheck> healtchChecks = injectService.runChecks(inject);

    // VERIFY
    assertNotNull(healtchChecks);
    assertFalse(healtchChecks.isEmpty());

    HealthCheck healthCheckToVerify =
        healtchChecks.stream()
            .filter(hc -> HealthCheck.Type.SECURITY_SYSTEM_COLLECTOR.equals(hc.getType()))
            .findFirst()
            .orElse(new HealthCheck(null, null, null, now()));
    assertEquals(HealthCheck.Type.SECURITY_SYSTEM_COLLECTOR, healthCheckToVerify.getType());
    assertEquals(HealthCheck.Detail.EMPTY, healthCheckToVerify.getDetail());
    assertEquals(HealthCheck.Status.ERROR, healthCheckToVerify.getStatus());
  }

  @Test
  public void given_injectorDependenciesOnNmap_when_nmapIsRegistered_then_noHealtchCheck()
      throws JsonProcessingException {

    // PREPARE
    Inject inject =
        InjectFixture.getInjectForEmailContract(
            InjectorContractFixture.createPayloadInjectorContractWithFieldsContent(
                InjectorFixture.createDefaultPayloadInjector(), null, List.of()));
    inject.setTenant(new Tenant("test-tenant-id"));
    inject
        .getInjectorContract()
        .get()
        .getFirstInjector()
        .setDependencies(new ExternalServiceDependency[] {ExternalServiceDependency.NMAP});

    // MOCK
    when(collectorService.securityPlatformCollectors(any())).thenReturn(List.of());
    Injector nmapInjector = new Injector();
    nmapInjector.setId("testNmap");
    nmapInjector.setType("openaev_nmap");
    when(injectorService.findAll()).thenReturn(List.of(nmapInjector));

    // RUN
    List<HealthCheck> healtchChecks = injectService.runChecks(inject);
    // VERIFY
    assertTrue(healtchChecks.isEmpty());
  }

  @Test
  public void given_injectorDependenciesOnNmap_when_nmapIsRegistered_then_healtchCheckCreated()
      throws JsonProcessingException {

    // PREPARE
    Inject inject =
        InjectFixture.getInjectForEmailContract(
            InjectorContractFixture.createPayloadInjectorContractWithFieldsContent(
                InjectorFixture.createDefaultPayloadInjector(), null, List.of()));
    inject.setTenant(new Tenant("test-tenant-id"));

    inject.setInjector(inject.getInjectorContract().get().getFirstInjector());

    inject
        .getInjector()
        .setDependencies(new ExternalServiceDependency[] {ExternalServiceDependency.NMAP});

    // MOCK
    when(collectorService.securityPlatformCollectors(any())).thenReturn(List.of());
    when(injectorService.findAll()).thenReturn(List.of());

    // RUN
    List<HealthCheck> healthChecks = injectService.runChecks(inject);

    // VERIFY
    HealthCheck healthCheckToVerify =
        healthChecks.stream()
            .filter(hc -> HealthCheck.Type.NMAP.equals(hc.getType()))
            .findFirst()
            .orElseThrow();
    assertEquals(HealthCheck.Type.NMAP, healthCheckToVerify.getType());
    assertEquals(HealthCheck.Detail.SERVICE_UNAVAILABLE, healthCheckToVerify.getDetail());
    assertEquals(HealthCheck.Status.ERROR, healthCheckToVerify.getStatus());
  }

  @Test
  public void given_injectorDependenciesOnNuclei_when_nucleiIsRegistered_then_healtchCheckCreated()
      throws JsonProcessingException {

    // PREPARE
    Inject inject =
        InjectFixture.getInjectForEmailContract(
            InjectorContractFixture.createPayloadInjectorContractWithFieldsContent(
                InjectorFixture.createDefaultPayloadInjector(), null, List.of()));
    inject.setTenant(new Tenant("test-tenant-id"));

    inject.setInjector(inject.getInjectorContract().get().getFirstInjector());

    inject
        .getInjector()
        .setDependencies(new ExternalServiceDependency[] {ExternalServiceDependency.NUCLEI});

    // MOCK
    when(collectorService.securityPlatformCollectors(any())).thenReturn(List.of());
    when(injectorService.findAll()).thenReturn(List.of());

    // RUN
    List<HealthCheck> healthChecks = injectService.runChecks(inject);

    // VERIFY
    HealthCheck healthCheckToVerify =
        healthChecks.stream()
            .filter(hc -> HealthCheck.Type.NUCLEI.equals(hc.getType()))
            .findFirst()
            .orElseThrow();
    assertEquals(HealthCheck.Type.NUCLEI, healthCheckToVerify.getType());
    assertEquals(HealthCheck.Detail.SERVICE_UNAVAILABLE, healthCheckToVerify.getDetail());
    assertEquals(HealthCheck.Status.ERROR, healthCheckToVerify.getStatus());
  }

  /* ============================================================
   * Find inject or return null
   * ============================================================ */
  @Nested
  @DisplayName("findInjectOrNull")
  class FindInjectOrNullTests {

    @Captor private ArgumentCaptor<String> injectIdCaptor;

    @Test
    @DisplayName("should return inject when found")
    void shouldReturnInjectWhenFound() {
      // Prepare
      String injectId = UUID.randomUUID().toString();
      Inject inject = mock(Inject.class);
      when(injectRepository.findById(injectId)).thenReturn(Optional.of(inject));

      // Act
      Inject result = injectService.findInjectOrNull(injectId);

      // Assert
      verify(injectRepository).findById(injectIdCaptor.capture());
      assertEquals(injectId, injectIdCaptor.getValue());
      assertNotNull(result);
      assertEquals(inject, result);
    }

    @Test
    @DisplayName("should return null when inject not found")
    void shouldReturnNullWhenNotFound() {
      // Prepare
      String injectId = UUID.randomUUID().toString();
      when(injectRepository.findById(injectId)).thenReturn(Optional.empty());

      // Act
      Inject result = injectService.findInjectOrNull(injectId);

      // Assert
      verify(injectRepository).findById(injectId);
      assertNull(result);
    }

    @Test
    @DisplayName("should return null when inject id is null")
    void shouldReturnNullWhenInjectIdIsNull() {
      // Act
      Inject result = injectService.findInjectOrNull(null);

      // Assert
      assertNull(result);
      verifyNoInteractions(injectRepository);
    }
  }

  /* ============================================================
   * Inject creation
   * ============================================================ */
  @Nested
  @DisplayName("createInject")
  class CreateInjectTests {

    @Captor private ArgumentCaptor<Inject> injectCaptor;

    @Test
    @DisplayName("should save and return inject")
    void shouldSaveAndReturnInject() {
      // Prepare
      Inject inject = mock(Inject.class);
      Inject savedInject = mock(Inject.class);
      when(injectRepository.save(inject)).thenReturn(savedInject);

      // Act
      Inject result = injectService.createInject(inject);

      // Assert
      verify(injectRepository).save(injectCaptor.capture());
      assertEquals(inject, injectCaptor.getValue());
      assertEquals(savedInject, result);
    }

    @Test
    @DisplayName("should pass inject to repository")
    void shouldPassInjectToRepository() {
      // Prepare
      Inject inject = mock(Inject.class);
      when(injectRepository.save(any(Inject.class))).thenAnswer(i -> i.getArgument(0));

      // Act
      Inject result = injectService.createInject(inject);

      // Assert
      verify(injectRepository).save(inject);
      assertEquals(inject, result);
    }
  }

  /* ============================================================
   * Team deletion for a simulation
   * ============================================================ */
  @Nested
  @DisplayName("removeTeamsForSimulation")
  class RemoveTeamsForSimulationTests {

    @Captor private ArgumentCaptor<String> simulationIdCaptor;

    @Captor private ArgumentCaptor<List<String>> teamIdsCaptor;

    private static Stream<Arguments> testCases() {
      return Stream.of(
          Arguments.of(
              "multiple team IDs",
              List.of(
                  UUID.randomUUID().toString(),
                  UUID.randomUUID().toString(),
                  UUID.randomUUID().toString())),
          Arguments.of("single team ID", List.of(UUID.randomUUID().toString())),
          Arguments.of("empty team IDs list", Collections.emptyList()));
    }

    @ParameterizedTest(name = "should remove teams for {0}")
    @MethodSource("testCases")
    void shouldRemoveTeams(String name, List<String> teamIds) {
      // Prepare
      String simulationId = UUID.randomUUID().toString();

      // Act
      injectService.removeTeamsForSimulation(simulationId, teamIds);

      // Assert
      verify(injectRepository)
          .removeTeamsForExercise(simulationIdCaptor.capture(), teamIdsCaptor.capture());
      assertEquals(simulationId, simulationIdCaptor.getValue());
      assertEquals(teamIds, teamIdsCaptor.getValue());
      verifyNoMoreInteractions(injectRepository);
    }
  }
}
