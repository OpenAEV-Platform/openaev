package io.openaev.rest.document;

import static io.openaev.collectors.expectations_vulnerability_manager.ExpectationsVulnerabilityManagerCollector.EXPECTATIONS_VULNERABILITY_COLLECTOR_TYPE;
import static io.openaev.collectors.utils.CollectorsUtils.FAKE_DETECTOR;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.collector.CollectorApi.COLLECTOR_URI;
import static io.openaev.rest.document.DocumentApi.DOCUMENT_API;
import static io.openaev.rest.document.DocumentApi.TENANT_DOCUMENT_API;
import static io.openaev.service.FileService.COLLECTORS_IMAGES_BASE_PATH;
import static io.openaev.service.FileService.EXECUTORS_IMAGES_BANNERS_BASE_PATH;
import static io.openaev.service.FileService.EXECUTORS_IMAGES_ICONS_BASE_PATH;
import static io.openaev.service.FileService.EXT_PNG;
import static io.openaev.service.FileService.INJECTORS_IMAGES_BASE_PATH;
import static io.openaev.service.FileService.SECRETS_PROVIDERS_IMAGES_ICONS_BASE_PATH;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.collectors.expectations_vulnerability_manager.ExpectationsVulnerabilityManagerCollector;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.executors.ExecutorService;
import io.openaev.injectors.manual.ManualContract;
import io.openaev.integration.impl.injectors.manual.ManualInjectorIntegrationFactory;
import io.openaev.integration.impl.secrets.local.LocalSecretsProviderIntegrationFactory;
import io.openaev.rest.collector.form.CollectorCreateInput;
import io.openaev.rest.executor.form.ExecutorCreateInput;
import io.openaev.secrets.provider.SecretsProviderType;
import io.openaev.service.MinioService;
import io.openaev.service.RabbitmqService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * Every storage object key names the tenant of the row it belongs to, whatever the ambient
 * thread-local holds. The caller belongs to two tenants, A and B, and the ambient tenant is cleared
 * first, so it falls back to the default tenant, as on a request thread of the non-prefixed route
 * or on a job thread. A key derived from the ambient tenant lands under the default tenant's prefix
 * and goes red here.
 *
 * <p>Requests commit (no test transaction): the objects are real MinIO side effects, removed with
 * the tenants' prefixes on teardown, and the rows go with the committed tenants.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithMockUser(isAdmin = true)
