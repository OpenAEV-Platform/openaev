package io.openaev.service.tenants;

import static io.openaev.service.tenants.TenantService.SOFT_DELETE_RETENTION_DAYS;
import static io.openaev.utils.fixtures.tenants.TenantFixture.TENANT_NAME;
import static io.openaev.utils.fixtures.tenants.TenantFixture.getTenant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import io.openaev.IntegrationTest;
import io.openaev.api.tenants.TenantInput;
import io.openaev.api.tenants.TenantOutput;
import io.openaev.config.MinioConfig;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.datapack.packs.V20260330_Default_tenant_data;
import io.openaev.executors.ExecutorService;
import io.openaev.helper.StreamHelper;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.service.InjectorService;
import io.openaev.service.RoleService;
import io.openaev.utils.fixtures.DetectionRemediationFixture;
import io.openaev.utils.fixtures.ExecutorFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.composers.DetectionRemediationComposer;
import io.openaev.utils.fixtures.composers.DomainComposer;
import io.openaev.utils.fixtures.composers.ExecutorComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@WithMockUser
class TenantServiceTest extends IntegrationTest {

  @Autowired private TenantService tenantService;

  @Autowired private TenantComposer tenantComposer;
  @Autowired protected EntityManager entityManager;
  @Autowired private MinioConfig minioConfig;
  @Autowired private MinioClient minioClient;
  @Autowired private DomainRepository domainRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private VulnerabilityRepository vulnerabilityRepository;
  @Autowired private CweRepository cweRepository;
  @Autowired private RoleService roleService;
  @Autowired private GroupRepository groupRepository;
  @Autowired private CollectorTypeRepository collectorTypeRepository;
  @Autowired private CollectorRepository collectorRepository;
  @Autowired private ExecutorRepository executorRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private ExecutorComposer executorComposer;
  @Autowired private ExecutorFixture executorFixture;
  @Autowired private DetectionRemediationComposer detectionRemediationComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private CollectorService collectorService;
  @Autowired private ExecutorService executorService;
  @Autowired private InjectorService injectorService;
  @Autowired private DomainComposer domainComposer;
  @Autowired private V20260330_Default_tenant_data datapack;

