package io.openaev.service.tenants;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
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
import io.openaev.database.model.Group;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.*;
import io.openaev.injectors.email.EmailContract;
import io.openaev.integration.impl.injectors.email.EmailInjectorIntegrationFactory;
import io.openaev.processor.datapack.V20260330_Default_tenant_data;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.TenantRoleService;
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
  @Autowired private TenantRoleService tenantRoleService;
  @Autowired private GroupRepository groupRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private EmailInjectorIntegrationFactory emailInjectorIntegrationFactory;
  @Autowired private V20260330_Default_tenant_data datapack;

  @Test
  void should_create_and_find_tenant() throws Exception {
    // -- ARRANGE --
    Tenant tenant = getTenant();

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

    // Verify the 10 domains from PresetDomain are created for this tenant
    Session session = entityManager.unwrap(Session.class);
    session.enableFilter("tenantFilter").setParameter("tenantId", created.getId());
    assertThat(domainRepository.findAll()).hasSize(10);
    // Verify datapack
    assertThat(vulnerabilityRepository.findAll()).hasSize(7);
    // cwes is on v2 isolation (no v1 @Filter anymore): assert by explicit tenant attribution.
    assertThat(cweRepository.findAll())
        .filteredOn(cwe -> created.getId().equals(cwe.getTenant().getId()))
        .hasSize(7);
    List<Role> roles = tenantRoleService.findAll(created.getId());
    assertThat(roles).extracting(Role::getName).contains("Admin", "Manager", "Observer");
    assertThat(roles).hasSizeGreaterThanOrEqualTo(3);
    List<Group> groups = groupRepository.findAllByTenantId(created.getId());
    assertThat(groups).extracting(Group::getName).contains("Admin", "Manager", "Observer");
    assertThat(groups).hasSizeGreaterThanOrEqualTo(3);
    assertThat(
            groups.stream()
                .filter(group -> group.getName().equals("Admin"))
                .findFirst()
                .get()
                .getUsers())
        .hasSize(1);

    Tenant exists = tenantService.findById(created.getId());
    assertThat(exists.getName()).isEqualTo(TENANT_NAME);
  }

  @Test
  void should_provision_and_link_builtin_injector_contracts_for_new_tenant() throws Exception {
    // A new tenant provisions its built-in connectors in two steps: the default injector contracts
    // are copied first, then the built-in injectors register and create the join rows.
    // PRIMARY guard: create() must complete without the composite-key foreign-key violation on
    // injectors_injector_contracts that the broken copy step used to raise (proven by reverting the
    // fix -> this test fails at create()). SECONDARY guards below: the new tenant ends up with the
    // email injector linked to its default contract, and the default tenant is left untouched (the
    // copy must never mutate or steal the source tenant's links).

    // Precondition: the default tenant must already carry a built-in contract WITH a join row, so
    // the copy step has a contract-with-link to copy. Without this the copy step is a no-op and the
    // foreign-key path is never exercised.
    emailInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());
    entityManager.flush();
    entityManager.clear();

    Tenant tenant = getTenant("Tenant Connectors");

    // -- ACT --
    Tenant created = tenantService.create(tenant);

    // -- ASSERT --
    entityManager.flush();
    entityManager.clear();
    TenantContext.setCurrentTenant(created.getId());
    Session session = entityManager.unwrap(Session.class);
    session.enableFilter("tenantFilter").setParameter("tenantId", created.getId());

    Injector emailInjector =
        injectorRepository
            .findByTypeAndTenantId(EmailContract.TYPE, created.getId())
            .orElseThrow(
                () -> new AssertionError("the email injector was not provisioned for the tenant"));
    assertThat(emailInjector.getContracts())
        .as("the email injector must be linked to its default contract in the new tenant")
        .extracting(InjectorContract::getId)
        .contains(EmailContract.EMAIL_DEFAULT);

    // The default tenant must be left untouched by the copy: its own email injector is still linked
    // to EMAIL_DEFAULT (the broken re-tenant/clear variants stole or deleted the source link).
    entityManager.clear();
    TenantContext.setCurrentTenant(DEFAULT_TENANT_UUID);
    session = entityManager.unwrap(Session.class);
    session.enableFilter("tenantFilter").setParameter("tenantId", DEFAULT_TENANT_UUID);
    Injector defaultEmailInjector =
        injectorRepository
            .findByTypeAndTenantId(EmailContract.TYPE, DEFAULT_TENANT_UUID)
            .orElseThrow(() -> new AssertionError("the default tenant lost its email injector"));
    assertThat(defaultEmailInjector.getContracts())
        .as("the default tenant's link must be untouched by the new-tenant copy")
        .extracting(InjectorContract::getId)
        .contains(EmailContract.EMAIL_DEFAULT);
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
    assertThatThrownBy(() -> tenantService.update(tenantB.getId(), duplicateNameInput))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Tenant name already used");
  }

  @Test
  void should_update_tenant_to_same_name_without_error() {
    // -- ARRANGE --
    Tenant existing = getTenant("Tenant Same");
    tenantComposer.forTenant(existing).persist();

    // -- ACT --
    TenantInput sameNameInput = new TenantInput("Tenant Same", null);
    Tenant updated = tenantService.update(existing.getId(), sameNameInput);

    // -- ASSERT --
    assertThat(updated.getName()).isEqualTo("Tenant Same");
  }

  @Test
  void should_update_default_tenant_to_unique_name() {
    // -- ACT --
    TenantInput input = new TenantInput("Renamed Default", null);
    Tenant updated = tenantService.update(DEFAULT_TENANT_UUID, input);

    // -- ASSERT --
    assertThat(updated.getName()).isEqualTo("Renamed Default");
  }

  @Test
  void should_fail_when_updating_default_tenant_to_existing_name() {
    // -- ARRANGE --
    Tenant other = getTenant("Existing Name");
    tenantComposer.forTenant(other).persist();

    TenantInput input = new TenantInput("Existing Name", null);

    // -- ACT & ASSERT --
    assertThatThrownBy(() -> tenantService.update(DEFAULT_TENANT_UUID, input))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Tenant name already used");
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
  void should_fail_when_soft_deleting_default_tenant() {
    // -- ACT & ASSERT --
    assertThatThrownBy(() -> tenantService.softDelete(DEFAULT_TENANT_UUID))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Default tenant cannot be deleted");
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
    // cwes is on v2 isolation (no v1 @Filter anymore): assert by explicit tenant attribution.
    assertThat(cweRepository.findAll())
        .filteredOn(cwe -> tenantExpired.getId().equals(cwe.getTenant().getId()))
        .isEmpty();
    assertThat(tenantRoleService.findAll(tenantExpired.getId())).isEmpty();
    assertThat(groupRepository.findAllByTenantId(tenantExpired.getId())).isEmpty();
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
    entityManager.flush();
    entityManager.clear();
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
}
