package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;
import static io.openaev.rest.payload.PayloadApi.PAYLOAD_URI;
import static io.openaev.service.threat_arsenal.ThreatArsenalImportService.EXPORT_FORMAT_TYPES;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.reflection.RelationUtils.getAllRelationsAsMap;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionCreateInput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Domain;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Group;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GrantRepository;
import io.openaev.database.repository.GroupRepository;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.jsonapi.JsonApiDocument;
import io.openaev.jsonapi.Relationship;
import io.openaev.jsonapi.ResourceIdentifier;
import io.openaev.jsonapi.ResourceObject;
import io.openaev.service.ZipJsonService;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.fixtures.DomainFixture;
import io.openaev.utils.fixtures.GrantFixture;
import io.openaev.utils.fixtures.ThreatArsenalInputFixture;
import io.openaev.utils.fixtures.composers.DomainComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pins the rule that a threat-arsenal import only rebuilds the threat-arsenal export format.
 *
 * <p>The export of an action is a JSON:API document whose root is an {@code injectors_contracts}
 * resource, with the entities it legitimately owns side-loaded in {@code included[]} (payload,
 * domains, attack patterns, ...). The rule: whatever else a file side-loads, a resource whose type
 * is outside that format (here {@code users} and {@code tokens}, which the export never emits for
 * an action) must not be reconstructed by the import.
 */
@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Threat arsenal import - export format scope")
class ThreatArsenalImportFormatScopeTest extends IntegrationTest {

  // Relationship keys walked from the root contract to the out-of-format resources.
  // contract --payload--> payload --grants--> grant --group--> group --users--> user --tokens-->
  // token
  private static final String REL_CONTRACT_PAYLOAD = "injector_contract_payload";
  private static final String REL_PAYLOAD_GRANTS = "grants";
  private static final String REL_GRANT_GROUP = "grant_group";
  private static final String REL_GROUP_USERS = "group_users";
  private static final String REL_USER_TOKENS = "tokens";

  @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
  @Autowired private MockMvc mockMvc;
  @Autowired private DomainComposer domainComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;
  @Autowired private ZipJsonService<?> zipJsonService;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private GroupRepository groupRepository;
  @Autowired private GrantRepository grantRepository;

  @BeforeEach
  void beforeEach() {
    builtinConnectorRegistration.register(
        openaevInjectorIntegrationFactory, TenantContext.getCurrentTenant());
    domainComposer.reset();
    injectorContractComposer.reset();
    if (testUserHolder.isSet()) {
      tenantRepository.addUserToTenant(testUserHolder.get().getId(), Tenant.DEFAULT_TENANT_UUID);
    }
  }

  // -- HELPERS --

  private String createAction() throws Exception {
    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    ThreatArsenalActionCreateInput createInput =
        ThreatArsenalInputFixture.createDefaultCommandLineAction(List.of(domain.getId()));
    String createResponse =
        mockMvc
            .perform(
                post(tenantUri(THREAT_ARSENAL_URL))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(createInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return createResponse;
  }

  private byte[] createAndExportContract() throws Exception {
    String createResponse = createAction();
    String contractId = JsonPath.read(createResponse, "$.injector_contract_id");
    return mockMvc
        .perform(
            get(tenantUri(TENANT_THREAT_ARSENAL_URL + "/" + contractId + "/export")).with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsByteArray();
  }

  private JsonApiDocument<ResourceObject> readRootDocument(byte[] zip) throws Exception {
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.getName().endsWith(".json") && !"meta.json".equals(entry.getName())) {
          return objectMapper.readValue(
              zis.readAllBytes(),
              objectMapper
                  .getTypeFactory()
                  .constructParametricType(JsonApiDocument.class, ResourceObject.class));
        }
        zis.closeEntry();
      }
    }
    throw new IllegalStateException("no json entry in export");
  }

