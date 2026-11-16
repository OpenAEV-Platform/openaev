package io.openaev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.ImportMapper;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ImportMapperRepository;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MapperServiceExportTest extends IntegrationTest {

  @Autowired private ImportMapperRepository importMapperRepository;

  @Autowired private MapperService mapperService;

  @Autowired private TenantScopedTransaction tenantTx;

  @DisplayName("Test exporting a mapper")
  @Test
  void exportMapper() throws Exception {
    // -- PREPARE --
    ImportMapper mapper = new ImportMapper();
    mapper.setName("Test Mapper");
    mapper.setInjectTypeColumn("injectType");
    mapper.setInjectImporters(new HashSet<>());
    mapper.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    // The export runs under the caller's tenant scope, as it does behind the API.
    TxCtx defaultTenant = TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID);
    ImportMapper mapperSaved =
        tenantTx.execute(defaultTenant, () -> this.importMapperRepository.save(mapper));

    // -- EXECUTE --
    String json =
        tenantTx.execute(
            defaultTenant,
            () -> {
              try {
                return this.mapperService.exportMappers(List.of(mapperSaved.getId()));
              } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
              }
            });

    // -- ASSERT --
    assertEquals(
        "[{"
            + "\"import_mapper_name\":\"Test Mapper\","
            + "\"import_mapper_inject_type_column\":\"injectType\","
            + "\"import_mapper_inject_importers\":[]"
            + "}]",
        json);

    // -- CLEAN --
    tenantTx.execute(defaultTenant, () -> this.importMapperRepository.delete(mapperSaved));
  }
}
