package io.openaev.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Base;
import io.openaev.database.model.Domain;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.DomainRepository;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.domain.enums.PresetDomain;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("V1 data importer - legacy domain names")
class V1_DataImporterLegacyDomainTest {

  private static final String TENANT_ID = "tenant-under-test";
  private static final String SOURCE_DOMAIN_ID = "source-instance-domain-id";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final DomainRepository domainRepository = mock(DomainRepository.class);
  @Spy private DomainService domainService = new DomainService(domainRepository);
  @InjectMocks private V1_DataImporter importer;

  @BeforeEach
  void setTenant() {
    TenantContext.setCurrentTenant(TENANT_ID);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  private static Domain tenantDomain(String name, String color) {
    return Domain.builder()
        .id(UUID.randomUUID().toString())
        .name(name)
        .color(color)
        .tenant(new Tenant(TENANT_ID))
        .build();
  }

  private JsonNode contractWithDomain(String name) throws Exception {
    return objectMapper.readTree(
        """
        {"injector_contract_domains": [
          {"domain_id": "%s", "domain_name": "%s", "domain_color": "#FFCC33"}
        ]}
        """
            .formatted(SOURCE_DOMAIN_ID, name));
  }

  @Test
  @DisplayName("given_legacyTableTopSpelling_should_linkTheTabletopPresetWithoutCreatingADomain")
  void given_legacyTableTopSpelling_should_linkTheTabletopPresetWithoutCreatingADomain()
      throws Exception {
    // Arrange
    Domain tabletop =
        tenantDomain(PresetDomain.getTabletop().getName(), PresetDomain.getTabletop().getColor());
    when(domainRepository.findByTenantId(TENANT_ID))
        .thenReturn(List.of(tenantDomain("Cloud", "#9999CC"), tabletop));
    Map<String, Base> baseIds = new HashMap<>();

    // Act
    List<Domain> imported =
        ReflectionTestUtils.invokeMethod(
            importer,
            "importDomains",
            contractWithDomain("Table-Top"),
            "injector_contract_",
            baseIds);

    // Assert
    assertThat(imported).containsExactly(tabletop);
    assertThat(imported.getFirst()).isSameAs(tabletop);
    assertThat(baseIds).containsEntry(SOURCE_DOMAIN_ID, tabletop);
    verify(domainRepository, never()).save(any());
  }

  @Test
  @DisplayName("given_domainUnknownToTheTenant_should_stillCreateIt")
  void given_domainUnknownToTheTenant_should_stillCreateIt() throws Exception {
    // Arrange
    when(domainRepository.findByTenantId(TENANT_ID))
        .thenReturn(
            List.of(
                tenantDomain(
                    PresetDomain.getTabletop().getName(), PresetDomain.getTabletop().getColor())));
    when(domainRepository.save(any(Domain.class))).thenAnswer(call -> call.getArgument(0));
    Map<String, Base> baseIds = new HashMap<>();

    // Act
    List<Domain> imported =
        ReflectionTestUtils.invokeMethod(
            importer,
            "importDomains",
            contractWithDomain("Satellite Ops"),
            "injector_contract_",
            baseIds);

    // Assert
    assertThat(imported)
        .singleElement()
        .satisfies(
            created -> {
              assertThat(created.getName()).isEqualTo("Satellite Ops");
              assertThat(created.getColor()).isEqualTo("#FFCC33");
              assertThat(created.getTenant().getId()).isEqualTo(TENANT_ID);
            });
    verify(domainRepository).save(any(Domain.class));
  }
}