  private Map<String, byte[]> readExtras(byte[] zip) throws Exception {
    Map<String, byte[]> extras = new HashMap<>();
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (!entry.getName().endsWith(".json")) {
          extras.put(entry.getName(), zis.readAllBytes());
        }
        zis.closeEntry();
      }
    }
    return extras;
  }

  private ResourceObject asResource(Object o) {
    return objectMapper.convertValue(o, ResourceObject.class);
  }

  private static Map<String, Relationship> withRelationship(
      Map<String, Relationship> existing, String key, Relationship relationship) {
    Map<String, Relationship> copy =
        existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
    copy.put(key, relationship);
    return copy;
  }

  private static Relationship toMany(String type, String id) {
    return new Relationship(List.of(new ResourceIdentifier(id, type)));
  }

  private static Relationship toOne(String type, String id) {
    return new Relationship(new ResourceIdentifier(id, type));
  }

  /** The grants / groups / users / tokens resources linked by {@code grantId} down to the token. */
  private List<ResourceObject> sideLoadedChain(
      String grantId, String email, String tokenValue, String groupName) {
    String groupId = UUID.randomUUID().toString();
    String userId = UUID.randomUUID().toString();
    String tokenId = UUID.randomUUID().toString();

    Map<String, Object> grantAttributes = new HashMap<>();
    grantAttributes.put("grant_name", "OBSERVER");
    grantAttributes.put("grant_resource_type", "THREAT_ARSENAL");
    Map<String, Object> groupAttributes = new HashMap<>();
    groupAttributes.put("group_name", groupName);
    Map<String, Object> userAttributes = new HashMap<>();
    userAttributes.put("user_email", email);
    userAttributes.put("user_admin", true);
    Map<String, Object> tokenAttributes = new HashMap<>();
    tokenAttributes.put("token_value", tokenValue);
    tokenAttributes.put("token_created_at", Instant.now().toString());

    return List.of(
        new ResourceObject(
            grantId, "grants", grantAttributes, Map.of(REL_GRANT_GROUP, toOne("groups", groupId))),
        new ResourceObject(
            groupId, "groups", groupAttributes, Map.of(REL_GROUP_USERS, toMany("users", userId))),
        new ResourceObject(
            userId, "users", userAttributes, Map.of(REL_USER_TOKENS, toMany("tokens", tokenId))),
        new ResourceObject(tokenId, "tokens", tokenAttributes, null));
  }

  /**
   * Same contract export, plus a users resource and a tokens resource side-loaded in included[] and
   * linked from the contract through the payload / grants / group / users / tokens relationships.
   */
  private JsonApiDocument<ResourceObject> withSideLoadedUserAndToken(
      JsonApiDocument<ResourceObject> original, String email, String tokenValue, String groupName) {
    ResourceObject root = original.data();
    String payloadId =
        ((ResourceIdentifier) root.relationships().get(REL_CONTRACT_PAYLOAD).asOne()).id();
    String grantId = UUID.randomUUID().toString();

    List<Object> included = new ArrayList<>();
    for (Object o : original.included() == null ? List.of() : original.included()) {
      ResourceObject ro = asResource(o);
      if (ro.id().equals(payloadId)) {
        ro =
            new ResourceObject(
                ro.id(),
                ro.type(),
                ro.attributes(),
                withRelationship(
                    ro.relationships(), REL_PAYLOAD_GRANTS, toMany("grants", grantId)));
      }
      included.add(ro);
    }
    included.addAll(sideLoadedChain(grantId, email, tokenValue, groupName));
    return new JsonApiDocument<>(root, included);
  }

  /** A legacy payload export (the payload is the root) with the same chain side-loaded. */
  private JsonApiDocument<ResourceObject> withSideLoadedUserAndTokenOnPayloadRoot(
      JsonApiDocument<ResourceObject> original, String email, String tokenValue, String groupName) {
    ResourceObject root = original.data();
    String grantId = UUID.randomUUID().toString();
    ResourceObject newRoot =
        new ResourceObject(
            root.id(),
            root.type(),
            root.attributes(),
            withRelationship(root.relationships(), REL_PAYLOAD_GRANTS, toMany("grants", grantId)));
    List<Object> included = new ArrayList<>();
    if (original.included() != null) {
      included.addAll(original.included());
    }
    included.addAll(sideLoadedChain(grantId, email, tokenValue, groupName));
    return new JsonApiDocument<>(newRoot, included);
  }

  private String importZip(byte[] zip) throws Exception {
    return importZipExpecting(zip, status().is2xxSuccessful());
  }

  private String importZipExpecting(byte[] zip, ResultMatcher expected) throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "threat-arsenal.zip", "application/zip", zip);
    return mockMvc
        .perform(
            multipart(tenantUri(TENANT_THREAT_ARSENAL_URL + "/import")).file(file).with(csrf()))
        .andExpect(expected)
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /** The import of a bundle with the given type set ({@code null}: no restriction). */
  @SuppressWarnings("unchecked")
  private InjectorContract importContractDirect(byte[] zip, Set<String> allowedTypes)
      throws Exception {
    ZipJsonService<InjectorContract> unrestricted =
        (ZipJsonService<InjectorContract>) (ZipJsonService<?>) zipJsonService;
    return unrestricted
        .handleImport(
            zip,
            "injector_contract_labels",
            null,
            contract -> {
              contract.setId(UUID.randomUUID().toString());
              contract.setTenant(new Tenant(TenantContext.getCurrentTenant()));
              return contract;
            },
            " (Import)",
            TenantContext.getCurrentTenant(),
            allowedTypes)
        .persistedData();
  }

  private byte[] exportPayload(String payloadId) throws Exception {
    return mockMvc
        .perform(get(PAYLOAD_URI + "/" + payloadId + "/export").with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsByteArray();
  }

  private long countUsersByEmail(String email) {
    entityManager.flush();
    entityManager.clear();
    return ((Number)
            entityManager
                .createNativeQuery("select count(*) from users where user_email = :email")
                .setParameter("email", email)
                .getSingleResult())
        .longValue();
  }

  private long countTokensByValue(String value) {
    entityManager.flush();
    entityManager.clear();
    return ((Number)
            entityManager
                .createNativeQuery("select count(*) from tokens where token_value = :value")
                .setParameter("value", value)
                .getSingleResult())
        .longValue();
  }

  private long countGroupsByName(String name) {
    entityManager.flush();
    entityManager.clear();
    return ((Number)
            entityManager
                .createNativeQuery("select count(*) from groups where group_name = :name")
                .setParameter("name", name)
                .getSingleResult())
        .longValue();
  }

  private long countGrants() {
    entityManager.flush();
    entityManager.clear();
    return ((Number)
            entityManager.createNativeQuery("select count(*) from grants").getSingleResult())
        .longValue();
  }

  private long countPayloadsByNameSuffixImport() {
    entityManager.flush();
    entityManager.clear();
    return ((Number)
            entityManager
                .createNativeQuery(
                    "select count(*) from payloads where payload_name like '%(Import)'")
                .getSingleResult())
        .longValue();
  }

  private long countContractsById(String id) {
    entityManager.flush();
    entityManager.clear();
    return ((Number)
            entityManager
                .createNativeQuery(
                    "select count(*) from injectors_contracts where injector_contract_id = :id")
                .setParameter("id", id)
                .getSingleResult())
        .longValue();
  }

  private long countContractsByLabelSuffixImport() {
    entityManager.flush();
    entityManager.clear();
    return ((Number)
            entityManager
                .createNativeQuery(
                    "select count(*) from injectors_contracts where injector_contract_labels::text like '%(Import)%'")
                .getSingleResult())
        .longValue();
  }

  // -- TESTS --

  @Test
  @DisplayName(
      "Import of a contract export with users and tokens side-loaded in included[] must not create them")
  void given_contractExportWithSideLoadedUserAndToken_should_notCreateEntitiesOutsideFormat()
      throws Exception {
    // Arrange
    byte[] exported = createAndExportContract();
    JsonApiDocument<ResourceObject> original = readRootDocument(exported);
    String email = "scope-test-" + UUID.randomUUID() + "@example.invalid";
    String tokenValue = "scope-test-token-" + UUID.randomUUID();
    String groupName = "scope-test-group-" + UUID.randomUUID();
    byte[] augmentedZip =
        zipJsonService.writeZip(
            withSideLoadedUserAndToken(original, email, tokenValue, groupName),
            readExtras(exported));
    assertEquals(0, countUsersByEmail(email));
    assertEquals(0, countTokensByValue(tokenValue));
    assertEquals(0, countGroupsByName(groupName));
    long grantsBefore = countGrants();
    long contractsBefore = countContractsByLabelSuffixImport();
    long payloadsBefore = countPayloadsByNameSuffixImport();

    // Act
    String importResponse = importZip(augmentedZip);

    // Assert: the contract itself is imported...
    assertNotNull(JsonPath.read(importResponse, "$.injector_contract_id"));
    // ...selectively: the contract and its payload are there, so the drop is not a wholesale
    // failure...
    assertTrue(countContractsByLabelSuffixImport() > contractsBefore);
    assertTrue(countPayloadsByNameSuffixImport() > payloadsBefore);
    // ...but nothing outside the export format is rebuilt from the file.
    assertAll(
        () ->
            assertEquals(
                0,
                countUsersByEmail(email),
                "a users resource side-loaded in included[] must not be reconstructed by the import"),
        () ->
            assertEquals(
                0,
                countTokensByValue(tokenValue),
                "a tokens resource side-loaded in included[] must not be reconstructed by the"
                    + " import"),
        () ->
            assertEquals(
                0,
                countGroupsByName(groupName),
                "a groups resource side-loaded in included[] must not be reconstructed by the"
                    + " import"),
        () ->
            assertEquals(
                grantsBefore,
                countGrants(),
                "a grants resource side-loaded in included[] must not be reconstructed by the"
                    + " import"));
  }

  @Test
  @DisplayName("Negative control: the same export without users/tokens creates the contract only")
  void given_contractExportWithoutSideLoadedEntities_should_createContractAndNoUserNorToken()
      throws Exception {
    // Arrange
    byte[] exported = createAndExportContract();
    JsonApiDocument<ResourceObject> original = readRootDocument(exported);
    String email = "scope-test-" + UUID.randomUUID() + "@example.invalid";
    String tokenValue = "scope-test-token-" + UUID.randomUUID();
    long contractsBefore = countContractsByLabelSuffixImport();
    // Same document shape as the test above, re-serialised, with no users/tokens added.
    byte[] controlZip =
        zipJsonService.writeZip(
            new JsonApiDocument<>(original.data(), original.included()), readExtras(exported));

    // Act
    String importResponse = importZip(controlZip);

    // Assert
    assertNotNull(JsonPath.read(importResponse, "$.injector_contract_id"));
    assertTrue(countContractsByLabelSuffixImport() > contractsBefore);
    assertEquals(0, countUsersByEmail(email));
    assertEquals(0, countTokensByValue(tokenValue));
  }

  @Test
  @DisplayName(
      "The same bundle imported with no type set at all still creates no identity or authorization row")
  void given_sameBundleWithoutTypeSet_should_createNoIdentityRow() throws Exception {
    // Arrange
    byte[] exported = createAndExportContract();
    String email = "scope-test-" + UUID.randomUUID() + "@example.invalid";
    String tokenValue = "scope-test-token-" + UUID.randomUUID();
    String groupName = "scope-test-group-" + UUID.randomUUID();
    byte[] augmentedZip =
        zipJsonService.writeZip(
            withSideLoadedUserAndToken(readRootDocument(exported), email, tokenValue, groupName),
            readExtras(exported));
    long grantsBefore = countGrants();

    // Act: no per-caller type set (null), as the other importers call it
    String importedId = importContractDirect(augmentedZip, null).getId();

    // Assert: before the central denylist this import created the user, token, group and grant
    assertAll(
        () -> assertEquals(0, countUsersByEmail(email)),
        () -> assertEquals(0, countTokensByValue(tokenValue)),
        () -> assertEquals(0, countGroupsByName(groupName)),
        () -> assertEquals(grantsBefore, countGrants()),
        () -> assertEquals(1, countContractsById(importedId)));
  }

  @Test
  @DisplayName("The relationship keys the side-loaded chain uses exist on the model")
  void given_sideLoadedChain_should_useRealRelationshipKeys() {
    // Guards the tests above against passing vacuously after a rename of a relationship key
    assertTrue(getAllRelationsAsMap(Payload.class).containsKey(REL_PAYLOAD_GRANTS));
    assertTrue(getAllRelationsAsMap(Grant.class).containsKey(REL_GRANT_GROUP));
    assertTrue(getAllRelationsAsMap(Group.class).containsKey(REL_GROUP_USERS));
    assertTrue(getAllRelationsAsMap(User.class).containsKey(REL_USER_TOKENS));
  }

  @Test
  @DisplayName("A users root is refused even with no type set")
  void given_usersRootWithoutTypeSet_should_beRefused() throws Exception {
    // Arrange
    byte[] exported = createAndExportContract();
    JsonApiDocument<ResourceObject> original = readRootDocument(exported);
    ResourceObject root = original.data();
    byte[] zip =
        zipJsonService.writeZip(
            new JsonApiDocument<>(
                new ResourceObject(
                    root.id(),
                    "users",
                    Map.of("user_email", "scope-test-" + UUID.randomUUID() + "@example.invalid"),
                    null),
                original.included()),
            readExtras(exported));

    // Act / Assert
    assertThrows(IllegalArgumentException.class, () -> importContractDirect(zip, null));
  }

  @Test
  @DisplayName("A reference by id to an identity or authorization row is never bound")
  void given_referenceByIdToExistingGrant_should_notBindIt() throws Exception {
    // Arrange: an existing grant, referenced from the payload by id only
    Group group = new Group();
    group.setName("scope-test-existing-group-" + UUID.randomUUID());
    group.setTenant(new Tenant(TenantContext.getCurrentTenant()));
    group = groupRepository.save(group);
    Grant existing =
        grantRepository.save(
            GrantFixture.getGrant(
                UUID.randomUUID().toString(),
                Grant.GRANT_RESOURCE_TYPE.THREAT_ARSENAL,
                Grant.GRANT_TYPE.OBSERVER,
                group));
    byte[] zip = withPayloadRelationship(toMany("grants", existing.getId()));
    long grantsBefore = countGrants();

    // Act
    InjectorContract restricted = importContractDirect(zip, EXPORT_FORMAT_TYPES);
    InjectorContract unrestricted = importContractDirect(zip, null);

    // Assert: with or without a type set
    assertEquals(0, restricted.getPayload().getGrants().size());
    assertEquals(0, unrestricted.getPayload().getGrants().size());
    assertEquals(grantsBefore, countGrants());
  }

  @Test
  @DisplayName("A reference by id to a type outside the format is skipped when a type set is given")
  void given_referenceByIdToTypeOutsideFormat_should_beSkippedOnlyWithTypeSet() throws Exception {
    // Arrange: a reference of a type that is neither denied nor part of the format
    byte[] zip =
        withPayloadRelationship(
            new Relationship(
                List.of(new ResourceIdentifier(UUID.randomUUID().toString(), "exercises"))));

    // Act / Assert: skipped with the type set...
    InjectorContract restricted = importContractDirect(zip, EXPORT_FORMAT_TYPES);
    assertEquals(0, restricted.getPayload().getGrants().size());
    // ...while the same reference is resolved, and refused as unknown, without it
    assertThrows(Exception.class, () -> importContractDirect(zip, null));
  }

  /** A contract export whose payload carries the given {@code grants} relationship. */
  private byte[] withPayloadRelationship(Relationship grants) throws Exception {
    byte[] exported = createAndExportContract();
    JsonApiDocument<ResourceObject> original = readRootDocument(exported);
    String payloadId =
        ((ResourceIdentifier) original.data().relationships().get(REL_CONTRACT_PAYLOAD).asOne())
            .id();
    List<Object> included = new ArrayList<>();
    for (Object o : original.included()) {
      ResourceObject ro = asResource(o);
      if (ro.id().equals(payloadId)) {
        ro =
            new ResourceObject(
                ro.id(),
                ro.type(),
                ro.attributes(),
                withRelationship(ro.relationships(), REL_PAYLOAD_GRANTS, grants));
      }
      included.add(ro);
    }
    return zipJsonService.writeZip(
        new JsonApiDocument<>(original.data(), included), readExtras(exported));
  }

  @Test
  @DisplayName("An included resource and a reference with no type are skipped, the import succeeds")
  void given_includedAndReferenceWithoutType_should_skipThemAndImportContract() throws Exception {
    // Arrange
    byte[] exported = createAndExportContract();
    JsonApiDocument<ResourceObject> original = readRootDocument(exported);
    String payloadId =
        ((ResourceIdentifier) original.data().relationships().get(REL_CONTRACT_PAYLOAD).asOne())
            .id();
    String untypedIncludedId = UUID.randomUUID().toString();
    String untypedReferenceId = UUID.randomUUID().toString();
    List<Object> included = new ArrayList<>();
    for (Object o : original.included()) {
      ResourceObject ro = asResource(o);
      if (ro.id().equals(payloadId)) {
        ro =
            new ResourceObject(
                ro.id(),
                ro.type(),
                ro.attributes(),
                withRelationship(
                    ro.relationships(),
                    REL_PAYLOAD_GRANTS,
                    new Relationship(
                        List.of(
                            new ResourceIdentifier(untypedIncludedId, "grants"),
                            new ResourceIdentifier(untypedReferenceId, null)))));
      }
      included.add(ro);
    }
    included.add(new ResourceObject(untypedIncludedId, null, Map.of("grant_name", "x"), null));
    byte[] zip =
        zipJsonService.writeZip(
            new JsonApiDocument<>(original.data(), included), readExtras(exported));
    long contractsBefore = countContractsByLabelSuffixImport();

    // Act
    String importResponse = importZip(zip);

    // Assert
    assertNotNull(JsonPath.read(importResponse, "$.injector_contract_id"));
    assertTrue(countContractsByLabelSuffixImport() > contractsBefore);
  }

  @Test
  @DisplayName("Legacy payload export with users and tokens side-loaded: payload imported only")
  void given_payloadExportWithSideLoadedUserAndToken_should_importPayloadOnly() throws Exception {
    // Arrange
    String createResponse = createAction();
    byte[] exported = exportPayload(JsonPath.read(createResponse, "$.action_payload.payload_id"));
    String email = "scope-test-" + UUID.randomUUID() + "@example.invalid";
    String tokenValue = "scope-test-token-" + UUID.randomUUID();
    String groupName = "scope-test-group-" + UUID.randomUUID();
    byte[] augmentedZip =
        zipJsonService.writeZip(
            withSideLoadedUserAndTokenOnPayloadRoot(
                readRootDocument(exported), email, tokenValue, groupName),
            readExtras(exported));
    long grantsBefore = countGrants();
    long payloadsBefore = countPayloadsByNameSuffixImport();

    // Act
    String importResponse = importZip(augmentedZip);

    // Assert
    assertNotNull(JsonPath.read(importResponse, "$.injector_contract_id"));
    assertTrue(countPayloadsByNameSuffixImport() > payloadsBefore);
    assertAll(
        () -> assertEquals(0, countUsersByEmail(email)),
        () -> assertEquals(0, countTokensByValue(tokenValue)),
        () -> assertEquals(0, countGroupsByName(groupName)),
        () -> assertEquals(grantsBefore, countGrants()));
  }

  @ParameterizedTest(name = "root type {0} is refused with a 400")
  @ValueSource(strings = {"users", "tokens", "groups", "grants", "tags", "documents"})
  @DisplayName("A root type outside the root types of the format is refused")
  void given_rootTypeOutsideRootTypes_should_beRefused(String rootType) throws Exception {
    // Arrange
    byte[] exported = createAndExportContract();
    JsonApiDocument<ResourceObject> original = readRootDocument(exported);
    ResourceObject root = original.data();
    byte[] zip =
        zipJsonService.writeZip(
            new JsonApiDocument<>(
                new ResourceObject(root.id(), rootType, root.attributes(), root.relationships()),
                original.included()),
            readExtras(exported));

    // Act / Assert
    importZipExpecting(zip, status().isBadRequest());
  }

  @Test
  @DisplayName("A root with no type is refused with a 400, not a server error")
  void given_rootWithoutType_should_beRefused() throws Exception {
    // Arrange
    byte[] exported = createAndExportContract();
    JsonApiDocument<ResourceObject> original = readRootDocument(exported);
    ResourceObject root = original.data();
    byte[] zip =
        zipJsonService.writeZip(
            new JsonApiDocument<>(
                new ResourceObject(root.id(), null, root.attributes(), root.relationships()),
                original.included()),
            readExtras(exported));

    // Act / Assert: through the API, and through the importer with a type set
    importZipExpecting(zip, status().isBadRequest());
    assertThrows(
        IllegalArgumentException.class, () -> importContractDirect(zip, EXPORT_FORMAT_TYPES));
  }
}
