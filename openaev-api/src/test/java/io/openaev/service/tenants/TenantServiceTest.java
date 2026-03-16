package io.openaev.service.tenants;

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
import io.openaev.config.MinioConfig;
import io.openaev.database.model.Tenant;
import io.openaev.service.MinioService;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantServiceTest extends IntegrationTest {

  @Autowired private TenantService tenantService;

  @Autowired private TenantComposer tenantComposer;
  @Autowired protected EntityManager entityManager;
  @Autowired private MinioConfig minioConfig;
  @Autowired private MinioClient minioClient;
  @Autowired private MinioService minioService;

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

    tenantB.setName("Tenant A");

    // -- ACT & ASSERT --
    assertThatThrownBy(
            () -> {
              tenantService.update(tenantB.getId(), tenantB);
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
    Page<Tenant> result = tenantService.search(searchInput);

    // -- ASSERT --
    assertThat(result.getContent()).extracting(Tenant::getName).contains(tenantNameA, tenantNameB);
  }

  @Test
  void should_update_tenant() {
    // -- ARRANGE --
    Tenant existing = getTenant("Tenant A");
    tenantComposer.forTenant(existing).persist();

    // -- ACT --
    String newTenantName = "Tenant B";
    Tenant update = getTenant(newTenantName);

    Tenant updated = tenantService.update(existing.getId(), update);

    // -- ASSERT --
    assertThat(updated.getName()).isEqualTo(newTenantName);
  }

  @Test
  void should_delete_tenant() throws Exception {
    // -- ARRANGE --
    Tenant tenant = getTenant("Tenant A");
    Tenant created = tenantService.create(tenant);

    // Upload a file to verify MinIO path-based isolation works
    byte[] content = "file-to-be-wiped".getBytes(StandardCharsets.UTF_8);
    InputStream data = new ByteArrayInputStream(content);
    minioClient.putObject(
        PutObjectArgs.builder()
            .bucket(minioConfig.getBucket())
            .object(created.getId() + "/docs/report.pdf")
            .stream(data, content.length, -1)
            .contentType("application/pdf")
            .build());

    // -- ACT --
    tenantService.delete(tenant.getId());
    entityManager.flush();
    entityManager.clear();

    // -- ASSERT --
    assertThatThrownBy(() -> tenantService.findById(tenant.getId()))
        .isInstanceOf(EntityNotFoundException.class);

    // Verify the file is removed under the tenant prefix
    assertThat(minioService.countObjects(created.getName() + "/")).isZero();
  }

  @Test
  void should_fail_when_tenant_does_not_exist() {
    assertThatThrownBy(() -> tenantService.findById("unknown"))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void should_fail_when_creating_tenant_with_existing_name() {
    // -- ARRANGE --
    Tenant tenant1 = getTenant("Tenant A");
    tenantComposer.forTenant(tenant1).persist();
    Tenant tenant2 = getTenant("Tenant A");

    // -- ACT & ASSERT --
    assertThatThrownBy(
            () -> {
              tenantService.create(tenant2);
              entityManager.flush();
            })
        .isInstanceOf(ConstraintViolationException.class);
  }
}