  @Test
  void should_create_and_find_tenant() throws Exception {
    // -- ARRANGE --
    Tenant tenant = getTenant();

    // Seed fake executor and injector in the default tenant so create() copies them
    Executor fakeExecutor = executorFixture.createDefaultExecutor("Fake Executor");
    executorComposer.forExecutor(fakeExecutor).persist();

    Injector fakeInjector =
        InjectorFixture.createInjector("fake-injector-id", "Fake Injector", "fake_type");
    injectorRepository.save(fakeInjector);

    // -- ACT --
    Tenant created = tenantService.create(tenant);
    TenantContext.setCurrentTenant(tenant.getId());
    // Simulate for tenant creation because Dataprocessor has @Profile("!test")
    datapack.process(created);

    // Upload a file to verify MinIO path-based isolation works
    byte[] content = "tenant-test-content".getBytes(StandardCharsets.UTF_8);
    InputStream data = new ByteArrayInputStream(content);
    minioClient.putObject(
        PutObjectArgs.builder()
            .bucket(minioConfig.getBucket())
            .object(created.getId() + "/test-file.txt")
            .stream(data, content.length, -1)
            .contentType("text/plain")
            .build());

    // -- ASSERT --
    assertThat(created.getId()).isNotNull();
    assertThat(created.getName()).isEqualTo(TENANT_NAME);
    // Verify the file exists under the tenant prefix
    Iterable<Result<Item>> results =
        minioClient.listObjects(
            ListObjectsArgs.builder()
                .bucket(minioConfig.getBucket())
                .prefix(created.getId() + "/")
                .maxKeys(1)
                .build());
    boolean pathExists = results.iterator().hasNext();
    assertThat(pathExists).isTrue();

    // Verify the 9 domains from PresetDomain are created for this tenant
    Session session = entityManager.unwrap(Session.class);
    session.enableFilter("tenantFilter").setParameter("tenantId", created.getId());
    assertThat(domainRepository.findAll()).hasSize(9);
    // Verify datapack
    assertThat(vulnerabilityRepository.findAll()).hasSize(7);
    assertThat(cweRepository.findAll()).hasSize(7);
    assertThat(roleService.findAll()).hasSize(3);
    List<Group> groups = StreamHelper.fromIterable(groupRepository.findAll());
    assertThat(groups).hasSize(3);
    assertThat(
            groups.stream()
                .filter(group -> group.getName().equals("Admin"))
                .findFirst()
                .get()
                .getUsers())
        .hasSize(1);

    // Verify collector types were copied from the default tenant
    List<CollectorType> collectorTypes =
        StreamHelper.fromIterable(collectorTypeRepository.findAll());
    assertThat(collectorTypes).isNotEmpty();
    assertThat(collectorTypes)
        .extracting(CollectorType::getName)
        .contains("openaev_fake_detector", "openaev_expectations_vulnerability_manager");
    // Verify built-in (non-external) collectors were copied
    List<Collector> collectors = StreamHelper.fromIterable(collectorRepository.findAll());
    assertThat(collectors).isNotEmpty();
    assertThat(collectors).allMatch(c -> !c.isExternal());
    // Verify executor was copied from the default tenant
    List<Executor> executors = StreamHelper.fromIterable(executorRepository.findAll());
    assertThat(executors).extracting(Executor::getName).contains("Fake Executor");
    // Verify built-in injector was copied from the default tenant
    List<Injector> injectors = StreamHelper.fromIterable(injectorRepository.findAll());
    assertThat(injectors).extracting(Injector::getName).contains("Fake Injector");

    Tenant exists = tenantService.findById(created.getId());
    assertThat(exists.getName()).isEqualTo(TENANT_NAME);
  }

