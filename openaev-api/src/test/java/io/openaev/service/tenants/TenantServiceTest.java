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
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.*;
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

  @Test
  void should_create_and_find_tenant() throws Exception {
    // -- ARRANGE --
    Tenant tenant = getTenant();

    // -- ACT --
    Tenant created = tenantService.create(tenant);

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
}
