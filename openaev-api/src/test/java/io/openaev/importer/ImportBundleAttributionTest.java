package io.openaev.importer;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;
import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tenant;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.jsonapi.JsonApiDocument;
import io.openaev.jsonapi.Relationship;
import io.openaev.jsonapi.ResourceIdentifier;
import io.openaev.jsonapi.ResourceObject;
import io.openaev.rest.exercise.exports.ExportOptions;
import io.openaev.rest.exercise.service.ExportService;
import io.openaev.service.FileService;
import io.openaev.service.MinioService;
import io.openaev.service.ZipJsonService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.constants.Constants;
import io.openaev.utils.fixtures.DocumentFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.FileFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.OrganizationFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.DocumentComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.OrganizationComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.fixtures.composers.TeamComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * One import request attributes every row it creates to one tenant: the request's write tenant.
 *
 * <p>On the non-prefixed route the write tenant comes from {@code X-Tenant-Ids} while the ambient
 * {@link TenantContext} stays on the default tenant. {@code documents} is attributed explicitly,
 * but the roots that are not activated yet (exercise, inject, payload) are stamped from the ambient
 * tenant, so without alignment a single import lands its document in the header tenant and its
 * parent in the default one. The ground truth is read through raw JDBC on the test connection,
 * which the statement inspector never rewrites: the rows held by any tenant other than the write
 * tenant must not change across the import, whatever the table.
 *
 * <p>{@code domains} is armed next to {@code documents} because the import resolves the preset
 * domain by name and relies on the scope to get a single row, as it does in production.
 */