  @Test
  void should_fail_when_updating_tenant_with_existing_name() {
    // -- ARRANGE --
    Tenant tenantA = getTenant("Tenant A");
    Tenant tenantB = getTenant("Tenant B");

    tenantComposer.forTenant(tenantA).persist();
    tenantComposer.forTenant(tenantB).persist();

    TenantInput duplicateNameInput = new TenantInput("Tenant A", null);

    // -- ACT & ASSERT --
    assertThatThrownBy(
            () -> {
              tenantService.update(tenantB.getId(), duplicateNameInput);
              entityManager.flush();
            })
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  void should_find_all_tenants() {
    // -- ARRANGE --
    String tenantNameA = "Tenant A";
    Tenant tenantA = getTenant(tenantNameA);
    String tenantNameB = "Tenant B";
    Tenant tenantB = getTenant(tenantNameB);

    tenantComposer.forTenant(tenantA).persist();
    tenantComposer.forTenant(tenantB).persist();
    SearchPaginationInput searchInput = new SearchPaginationInput();
    searchInput.setPage(0);
    searchInput.setSize(10);

    // -- ACT --
    Page<TenantOutput> result = tenantService.search(searchInput);

    // -- ASSERT --
    assertThat(result.getContent())
        .extracting(TenantOutput::name)
        .contains(tenantNameA, tenantNameB);
  }

  @Test
  void should_update_tenant() {
    // -- ARRANGE --
    Tenant existing = getTenant("Tenant A");
    tenantComposer.forTenant(existing).persist();

    // -- ACT --
    String newTenantName = "Tenant B";
    TenantInput updateInput = new TenantInput(newTenantName, null);

    Tenant updated = tenantService.update(existing.getId(), updateInput);

    // -- ASSERT --
    assertThat(updated.getName()).isEqualTo(newTenantName);
  }

  @Test
  void should_soft_delete_tenant() {
    // -- ARRANGE --
    Tenant tenant = getTenant("Tenant A");
    Tenant created = tenantComposer.forTenant(tenant).persist().get();

    // -- ACT --
    Tenant softDeleted = tenantService.softDelete(created.getId());

    // -- ASSERT --
    assertThat(softDeleted.getDeletedAt()).isNotNull();
    assertThat(tenantRepository.findById(created.getId())).isPresent();
  }

  @Test
  void should_reactivate_soft_deleted_tenant() {
    // -- ARRANGE --
    Tenant tenant = getTenant("Tenant A");
    tenantComposer.forTenant(tenant).persist();
    tenantService.softDelete(tenant.getId());

    // -- ACT --
    Tenant reactivated = tenantService.reactivate(tenant.getId());

    // -- ASSERT --
    assertThat(reactivated.getDeletedAt()).isNull();
  }

  @Test
  void should_purge_expired_tenants() {
    // -- ARRANGE --
    Tenant tenantExpired = getTenant("Tenant Expired");
    tenantComposer.forTenant(tenantExpired).persist();
    tenantExpired.setDeletedAt(
        Instant.now().minus(SOFT_DELETE_RETENTION_DAYS + 1, ChronoUnit.DAYS));
    tenantRepository.save(tenantExpired);
    TenantContext.setCurrentTenant(tenantExpired.getId());

    Tenant tenantRecent = getTenant("Tenant Recent");
    tenantComposer.forTenant(tenantRecent).persist();
    tenantService.softDelete(tenantRecent.getId());

    // -- ACT --
    int purged = tenantService.purgeExpiredTenants();

    // -- ASSERT --
    assertThat(purged).isEqualTo(1);
    assertThat(tenantRepository.findById(tenantExpired.getId())).isEmpty();
    assertThat(tenantRepository.findById(tenantRecent.getId())).isPresent();

    // Verify no domain anymore for the deleted tenant
    Session session = entityManager.unwrap(Session.class);
    session.enableFilter("tenantFilter").setParameter("tenantId", tenantExpired.getId());
    assertThat(domainRepository.findAll()).isEmpty();
    // Verify datapack
    assertThat(vulnerabilityRepository.findAll()).isEmpty();
    assertThat(cweRepository.findAll()).isEmpty();
    assertThat(roleService.findAll()).isEmpty();
    assertThat(groupRepository.findAll()).isEmpty();
    // Verify collectors and collector types were cleaned up
    assertThat(collectorRepository.findAll()).isEmpty();
    assertThat(collectorTypeRepository.findAll()).isEmpty();
    // Verify executors and injectors were cleaned up
    assertThat(executorRepository.findAll()).isEmpty();
    assertThat(injectorRepository.findAll()).isEmpty();
  }

  @Test
  void should_fail_when_tenant_does_not_exist() {
    assertThatThrownBy(() -> tenantService.findById("unknown"))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void should_find_tenants_by_user_id() throws Exception {
    // -- ARRANGE --
    String userId = testUserHolder.get().getId();
    tenantService.create(getTenant("Tenant Alpha"));
    tenantService.create(getTenant("Tenant Beta"));
    // Tenant Gamma is NOT created by this user
    tenantComposer.forTenant(getTenant("Tenant Gamma")).persist();

    // -- ACT --
    List<Tenant> tenants = tenantService.findTenantsByUserId(userId);

    // -- ASSERT --
    assertThat(tenants).extracting(Tenant::getName).containsExactly("Tenant Alpha", "Tenant Beta");
  }

  @Test
  void should_return_empty_when_user_has_no_tenant() {
    // -- ARRANGE --
    String userId = testUserHolder.get().getId();

    // -- ACT --
    List<Tenant> tenants = tenantService.findTenantsByUserId(userId);

    // -- ASSERT --
    assertThat(tenants).isEmpty();
  }

  // -- TENANT DEPENDENCY LIFECYCLE --

  @Nested
  @DisplayName("createDependencyForTenant")
  class CreateDependencyForTenant {

    @Test
    @DisplayName("should copy executors to the new tenant")
    void should_copy_executors_to_new_tenant() throws DependenciesManagerException {
      // -- ARRANGE --
      Executor source = executorFixture.createDefaultExecutor("Test Executor");
      executorComposer.forExecutor(source).persist();

      Tenant newTenant = getTenant("New Tenant");
      tenantComposer.forTenant(newTenant).persist();

      // -- ACT --
      executorService.createDependencyForTenant(newTenant);
      entityManager.flush();

      // -- ASSERT --
      Session session = entityManager.unwrap(Session.class);
      session.enableFilter("tenantFilter").setParameter("tenantId", newTenant.getId());
      List<Executor> copiedExecutors = StreamHelper.fromIterable(executorRepository.findAll());
      assertThat(copiedExecutors).hasSize(1);
      assertThat(copiedExecutors).extracting(Executor::getName).containsExactly("Test Executor");
      // Verify the copy has a different ID than the source
      assertThat(copiedExecutors.getFirst().getId()).isNotEqualTo(source.getId());
    }

    @Test
    @DisplayName("should copy built-in injectors to the new tenant")
    void should_copy_builtin_injectors_to_new_tenant() throws DependenciesManagerException {
      // -- ARRANGE --
      Injector source =
          InjectorFixture.createInjector("test-injector-id", "Test Injector", "test_type");
      injectorRepository.save(source);

      Tenant newTenant = getTenant("New Tenant");
      tenantComposer.forTenant(newTenant).persist();

      // -- ACT --
      injectorService.createDependencyForTenant(newTenant);
      entityManager.flush();

      // -- ASSERT --
      Session session = entityManager.unwrap(Session.class);
      session.enableFilter("tenantFilter").setParameter("tenantId", newTenant.getId());
      List<Injector> copiedInjectors = StreamHelper.fromIterable(injectorRepository.findAll());
      assertThat(copiedInjectors).hasSize(1);
      assertThat(copiedInjectors).extracting(Injector::getName).containsExactly("Test Injector");
      assertThat(copiedInjectors).extracting(Injector::getType).containsExactly("test_type");
      assertThat(copiedInjectors.getFirst().getId()).isNotEqualTo(source.getId());
    }

    @Test
    @DisplayName("should not copy external injectors to the new tenant")
    void should_not_copy_external_injectors_to_new_tenant() throws DependenciesManagerException {
      // -- ARRANGE --
      Injector externalInjector =
          InjectorFixture.createInjector("ext-injector-id", "External Injector", "ext_type");
      externalInjector.setExternal(true);
      injectorRepository.save(externalInjector);

      Tenant newTenant = getTenant("New Tenant");
      tenantComposer.forTenant(newTenant).persist();

      // -- ACT --
      injectorService.createDependencyForTenant(newTenant);
      entityManager.flush();

      // -- ASSERT --
      Session session = entityManager.unwrap(Session.class);
      session.enableFilter("tenantFilter").setParameter("tenantId", newTenant.getId());
      List<Injector> copiedInjectors = StreamHelper.fromIterable(injectorRepository.findAll());
      assertThat(copiedInjectors).isEmpty();
    }

    @Test
    @DisplayName("should copy collector types and collectors to the new tenant")
    void should_copy_collector_types_and_collectors_to_new_tenant()
        throws DependenciesManagerException {
      // -- ARRANGE --
      Tenant newTenant = getTenant("New Tenant");
      tenantComposer.forTenant(newTenant).persist();

      // Built-in collectors exist in the default tenant (registered at startup)

      // -- ACT --
      collectorService.createDependencyForTenant(newTenant);
      entityManager.flush();

      // -- ASSERT --
      Session session = entityManager.unwrap(Session.class);
      session.enableFilter("tenantFilter").setParameter("tenantId", newTenant.getId());

      List<CollectorType> copiedTypes =
          StreamHelper.fromIterable(collectorTypeRepository.findAll());
      assertThat(copiedTypes).isNotEmpty();

      List<Collector> copiedCollectors = StreamHelper.fromIterable(collectorRepository.findAll());
      assertThat(copiedCollectors).isNotEmpty();
      assertThat(copiedCollectors).allMatch(c -> !c.isExternal());
    }

    @Test
    @DisplayName("should throw DependenciesManagerException when collector copy fails")
    void should_throw_when_collector_copy_fails() {
      // -- ARRANGE --
      // Tenant not persisted → FK violation when saving collector type copies
      Tenant unsavedTenant = new Tenant();
      unsavedTenant.setName("Ghost Tenant");

      // -- ACT & ASSERT --
      assertThatThrownBy(() -> collectorService.createDependencyForTenant(unsavedTenant))
          .isInstanceOf(DependenciesManagerException.class)
          .hasMessageContaining("Failed to create collectors");
    }

    @Test
    @DisplayName("should throw DependenciesManagerException when executor copy fails")
    void should_throw_when_executor_copy_fails() {
      // -- ARRANGE --
      Executor source = executorFixture.createDefaultExecutor("Executor");
      executorComposer.forExecutor(source).persist();

      Tenant unsavedTenant = new Tenant();
      unsavedTenant.setName("Ghost Tenant");

      // -- ACT & ASSERT --
      assertThatThrownBy(() -> executorService.createDependencyForTenant(unsavedTenant))
          .isInstanceOf(DependenciesManagerException.class)
          .hasMessageContaining("Failed to create executors");
    }

    @Test
    @DisplayName("should throw DependenciesManagerException when injector copy fails")
    void should_throw_when_injector_copy_fails() {
      // -- ARRANGE --
      Injector source =
          InjectorFixture.createInjector("fail-injector-id", "Fail Injector", "fail_type");
      injectorRepository.save(source);

      Tenant unsavedTenant = new Tenant();
      unsavedTenant.setName("Ghost Tenant");

      // -- ACT & ASSERT --
      assertThatThrownBy(() -> injectorService.createDependencyForTenant(unsavedTenant))
          .isInstanceOf(DependenciesManagerException.class)
          .hasMessageContaining("Failed to create injectors");
    }

    @Test
    @DisplayName("should link pre-existing contracts when copying injectors to the new tenant")
    void should_link_contracts_when_copying_injectors() throws DependenciesManagerException {
      // -- ARRANGE --
      // 1. Create an injector with a contract in the default tenant
      Injector sourceInjector =
          InjectorFixture.createInjector("link-injector-id", "Link Injector", "link_type");
      sourceInjector = injectorRepository.save(sourceInjector);

      InjectorContract sourceContract =
          injectorContractComposer
              .forInjectorContract(
                  io.openaev.utils.fixtures.InjectorContractFixture.createDefaultInjectorContract())
              .withInjector(sourceInjector)
              .persist()
              .get();

      // 2. Create the new tenant
      Tenant newTenant = getTenant("Linked Tenant");
      tenantComposer.forTenant(newTenant).persist();
      // Flush tenant first — InjectorContractId stores tenant_id as a plain @Column (not
      // @ManyToOne), so Hibernate cannot infer the FK ordering and may try to INSERT the
      // contract before the tenant.
      entityManager.flush();

      // 3. Simulate InjectorContractService having already copied the contract to the new tenant
      //    (InjectorContractService is a prerequisite of InjectorService)
      InjectorContract contractCopyForNewTenant = new InjectorContract();
      contractCopyForNewTenant.setId(sourceContract.getId());
      contractCopyForNewTenant.setTenant(newTenant);
      contractCopyForNewTenant.setContent(sourceContract.getContent());
      contractCopyForNewTenant.setConvertedContent(sourceContract.getConvertedContent());
      injectorContractRepository.save(contractCopyForNewTenant);
      entityManager.flush();

      // -- ACT --
      injectorService.createDependencyForTenant(newTenant);
      entityManager.flush();

      // -- ASSERT --
      Session session = entityManager.unwrap(Session.class);
      session.enableFilter("tenantFilter").setParameter("tenantId", newTenant.getId());

      List<Injector> copiedInjectors = StreamHelper.fromIterable(injectorRepository.findAll());
      assertThat(copiedInjectors).hasSize(1);
      Injector copiedInjector = copiedInjectors.getFirst();
      assertThat(copiedInjector.getName()).isEqualTo("Link Injector");
      // Verify the contract was linked to the copied injector
      assertThat(copiedInjector.getContracts()).hasSize(1);
      assertThat(copiedInjector.getContracts().iterator().next().getId())
          .isEqualTo(sourceContract.getId());
    }
  }

  @Nested
  @DisplayName("deleteDependencyForTenant")
  class DeleteDependencyForTenant {

    @Test
    @DisplayName("should delete all collectors and collector types for the tenant")
    void should_delete_collectors_for_tenant() throws DependenciesManagerException {
      // -- ARRANGE --
      Tenant tenant = getTenant("Tenant to clean");
      tenantComposer.forTenant(tenant).persist();
      collectorService.createDependencyForTenant(tenant);
      entityManager.flush();

      // -- ACT --
      collectorService.deleteDependencyForTenant(tenant.getId());
      entityManager.flush();
      entityManager.clear();

      // -- ASSERT --
      Session session = entityManager.unwrap(Session.class);
      session.enableFilter("tenantFilter").setParameter("tenantId", tenant.getId());
      assertThat(collectorRepository.findAll()).isEmpty();
      assertThat(collectorTypeRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("should delete all executors for the tenant")
    void should_delete_executors_for_tenant() throws DependenciesManagerException {
      // -- ARRANGE --
      Tenant tenant = getTenant("Tenant to clean");
      tenantComposer.forTenant(tenant).persist();
      executorService.createDependencyForTenant(tenant);
      entityManager.flush();

      // -- ACT --
      executorService.deleteDependencyForTenant(tenant.getId());
      entityManager.flush();
      entityManager.clear();

      // -- ASSERT --
      Session session = entityManager.unwrap(Session.class);
      session.enableFilter("tenantFilter").setParameter("tenantId", tenant.getId());
      assertThat(executorRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("should delete all injectors for the tenant")
    void should_delete_injectors_for_tenant() throws DependenciesManagerException {
      // -- ARRANGE --
      Tenant tenant = getTenant("Tenant to clean");
      tenantComposer.forTenant(tenant).persist();
      injectorService.createDependencyForTenant(tenant);
      entityManager.flush();

      // -- ACT --
      injectorService.deleteDependencyForTenant(tenant.getId());
      entityManager.flush();
      entityManager.clear();

      // -- ASSERT --
      Session session = entityManager.unwrap(Session.class);
      session.enableFilter("tenantFilter").setParameter("tenantId", tenant.getId());
      assertThat(injectorRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName(
        "should throw DependenciesManagerException when collector type delete fails due to FK constraint")
    void should_throw_when_collector_type_delete_fails() throws DependenciesManagerException {
      // -- ARRANGE --
      // Create a tenant with its own collector types (copied from default)
      Tenant tenant = getTenant("FK Tenant");
      tenantComposer.forTenant(tenant).persist();
      collectorService.createDependencyForTenant(tenant);
      entityManager.flush();

      // Pick one of the copied collector types and attach a DetectionRemediation to it.
      // The FK from detection_remediations → collector_types has no ON DELETE CASCADE,
      // so the native DELETE on collector_types will fail.
      Session session = entityManager.unwrap(Session.class);
      session.enableFilter("tenantFilter").setParameter("tenantId", tenant.getId());
      CollectorType copiedType =
          StreamHelper.fromIterable(collectorTypeRepository.findAll()).getFirst();

      Payload payload =
          payloadComposer.forPayload(PayloadFixture.createDefaultCommand()).persist().get();
      DetectionRemediation dr = DetectionRemediationFixture.createDefaultDetectionRemediation();
      dr.setPayload(payload);
      dr.setCollectorType(copiedType);
      detectionRemediationComposer.forDetectionRemediation(dr).persist();
      entityManager.flush();

      // -- ACT & ASSERT --
      assertThatThrownBy(() -> collectorService.deleteDependencyForTenant(tenant.getId()))
          .isInstanceOf(DependenciesManagerException.class)
          .hasMessageContaining("Failed to delete collectors");
    }
  }
}
