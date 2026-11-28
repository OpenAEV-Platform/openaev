package io.openaev.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.ConnectorCompositeId;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.InjectorContractId;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName(
    "A connector or injector contract reference is resolved in the tenant its reader names")
class CompositeIdTenantResolutionTest extends IntegrationTest {

  @Autowired private EntityManager entityManager;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private MappingJackson2HttpMessageConverter requestBodyConverter;

  /** Two references whose entities carry an id that repeats across tenants. */
  static class References {
    @JsonProperty("injector")
    @JsonDeserialize(using = MonoIdDeserializerHelper.class)
    public Injector injector;

    @JsonProperty("injector_contract")
    @JsonDeserialize(using = MonoIdDeserializerHelper.class)
    public InjectorContract injectorContract;
  }

  /** A reader of one injector reference, so a refusal names that entity alone. */
  static class InjectorReference {
    @JsonProperty("injector")
    @JsonDeserialize(using = MonoIdDeserializerHelper.class)
    public Injector injector;
  }

  /** A reader of one injector contract reference, so a refusal names that entity alone. */
  static class InjectorContractReference {
    @JsonProperty("injector_contract")
    @JsonDeserialize(using = MonoIdDeserializerHelper.class)
    public InjectorContract injectorContract;
  }

  static Stream<Arguments> singleReferenceReaders() {
    return Stream.of(
        Arguments.of(InjectorReference.class, "injector", Injector.class),
        Arguments.of(InjectorContractReference.class, "injector_contract", InjectorContract.class));
  }

  private String defaultTenantId;
  private String tenantBId;
  private String sharedInjectorId;
  private String sharedContractId;
  private String json;

  @BeforeEach
  void seedTheSameIdsInTwoTenants() throws Exception {
    defaultTenantId = TenantContext.getCurrentTenant();
    tenantBId =
        tenantRepository
            .save(TenantFixture.getTenant("composite-id-b-" + UUID.randomUUID()))
            .getId();
    entityManager.flush();
    sharedInjectorId = UUID.randomUUID().toString();
    sharedContractId = UUID.randomUUID().toString();
    seedIn(defaultTenantId);
    seedIn(tenantBId);
    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenants(List.of(defaultTenantId, tenantBId)));
    entityManager.clear();
    json =
        """
        {"injector": "%s", "injector_contract": "%s"}
        """
            .formatted(sharedInjectorId, sharedContractId);
  }

  private void seedIn(String tenantId) throws Exception {
    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantId));
    Injector injector = InjectorFixture.createDefaultPayloadInjector();
    injector.setId(sharedInjectorId);
    injector.setName("injector of " + tenantId);
    injector.setTenantId(tenantId);
    Injector savedInjector = injectorRepository.save(injector);
    InjectorContract contract = InjectorContractFixture.createImplantInjectorContract();
    contract.setId(sharedContractId);
    contract.setLabels(Map.of("en", "contract of " + tenantId));
    contract.setTenant(new Tenant(tenantId));
    contract.addInjector(savedInjector);
    injectorContractRepository.save(contract);
    entityManager.flush();
  }

  private ObjectReader readerWithEntityManager(Class<?> type) {
    return new ObjectMapper()
        .setInjectableValues(
            new InjectableValues.Std().addValue(EntityManager.class, entityManager))
        .readerFor(type);
  }

  private Object identifierOf(Object reference) {
    return entityManager
        .getEntityManagerFactory()
        .getPersistenceUnitUtil()
        .getIdentifier(reference);
  }

  @Nested
  @DisplayName("A reader off a request")
  class OffRequest {

    @Test
    @DisplayName("given the attribute naming tenant B should link the rows of B")
    void given_theAttributeNamingTenantB_should_linkTheRowsOfB() throws Exception {
      // ARRANGE: the ambient tenant is the default one, the reader names B
      TenantContext.setCurrentTenant(defaultTenantId);
      ObjectReader reader =
          readerWithEntityManager(References.class)
              .withAttribute(CompositeIdResolvableI.TENANT_ATTRIBUTE, tenantBId);

      // ACT
      References references = reader.readValue(json);

      // ASSERT
      assertThat(identifierOf(references.injector))
          .isEqualTo(new ConnectorCompositeId(sharedInjectorId, tenantBId));
      InjectorContractId expectedContractId = new InjectorContractId();
      expectedContractId.setId(sharedContractId);
      expectedContractId.setTenantId(tenantBId);
      assertThat(identifierOf(references.injectorContract)).isEqualTo(expectedContractId);
      Hibernate.initialize(references.injector);
      Hibernate.initialize(references.injectorContract);
      assertThat(references.injector.getName()).isEqualTo("injector of " + tenantBId);
      assertThat(references.injectorContract.getLabels())
          .containsEntry("en", "contract of " + tenantBId);
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("io.openaev.helper.CompositeIdTenantResolutionTest#singleReferenceReaders")
    @DisplayName("given no attribute should refuse the read naming the entity")
    void given_noAttribute_should_refuseTheReadNamingTheEntity(
        Class<?> readerType, String field, Class<?> entityType) {
      // ARRANGE: an ambient tenant is set, and a reader of one reference names none
      TenantContext.setCurrentTenant(defaultTenantId);
      ObjectReader reader = readerWithEntityManager(readerType);
      String referenceJson =
          """
          {"%s": "%s"}
          """
              .formatted(field, entityType == Injector.class ? sharedInjectorId : sharedContractId);

      // ACT & ASSERT
      assertThatThrownBy(() -> reader.readValue(referenceJson))
          .rootCause()
          .isInstanceOf(IllegalStateException.class)
          .hasMessageEndingWith(" " + entityType.getSimpleName());
    }
  }

  @Nested
  @DisplayName("A request body")
  class RequestBody {

    @Test
    @DisplayName("given the request body converter should hold no entity manager to resolve ids")
    void given_theRequestBodyConverter_should_holdNoEntityManagerToResolveIds() {
      // ARRANGE
      ObjectMapper requestBodyMapper = requestBodyConverter.getObjectMapper();

      // ACT
      InjectableValues injectableValues = requestBodyMapper.getInjectableValues();

      // ASSERT: no request body resolves a reference to a row, so none derives a tenant
      assertThat(injectableValues).isNull();
    }
  }
}