@TestInstance(PER_CLASS)
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=documents,domains")
@WithMockUser(isAdmin = true)
@DisplayName("An import attributes the whole bundle to the request's write tenant")
class ImportBundleAttributionTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ExportService exportService;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private DocumentComposer documentComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private TeamComposer teamComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private OrganizationComposer organizationComposer;
  @Autowired private ZipJsonService<Payload> zipJsonService;
  @Autowired private FileService fileService;
  @Autowired private MinioService minioService;

  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private String tenantB;

  @BeforeEach
  void seedWriteTenant() throws Exception {
    exerciseComposer.reset();
    documentComposer.reset();
    injectComposer.reset();
    teamComposer.reset();
    userComposer.reset();
    organizationComposer.reset();
    injectorContractComposer.reset();
    payloadComposer.reset();
    Mockito.when(enterpriseEditionService.isEnterpriseLicenseInactive(Mockito.any()))
        .thenReturn(false);
    // The caller belongs to the default tenant (where the source bundle is composed) and to B (the
    // import's write tenant), so the header selects B while the ambient tenant stays the default.
    tenantRepository.addUserToTenant(testUserHolder.get().getId(), Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantHelper.createTenantWithCurrentUser("import-bundle-b").getId();
  }

  @AfterEach
  void cleanStoredObjects() throws Exception {
    String target = FileFixture.getPlainTextFileContent().getFileName();
    fileService.deleteFile(target);
    try {
      minioService.deleteFileForTenant(tenantB, target);
    } catch (Exception e) {
      // best-effort cleanup: a missing object must not fail teardown
    }
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Exercise zip import on the header route")
  class ExerciseZipImport {

    @Test
    @DisplayName(
        "given_headerTenantDiffersFromAmbient_should_attributeExerciseDocumentAndPayloadToHeaderTenant")
    void
        given_headerTenantDiffersFromAmbient_should_attributeExerciseDocumentAndPayloadToHeaderTenant()
            throws Exception {
      // Arrange
      ExerciseComposer.Composer source = composeExerciseWithDocumentAndPayload();
      Exercise exercise = source.persist().get();
      Payload sourcePayload =
          exercise.getInjects().getFirst().getInjectorContract().orElseThrow().getPayload();
      byte[] zip =
          exportService.exportExerciseToZip(
              exercise, ExportOptions.mask(false, false, false), true);
      entityManager.flush();
      entityManager.clear();
      Map<String, Long> before = rowsOutsideTenant(tenantB);

      // Act
      mvc.perform(
              multipart(EXERCISE_URI + "/import")
                  .file(new MockMultipartFile("file", zip))
                  .header(TENANT_HEADER, tenantB)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // Assert
      String importedName = exercise.getName() + Constants.IMPORTED_OBJECT_NAME_SUFFIX;
      assertThat(tenantsOf("SELECT tenant_id FROM exercises WHERE exercise_name = ?", importedName))
          .as("the imported exercise must belong to the header tenant")
          .containsExactly(tenantB);
      assertThat(
              tenantsOf(
                  "SELECT d.tenant_id FROM documents d JOIN exercises_documents ed ON"
                      + " ed.document_id = d.document_id JOIN exercises e ON e.exercise_id ="
                      + " ed.exercise_id WHERE e.exercise_name = ?",
                  importedName))
          .as("the imported exercise's document must belong to the header tenant")
          .containsExactly(tenantB);
      assertThat(
              tenantsOf(
                  "SELECT tenant_id FROM payloads WHERE payload_name = ? AND payload_id <> ?",
                  sourcePayload.getName(),
                  sourcePayload.getId()))
          .as("the payload created by the bundle must belong to the header tenant")
          .containsExactly(tenantB);
      assertThat(rowsOutsideTenant(tenantB))
          .as("the import must not create a row in any tenant other than the header tenant")
          .isEqualTo(before);
    }

    @Test
    @DisplayName(
        "given_sameNamedOrganizationInAmbientTenant_should_createOrganizationInHeaderTenant")
    void given_sameNamedOrganizationInAmbientTenant_should_createOrganizationInHeaderTenant()
        throws Exception {
      // Arrange: the source exercise stays in the default tenant, so an organization of the same
      // name exists there when the bundle is imported into B. The import looks organizations up by
      // name through the ambient tenant filter only.
      Organization organization = OrganizationFixture.createDefaultOrganisation();
      Exercise exercise =
          exerciseComposer
              .forExercise(ExerciseFixture.createDefaultExercise())
              .withTeam(
                  teamComposer
                      .forTeam(TeamFixture.getDefaultTeam())
                      .withUser(
                          userComposer
                              .forUser(UserFixture.getUserWithDefaultEmail())
                              .withOrganization(
                                  organizationComposer.forOrganization(organization))))
              .withTeamUsers()
              .persist()
              .get();
      byte[] zip =
          exportService.exportExerciseToZip(exercise, ExportOptions.mask(true, true, false), true);
      entityManager.flush();
      entityManager.clear();
      Map<String, Long> before = rowsOutsideTenant(tenantB);

      // Act
      mvc.perform(
              multipart(EXERCISE_URI + "/import")
                  .file(new MockMultipartFile("file", zip))
                  .header(TENANT_HEADER, tenantB)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // Assert
      assertThat(
              tenantsOf(
                  "SELECT tenant_id FROM organizations WHERE organization_name = ?",
                  organization.getName()))
          .as("B gets its own organization instead of reusing the default tenant's one")
          .containsExactlyInAnyOrder(Tenant.DEFAULT_TENANT_UUID, tenantB);
      assertThat(rowsOutsideTenant(tenantB))
          .as("the import must not create a row in any tenant other than the header tenant")
          .isEqualTo(before);
    }

    private ExerciseComposer.Composer composeExerciseWithDocumentAndPayload() {
      return exerciseComposer
          .forExercise(ExerciseFixture.createDefaultExercise())
          .withDocument(
              documentComposer
                  .forDocument(DocumentFixture.getDocument(FileFixture.getPlainTextFileContent()))
                  .withInMemoryFile(FileFixture.getPlainTextFileContent()))
          .withInject(
              injectComposer
                  .forInject(InjectFixture.getDefaultInject())
                  .withInjectorContract(
                      injectorContractComposer
                          .forInjectorContract(
                              InjectorContractFixture.createDefaultInjectorContract())
                          .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
                          .withPayload(
                              payloadComposer.forPayload(PayloadFixture.createDefaultCommand()))));
    }
  }

  @Nested
  @DisplayName("JSON:API payload import on the header route")
  class JsonApiPayloadImport {

    @Test
    @DisplayName(
        "given_headerTenantDiffersFromAmbient_should_attributePayloadDomainAndAttackPatternToHeaderTenant")
    void
        given_headerTenantDiffersFromAmbient_should_attributePayloadDomainAndAttackPatternToHeaderTenant()
            throws Exception {
      // Arrange: a payload export carrying a domain and an attack pattern, both created by the
      // import next to the payload and its injector contract.
      String payloadName = "import-bundle-payload-" + UUID.randomUUID();
      String domainName = "import-bundle-domain-" + UUID.randomUUID();
      String attackPatternExternalId = "T-" + UUID.randomUUID();
      String domainId = UUID.randomUUID().toString();
      String attackPatternId = UUID.randomUUID().toString();
      ResourceObject domain =
          new ResourceObject(
              domainId,
              "domains",
              Map.of("domain_name", domainName, "domain_color", "#000000"),
              emptyMap());
      ResourceObject attackPattern =
          new ResourceObject(
              attackPatternId,
              "attack_patterns",
              Map.of(
                  "attack_pattern_name",
                  "import-bundle-attack-pattern",
                  "attack_pattern_description",
                  "",
                  "attack_pattern_stix_id",
                  "attack-pattern--" + UUID.randomUUID(),
                  "attack_pattern_external_id",
                  attackPatternExternalId,
                  "attack_pattern_platforms",
                  List.of("Windows"),
                  "attack_pattern_permissions_required",
                  List.of()),
              emptyMap());
      JsonApiDocument<ResourceObject> document =
          new JsonApiDocument<>(
              new ResourceObject(
                  null,
                  "command",
                  commandAttributes(payloadName),
                  Map.of(
                      "payload_domains",
                      new Relationship(List.of(new ResourceIdentifier(domainId, "domains"))),
                      "payload_attack_patterns",
                      new Relationship(
                          List.of(new ResourceIdentifier(attackPatternId, "attack_patterns"))))),
              List.of(domain, attackPattern));
      byte[] zip = zipJsonService.writeZip(document, emptyMap());
      Map<String, Long> before = rowsOutsideTenant(tenantB);

      // Act
      mvc.perform(
              multipart(THREAT_ARSENAL_URL + "/import")
                  .file(new MockMultipartFile("file", "payload.zip", "application/zip", zip))
                  .header(TENANT_HEADER, tenantB)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // Assert
      assertThat(
              tenantsOf(
                  "SELECT tenant_id FROM payloads WHERE payload_name = ?",
                  payloadName + Constants.IMPORTED_OBJECT_NAME_SUFFIX))
          .as("the imported payload must belong to the header tenant")
          .containsExactly(tenantB);
      assertThat(tenantsOf("SELECT tenant_id FROM domains WHERE domain_name = ?", domainName))
          .as("the domain created by the bundle must belong to the header tenant")
          .containsExactly(tenantB);
      assertThat(
              tenantsOf(
                  "SELECT tenant_id FROM attack_patterns WHERE attack_pattern_external_id = ?",
                  attackPatternExternalId))
          .as("the attack pattern created by the bundle must belong to the header tenant")
          .containsExactly(tenantB);
      assertThat(rowsOutsideTenant(tenantB))
          .as("the import must not create a row in any tenant other than the header tenant")
          .isEqualTo(before);
    }

    private Map<String, Object> commandAttributes(String payloadName) {
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("payload_type", "Command");
      attributes.put("command_executor", "psh");
      attributes.put("command_content", "echo \"bundle\"");
      attributes.put("payload_name", payloadName);
      attributes.put("payload_description", "");
      attributes.put("payload_platforms", new String[] {"Windows"});
      attributes.put("payload_source", "MANUAL");
      attributes.put("payload_expectations", new String[] {"VULNERABILITY"});
      attributes.put("payload_status", "VERIFIED");
      attributes.put("payload_execution_arch", "ALL_ARCHITECTURES");
      return attributes;
    }
  }

  /**
   * Row count per tenant-scoped table, restricted to the rows that do NOT belong to {@code
   * tenantId}. Compared before and after an import it states that nothing was written elsewhere,
   * for every table carrying a {@code tenant_id}, not only the ones a test thought of.
   */
  private Map<String, Long> rowsOutsideTenant(String tenantId) {
    entityManager.flush();
    Map<String, Long> counts = new TreeMap<>();
    for (String table : tenantScopedTables()) {
      counts.put(
          table,
          entityManager
              .unwrap(Session.class)
              .doReturningWork(
                  connection -> {
                    try (PreparedStatement statement =
                        connection.prepareStatement(
                            "SELECT count(*) FROM \""
                                + table
                                + "\" WHERE tenant_id::text IS DISTINCT FROM ?")) {
                      statement.setString(1, tenantId);
                      try (ResultSet resultSet = statement.executeQuery()) {
                        resultSet.next();
                        return resultSet.getLong(1);
                      }
                    }
                  }));
    }
    return counts;
  }

  private List<String> tenantScopedTables() {
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              List<String> tables = new ArrayList<>();
              try (PreparedStatement statement =
                      connection.prepareStatement(
                          "SELECT c.table_name FROM information_schema.columns c JOIN"
                              + " information_schema.tables t ON t.table_schema = c.table_schema"
                              + " AND t.table_name = c.table_name WHERE c.table_schema = 'public'"
                              + " AND c.column_name = 'tenant_id' AND t.table_type = 'BASE TABLE'"
                              + " AND c.table_name <> 'tenants'");
                  ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                  tables.add(resultSet.getString(1));
                }
              }
              return tables;
            });
  }

  private List<String> tenantsOf(String sql, String... parameters) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              List<String> tenants = new ArrayList<>();
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < parameters.length; i++) {
                  statement.setString(i + 1, parameters[i]);
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                  while (resultSet.next()) {
                    tenants.add(resultSet.getString(1));
                  }
                }
              }
              return tenants;
            });
  }
}