@DisplayName("Storage object keys name the tenant of their row")
class StorageObjectKeyTenantTest extends ProductionTablesIntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;
  private static final String API = "/api";
  private static final String TENANT_COLLECTOR_URI = TENANT_PREFIX + "/collectors";
  private static final String EXECUTORS_PATH = "/executors";
  private static final String INJECTORS_PATH = "/injectors";
  private static final String COLLECTOR_IMAGE_PATH = "/collectors/{type}/image";
  private static final String COLLECTOR_IMAGE_BY_ID_PATH = "/collectors/id/{id}/image";
  private static final String EXECUTOR_ICON_PATH = "/images/executors/icons/{type}";
  private static final String EXECUTOR_BANNER_PATH = "/images/executors/banners/{type}";
  private static final String INJECTOR_IMAGE_PATH = "/injectors/{type}/image";
  private static final String SECRETS_PROVIDER_IMAGE_PATH = "/secrets_providers/{type}/image";
  private static final String SECURITY_PLATFORM_IMAGE_PATH =
      "/images/security_platforms/id/{id}/light";
  private static final String CHANNEL_IMAGE_PATH = "/images/channels/id/{id}/light";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private MinioService minioService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private ExpectationsVulnerabilityManagerCollector builtinCollector;
  @Autowired private ExecutorService executorService;
  @Autowired private ManualInjectorIntegrationFactory manualInjectorFactory;
  @Autowired private LocalSecretsProviderIntegrationFactory localSecretsProviderFactory;
  @Autowired private RabbitmqService rabbitmqService;
  @Autowired private DataSource dataSource;

  private String tenantA;
  private String tenantB;
  private final List<String> defaultTenantKeys = new ArrayList<>();
  private final List<String> injectorIds = new ArrayList<>();

  @BeforeEach
  void seedTwoTenantsAndClearTheAmbientTenant() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("storage-key-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("storage-key-b").getId();
    // Onboarding leaves a tenant on the test thread; production threads carry none.
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(DEFAULT_TENANT);
  }

  @AfterEach
  void removeObjectsAndTenants() throws Exception {
    for (String key : defaultTenantKeys) {
      minioService.deleteFileForTenant(DEFAULT_TENANT, key);
    }
    defaultTenantKeys.clear();
    injectorIds.forEach(rabbitmqService::deleteInjectorQueueIfUnused);
    injectorIds.clear();
    minioService.deleteDependencyForTenant(tenantA);
    minioService.deleteDependencyForTenant(tenantB);
    tenantHelper.deleteCommittedTenants(tenantA, tenantB);
    TenantContext.clearCurrentTenant();
  }

  /** The tenant-prefixed route and the non-prefixed route with {@code X-Tenant-Ids}. */
  enum Route {
    PREFIXED,
    HEADER
  }

  static Stream<Arguments> routes() {
    return Stream.of(Arguments.of(Route.PREFIXED), Arguments.of(Route.HEADER));
  }

  /** How a two-tenant caller leaves its tenant unselected on the non-prefixed route. */
  enum Selector {
    NO_HEADER,
    TWO_TENANT_HEADER
  }

  /** The parent rows whose logo falls back to the fake detector icon. */
  enum Parent {
    SECURITY_PLATFORM,
    CHANNEL
  }

  /** The connector image routes, each served on both routes. */
  enum ConnectorImage {
    COLLECTOR_BY_TYPE,
    COLLECTOR_BY_ID,
    EXECUTOR_ICON,
    EXECUTOR_BANNER,
    INJECTOR
  }

  static Stream<Arguments> connectorImagesAndRoutes() {
    return Arrays.stream(ConnectorImage.values())
        .flatMap(image -> Arrays.stream(Route.values()).map(route -> Arguments.of(image, route)));
  }

  static Stream<Arguments> parentsAndRoutes() {
    return Arrays.stream(Parent.values())
        .flatMap(parent -> Arrays.stream(Route.values()).map(route -> Arguments.of(parent, route)));
  }

  private <B extends AbstractMockHttpServletRequestBuilder<B>> B on(
      Route route, String tenantId, B prefixed, B plain) {
    return route == Route.PREFIXED ? prefixed : plain.header(TENANT_HEADER, tenantId);
  }

  /** A GET of {@code /api<path>} on the given route, in the given tenant. */
  private ResultActions getIn(Route route, String tenantId, String path, Object... vars)
      throws Exception {
    Object[] prefixedVars = Stream.concat(Stream.of(tenantId), Arrays.stream(vars)).toArray();
    return mvc.perform(
        on(route, tenantId, get(TENANT_PREFIX + path, prefixedVars), get(API + path, vars)));
  }

  private static byte[] bodyOf(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsByteArray();
  }

  private int objectsUnder(String tenantId, String key) {
    return minioService.countObjects(tenantId + (key.startsWith("/") ? key : "/" + key));
  }

  /** Writes unique bytes under a tenant's key, so a read of that key alone returns them. */
  private byte[] markerUnder(String tenantId, String key) throws Exception {
    byte[] marker = ("marker-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    if (DEFAULT_TENANT.equals(tenantId)) {
      defaultTenantKeys.add(key);
    }
    minioService.uploadFileForTenant(
        tenantId, key, new ByteArrayInputStream(marker), marker.length, MediaType.IMAGE_PNG_VALUE);
    return marker;
  }

  private static byte[] uniqueBytes(String label) {
    return (label + "-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
  }

  private static MockMultipartFile jsonPart(String name, Object input) {
    return new MockMultipartFile(
        name,
        name + ".json",
        MediaType.APPLICATION_JSON_VALUE,
        asJsonString(input).getBytes(StandardCharsets.UTF_8));
  }

  private static MockMultipartFile pngPart(String name, byte[] content) {
    return new MockMultipartFile(name, name + ".png", MediaType.IMAGE_PNG_VALUE, content);
  }

  /** Runs a registration scoped to the tenant on a thread with no ambient tenant, as a job does. */
  private void registerWithNoAmbientTenant(String tenantId, Registration registration) {
    TenantContext.clearCurrentTenant();
    tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          try {
            registration.run();
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
        });
  }

  @FunctionalInterface
  private interface Registration {
    void run() throws Exception;
  }

  /** Registers a collector of the given type in a tenant and returns its id. */
  private String registerCollector(
      Route route, String tenantId, String type, MockMultipartFile icon) throws Exception {
    CollectorCreateInput input = new CollectorCreateInput();
    input.setId(UUID.randomUUID().toString());
    input.setName(type);
    input.setType(type);
    MockMultipartFile inputPart = jsonPart("input", input);
    mvc.perform(
            on(
                    route,
                    tenantId,
                    multipart(TENANT_COLLECTOR_URI, tenantId).file(inputPart).file(icon),
                    multipart(COLLECTOR_URI).file(inputPart).file(icon))
                .with(csrf()))
        .andExpect(status().isOk());
    return input.getId();
  }

  /** Seeds a parent row of B with no logo, so its image falls back to the fake detector icon. */
  private String seedParentOfB(Parent parent) {
    return parent == Parent.SECURITY_PLATFORM ? seedSecurityPlatform() : seedChannel();
  }

  private String seedSecurityPlatform() {
    String id = UUID.randomUUID().toString();
    new JdbcTemplate(dataSource)
        .update(
            "INSERT INTO assets (asset_id, asset_type, asset_name, security_platform_type,"
                + " tenant_id) VALUES (?, 'SecurityPlatform', ?, 'EDR', ?)",
            id,
            "storage-key-sp-" + id,
            tenantB);
    return id;
  }

  private String seedChannel() {
    String id = UUID.randomUUID().toString();
    new JdbcTemplate(dataSource)
        .update(
            "INSERT INTO channels (channel_id, channel_type, channel_name, tenant_id) VALUES (?,"
                + " 'newsletter', ?, ?)",
            id,
            "storage-key-channel-" + id,
            tenantB);
    return id;
  }

  @Nested
  @DisplayName("A document of tenant B")
  class DocumentLifecycle {

    @ParameterizedTest(name = "{0} route")
    @MethodSource("io.openaev.rest.document.StorageObjectKeyTenantTest#routes")
    @DisplayName(
        "given a document uploaded in B should be read, listed and deleted under B's key and never"
            + " reached from A")
    void given_documentUploadedInB_should_beReadListedAndDeletedUnderBsKeyOnly(Route route)
        throws Exception {
      // Arrange
      byte[] content = ("storage-key-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
      String target = DigestUtils.md5Hex(content) + ".txt";
      defaultTenantKeys.add(target);

      // Act: write
      String documentId = upload(route, tenantB, content);

      // Assert: written under B, nothing under the default tenant
      assertThat(objectsUnder(tenantB, target)).isEqualTo(1);
      assertThat(objectsUnder(DEFAULT_TENANT, target)).isZero();

      // Assert: read by id and listed under B
      byte[] read =
          mvc.perform(
                  on(
                      route,
                      tenantB,
                      get(TENANT_DOCUMENT_API + "/{id}/file", tenantB, documentId),
                      get(DOCUMENT_API + "/{id}/file", documentId)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();
      assertThat(read).isEqualTo(content);
      assertThat(listedIds(route, tenantB)).contains(documentId);

      // Assert: A reads nothing, lists nothing and deletes nothing
      mvc.perform(
              on(
                  route,
                  tenantA,
                  get(TENANT_DOCUMENT_API + "/{id}/file", tenantA, documentId),
                  get(DOCUMENT_API + "/{id}/file", documentId)))
          .andExpect(status().isNotFound());
      assertThat(listedIds(route, tenantA)).doesNotContain(documentId);
      mvc.perform(
              on(
                      route,
                      tenantA,
                      delete(TENANT_DOCUMENT_API + "/{id}", tenantA, documentId),
                      delete(DOCUMENT_API + "/{id}", documentId))
                  .with(csrf()))
          .andExpect(status().isNotFound());
      assertThat(objectsUnder(tenantB, target)).isEqualTo(1);

      // Act: delete under B
      mvc.perform(
              on(
                      route,
                      tenantB,
                      delete(TENANT_DOCUMENT_API + "/{id}", tenantB, documentId),
                      delete(DOCUMENT_API + "/{id}", documentId))
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert: the object goes with its row
      assertThat(objectsUnder(tenantB, target)).isZero();
    }

    private String upload(Route route, String tenantId, byte[] content) throws Exception {
      MockPart input = new MockPart("input", "{}".getBytes(StandardCharsets.UTF_8));
      input.getHeaders().setContentType(MediaType.APPLICATION_JSON);
      MockMultipartFile file =
          new MockMultipartFile(
              "file", "storage-key-" + UUID.randomUUID() + ".txt", "text/plain", content);
      String response =
          mvc.perform(
                  on(
                          route,
                          tenantId,
                          multipart(TENANT_DOCUMENT_API, tenantId).part(input).file(file),
                          multipart(DOCUMENT_API).part(input).file(file))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      return JsonPath.read(response, "$.document_id");
    }

    private List<String> listedIds(Route route, String tenantId) throws Exception {
      String response =
          mvc.perform(on(route, tenantId, get(TENANT_DOCUMENT_API, tenantId), get(DOCUMENT_API)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      return JsonPath.read(response, "$[*].document_id");
    }
  }

  @Nested
  @DisplayName("A collector icon")
  class CollectorIcon {

    @ParameterizedTest(name = "{0} route")
    @MethodSource("io.openaev.rest.document.StorageObjectKeyTenantTest#routes")
    @DisplayName(
        "given a collector registered in B should store its icon under B and serve it in B only,"
            + " by type and by id")
    void given_collectorRegisteredInB_should_storeAndServeItsIconUnderBOnly(Route route)
        throws Exception {
      // Arrange
      String type = "storage-key-" + UUID.randomUUID();
      String iconKey = COLLECTORS_IMAGES_BASE_PATH + type + EXT_PNG;
      defaultTenantKeys.add(iconKey);
      byte[] icon = uniqueBytes("collector-icon");

      // Act
      String collectorId = registerCollector(route, tenantB, type, pngPart("icon", icon));

      // Assert: stored under B, served in B by type and by id, not served in A
      assertThat(objectsUnder(tenantB, iconKey)).isEqualTo(1);
      assertThat(objectsUnder(DEFAULT_TENANT, iconKey)).isZero();
      assertThat(
              bodyOf(getIn(route, tenantB, COLLECTOR_IMAGE_PATH, type).andExpect(status().isOk())))
          .isEqualTo(icon);
      assertThat(
              bodyOf(
                  getIn(route, tenantB, COLLECTOR_IMAGE_BY_ID_PATH, collectorId)
                      .andExpect(status().isOk())))
          .isEqualTo(icon);
      getIn(route, tenantA, COLLECTOR_IMAGE_PATH, type).andExpect(status().isNotFound());
      getIn(route, tenantA, COLLECTOR_IMAGE_BY_ID_PATH, collectorId)
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "given a built-in collector registered for B on a thread with no ambient tenant should store"
            + " its icon under B")
    void given_builtinRegisteredForBWithNoAmbientTenant_should_storeItsIconUnderB() {
      // Arrange: tenant creation may already have written B's built-in icon
      String iconKey =
          COLLECTORS_IMAGES_BASE_PATH + EXPECTATIONS_VULNERABILITY_COLLECTOR_TYPE + EXT_PNG;
      deleteUnder(tenantB, iconKey);

      // Act: the registration a manager runs for its tenant
      registerWithNoAmbientTenant(tenantB, () -> builtinCollector.registerForTenant(tenantB));

      // Assert
      assertThat(objectsUnder(tenantB, iconKey)).isEqualTo(1);
    }

    @Test
    @DisplayName(
        "given an icon stored under the default tenant for a collector type of B should serve it on"
            + " B's route")
    void given_iconUnderTheDefaultTenantForATypeOfB_should_serveItOnBsRoute() throws Exception {
      // Arrange: B owns the type, the icon sits where earlier versions wrote it
      String type = "storage-key-" + UUID.randomUUID();
      String iconKey = COLLECTORS_IMAGES_BASE_PATH + type + EXT_PNG;
      registerCollector(
          Route.PREFIXED,
          tenantB,
          type,
          new MockMultipartFile("icon", "icon.txt", MediaType.TEXT_PLAIN_VALUE, new byte[] {1}));
      byte[] legacyIcon = markerUnder(DEFAULT_TENANT, iconKey);
      assertThat(objectsUnder(tenantB, iconKey)).isZero();

      // Act
      byte[] served =
          bodyOf(
              getIn(Route.PREFIXED, tenantB, COLLECTOR_IMAGE_PATH, type)
                  .andExpect(status().isOk()));

      // Assert
      assertThat(served).isEqualTo(legacyIcon);
    }
  }

  @Nested
  @DisplayName("An executor icon and banner")
  class ExecutorImages {

    @ParameterizedTest(name = "{0} route")
    @MethodSource("io.openaev.rest.document.StorageObjectKeyTenantTest#routes")
    @DisplayName(
        "given an executor registered in B should store its icon and banner under B and serve them"
            + " in B only")
    void given_executorRegisteredInB_should_storeAndServeItsImagesUnderBOnly(Route route)
        throws Exception {
      // Arrange
      String type = "storage-key-" + UUID.randomUUID();
      String iconKey = EXECUTORS_IMAGES_ICONS_BASE_PATH + type + EXT_PNG;
      String bannerKey = EXECUTORS_IMAGES_BANNERS_BASE_PATH + type + EXT_PNG;
      defaultTenantKeys.addAll(List.of(iconKey, bannerKey));
      byte[] icon = uniqueBytes("executor-icon");
      byte[] banner = uniqueBytes("executor-banner");
      ExecutorCreateInput input = new ExecutorCreateInput();
      input.setId(UUID.randomUUID().toString());
      input.setName(type);
      input.setType(type);
      MockMultipartFile inputPart = jsonPart("input", input);

      // Act
      mvc.perform(
              on(
                      route,
                      tenantB,
                      multipart(TENANT_PREFIX + EXECUTORS_PATH, tenantB)
                          .file(inputPart)
                          .file(pngPart("icon", icon))
                          .file(pngPart("banner", banner)),
                      multipart(API + EXECUTORS_PATH)
                          .file(inputPart)
                          .file(pngPart("icon", icon))
                          .file(pngPart("banner", banner)))
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert: stored under B only
      assertThat(objectsUnder(tenantB, iconKey)).isEqualTo(1);
      assertThat(objectsUnder(tenantB, bannerKey)).isEqualTo(1);
      assertThat(objectsUnder(DEFAULT_TENANT, iconKey)).isZero();
      assertThat(objectsUnder(DEFAULT_TENANT, bannerKey)).isZero();

      // Assert: served in B, not in A
      assertThat(bodyOf(getIn(route, tenantB, EXECUTOR_ICON_PATH, type).andExpect(status().isOk())))
          .isEqualTo(icon);
      assertThat(
              bodyOf(getIn(route, tenantB, EXECUTOR_BANNER_PATH, type).andExpect(status().isOk())))
          .isEqualTo(banner);
      getIn(route, tenantA, EXECUTOR_ICON_PATH, type).andExpect(status().isNotFound());
      assertThat(bodyOf(getIn(route, tenantA, EXECUTOR_BANNER_PATH, type))).isEmpty();
    }

    @Test
    @DisplayName(
        "given an executor registered for B on a thread with no ambient tenant should store its icon"
            + " and banner under B")
    void given_executorRegisteredForBWithNoAmbientTenant_should_storeItsImagesUnderB() {
      // Arrange
      String type = "storage-key-" + UUID.randomUUID();
      String iconKey = EXECUTORS_IMAGES_ICONS_BASE_PATH + type + EXT_PNG;
      String bannerKey = EXECUTORS_IMAGES_BANNERS_BASE_PATH + type + EXT_PNG;
      defaultTenantKeys.addAll(List.of(iconKey, bannerKey));
      InputStream icon = new ByteArrayInputStream(uniqueBytes("executor-icon"));
      InputStream banner = new ByteArrayInputStream(uniqueBytes("executor-banner"));

      // Act: the registration an executor integration runs for its tenant
      registerWithNoAmbientTenant(
          tenantB,
          () ->
              executorService.register(
                  tenantB,
                  UUID.randomUUID().toString(),
                  type,
                  type,
                  null,
                  null,
                  icon,
                  banner,
                  new String[] {"Linux"}));

      // Assert
      assertThat(objectsUnder(tenantB, iconKey)).isEqualTo(1);
      assertThat(objectsUnder(tenantB, bannerKey)).isEqualTo(1);
      assertThat(objectsUnder(DEFAULT_TENANT, iconKey)).isZero();
    }
  }

  @Nested
  @DisplayName("An injector icon")
  class InjectorIcon {

    @ParameterizedTest(name = "{0} route")
    @MethodSource("io.openaev.rest.document.StorageObjectKeyTenantTest#routes")
    @DisplayName(
        "given an external injector registered in B should store its icon under B and serve it in B"
            + " only")
    void given_externalInjectorRegisteredInB_should_storeAndServeItsIconUnderBOnly(Route route)
        throws Exception {
      // Arrange
      String type = "storage-key-" + UUID.randomUUID();
      String iconKey = INJECTORS_IMAGES_BASE_PATH + type + EXT_PNG;
      defaultTenantKeys.add(iconKey);
      byte[] icon = uniqueBytes("injector-icon");
      String injectorId = UUID.randomUUID().toString();
      injectorIds.add(injectorId);
      MockMultipartFile inputPart =
          jsonPart(
              "input",
              InjectorFixture.createDefaultInjectorCreateInput(
                  injectorId, type, type, UUID.randomUUID().toString()));

      // Act
      mvc.perform(
              on(
                      route,
                      tenantB,
                      multipart(TENANT_PREFIX + INJECTORS_PATH, tenantB)
                          .file(inputPart)
                          .file(pngPart("icon", icon)),
                      multipart(API + INJECTORS_PATH).file(inputPart).file(pngPart("icon", icon)))
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert: stored under B, served in B, not served in A
      assertThat(objectsUnder(tenantB, iconKey)).isEqualTo(1);
      assertThat(objectsUnder(DEFAULT_TENANT, iconKey)).isZero();
      assertThat(
              bodyOf(getIn(route, tenantB, INJECTOR_IMAGE_PATH, type).andExpect(status().isOk())))
          .isEqualTo(icon);
      getIn(route, tenantA, INJECTOR_IMAGE_PATH, type).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "given a built-in injector registered for B on a thread with no ambient tenant should store"
            + " its icon under B")
    void given_builtinInjectorRegisteredForBWithNoAmbientTenant_should_storeItsIconUnderB() {
      // Arrange: tenant creation may already have written B's built-in icon
      String iconKey = INJECTORS_IMAGES_BASE_PATH + ManualContract.TYPE + EXT_PNG;
      deleteUnder(tenantB, iconKey);

      // Act
      registerWithNoAmbientTenant(tenantB, () -> manualInjectorFactory.registerForTenant(tenantB));

      // Assert
      assertThat(objectsUnder(tenantB, iconKey)).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("A secrets provider icon")
  class SecretsProviderIcon {

    @Test
    @DisplayName(
        "given the local secrets provider registered for B on a thread with no ambient tenant should"
            + " store its icon under B and serve B's copy")
    void given_localProviderRegisteredForBWithNoAmbientTenant_should_storeAndServeItsIconInB()
        throws Exception {
      // Arrange: tenant creation may already have written B's icon
      String type = SecretsProviderType.LOCAL.type;
      String iconKey = SECRETS_PROVIDERS_IMAGES_ICONS_BASE_PATH + type + EXT_PNG;
      deleteUnder(tenantB, iconKey);

      // Act
      registerWithNoAmbientTenant(
          tenantB, () -> localSecretsProviderFactory.registerForTenant(tenantB));

      // Assert: stored under B; B's route serves B's copy, not the default tenant's
      assertThat(objectsUnder(tenantB, iconKey)).isEqualTo(1);
      byte[] bCopy = markerUnder(tenantB, iconKey);
      assertThat(
              bodyOf(
                  getIn(Route.PREFIXED, tenantB, SECRETS_PROVIDER_IMAGE_PATH, type)
                      .andExpect(status().isOk())))
          .isEqualTo(bCopy);
    }
  }

  @Nested
  @DisplayName("The fake detector fallback image")
  class FakeDetectorFallback {

    @ParameterizedTest(name = "{0}, {1} route")
    @MethodSource("io.openaev.rest.document.StorageObjectKeyTenantTest#parentsAndRoutes")
    @DisplayName(
        "given a parent of B without a logo should serve B's fake detector icon in B and nothing in"
            + " A")
    void given_parentOfBWithoutLogo_should_serveBsFakeDetectorIconInBOnly(
        Parent parent, Route route) throws Exception {
      // Arrange
      String parentId = seedParentOfB(parent);
      String imagePath =
          parent == Parent.SECURITY_PLATFORM ? SECURITY_PLATFORM_IMAGE_PATH : CHANNEL_IMAGE_PATH;
      byte[] bIcon = markerUnder(tenantB, COLLECTORS_IMAGES_BASE_PATH + FAKE_DETECTOR + EXT_PNG);

      // Act
      byte[] served = bodyOf(getIn(route, tenantB, imagePath, parentId).andExpect(status().isOk()));

      // Assert
      assertThat(served).isEqualTo(bIcon);
      getIn(route, tenantA, imagePath, parentId).andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("The cache policy of an image")
  class ImageCachePolicy {

    private static final String CACHEABLE = "max-age=300";
    private static final String NOT_STORED = "no-store";

    @ParameterizedTest(name = "{0}, {1} route")
    @MethodSource("io.openaev.rest.document.StorageObjectKeyTenantTest#connectorImagesAndRoutes")
    @DisplayName(
        "given a connector image of B should be cacheable on the prefixed route and not stored on"
            + " the non-prefixed route")
    void given_connectorImageOfB_should_beCacheableOnlyOnThePrefixedRoute(
        ConnectorImage image, Route route) throws Exception {
      // Arrange
      String type = "storage-key-" + UUID.randomUUID();
      String path;
      String var = type;
      switch (image) {
        case COLLECTOR_BY_TYPE -> {
          markerUnder(tenantB, COLLECTORS_IMAGES_BASE_PATH + type + EXT_PNG);
          path = COLLECTOR_IMAGE_PATH;
        }
        case COLLECTOR_BY_ID -> {
          var =
              registerCollector(Route.PREFIXED, tenantB, type, pngPart("icon", uniqueBytes(type)));
          path = COLLECTOR_IMAGE_BY_ID_PATH;
        }
        case EXECUTOR_ICON -> {
          markerUnder(tenantB, EXECUTORS_IMAGES_ICONS_BASE_PATH + type + EXT_PNG);
          path = EXECUTOR_ICON_PATH;
        }
        case EXECUTOR_BANNER -> {
          markerUnder(tenantB, EXECUTORS_IMAGES_BANNERS_BASE_PATH + type + EXT_PNG);
          path = EXECUTOR_BANNER_PATH;
        }
        case INJECTOR -> {
          markerUnder(tenantB, INJECTORS_IMAGES_BASE_PATH + type + EXT_PNG);
          path = INJECTOR_IMAGE_PATH;
        }
        default -> throw new IllegalArgumentException(image.name());
      }

      // Act
      ResultActions result = getIn(route, tenantB, path, var).andExpect(status().isOk());

      // Assert
      result.andExpect(
          header()
              .string(HttpHeaders.CACHE_CONTROL, route == Route.PREFIXED ? CACHEABLE : NOT_STORED));
    }

    @Test
    @DisplayName(
        "given a secrets provider image of B should be cacheable on its route, which is prefixed"
            + " only")
    void given_secretsProviderImageOfB_should_beCacheable() throws Exception {
      // Arrange
      String type = SecretsProviderType.LOCAL.type;
      markerUnder(tenantB, SECRETS_PROVIDERS_IMAGES_ICONS_BASE_PATH + type + EXT_PNG);

      // Act
      ResultActions result =
          getIn(Route.PREFIXED, tenantB, SECRETS_PROVIDER_IMAGE_PATH, type)
              .andExpect(status().isOk());

      // Assert
      result.andExpect(header().string(HttpHeaders.CACHE_CONTROL, CACHEABLE));
    }

    @ParameterizedTest(name = "{0}, {1} route")
    @MethodSource("io.openaev.rest.document.StorageObjectKeyTenantTest#parentsAndRoutes")
    @DisplayName(
        "given a parent of B without a logo should not store the fake detector image on either"
            + " route")
    void given_parentOfBWithoutLogo_should_notStoreTheFallbackImage(Parent parent, Route route)
        throws Exception {
      // Arrange
      String parentId = seedParentOfB(parent);
      String imagePath =
          parent == Parent.SECURITY_PLATFORM ? SECURITY_PLATFORM_IMAGE_PATH : CHANNEL_IMAGE_PATH;
      markerUnder(tenantB, COLLECTORS_IMAGES_BASE_PATH + FAKE_DETECTOR + EXT_PNG);

      // Act
      ResultActions result = getIn(route, tenantB, imagePath, parentId).andExpect(status().isOk());

      // Assert: the response sets no cache policy, so the application default applies
      result.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString(NOT_STORED)));
    }
  }

  @Nested
  @DisplayName("A non-prefixed image route")
  class NonPrefixedImageRoute {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Selector.class)
    @DisplayName(
        "given a caller in two tenants without the default one should refuse an image request"
            + " that does not pick one tenant")
    void given_twoTenantCallerWithoutOneSelectedTenant_should_refuseTheImage(Selector selector)
        throws Exception {
      // Arrange
      var request = get(API + COLLECTOR_IMAGE_PATH, "any-type");
      if (selector == Selector.TWO_TENANT_HEADER) {
        request.header(TENANT_HEADER, tenantA + "," + tenantB);
      }

      // Act / Assert
      mvc.perform(request).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("given a caller with no tenant should refuse an image request")
    void given_callerWithNoTenant_should_refuseTheImage() throws Exception {
      // Arrange
      tenantHelper.detachCurrentUserFromTenant(tenantA);
      tenantHelper.detachCurrentUserFromTenant(tenantB);

      // Act / Assert
      mvc.perform(get(API + COLLECTOR_IMAGE_PATH, "any-type")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("given a caller in one tenant should serve that tenant's image without a header")
    void given_singleTenantCaller_should_serveItsTenantsImage() throws Exception {
      // Arrange
      tenantHelper.detachCurrentUserFromTenant(tenantA);
      String type = "storage-key-" + UUID.randomUUID();
      byte[] bIcon = markerUnder(tenantB, COLLECTORS_IMAGES_BASE_PATH + type + EXT_PNG);

      // Act
      byte[] served =
          bodyOf(mvc.perform(get(API + COLLECTOR_IMAGE_PATH, type)).andExpect(status().isOk()));

      // Assert
      assertThat(served).isEqualTo(bIcon);
    }
  }

  private void deleteUnder(String tenantId, String key) {
    try {
      minioService.deleteFileForTenant(tenantId, key);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    assertThat(objectsUnder(tenantId, key)).isZero();
  }
}
