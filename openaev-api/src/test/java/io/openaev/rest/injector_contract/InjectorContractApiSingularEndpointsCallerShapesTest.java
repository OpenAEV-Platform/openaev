package io.openaev.rest.injector_contract;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;
import static io.openaev.rest.injector_contract.InjectorContractApi.INJECTOR_CONTRACT_URL;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.database.model.Tenant;
import io.openaev.injectors.manual.ManualContract;
import io.openaev.integration.impl.injectors.manual.ManualInjectorIntegrationFactory;
import io.openaev.rest.exception.TenantSingleScopeRequiredException;
import io.openaev.rest.injector_contract.form.InjectorContractUpdateInput;
import io.openaev.rest.injector_contract.form.InjectorContractUpdateMappingInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The five singular endpoints that resolve one contract by id (the contract GET, PUT, PUT mapping
 * and DELETE, and the threat arsenal GET) carry the same {@code @RequireTenantSelector} and {@code
 * requireOneTenantOfScope}. This pins what each does for every shape of caller that sends no tenant
 * header, plus a selector naming two tenants: the status, and which tenant's row is read or
 * written.
 *
 * <p>Every case works on a custom contract id cloned into each tenant of the caller, so the
 * built-in contract rows the other tests read are never touched, and a DELETE has something it is
 * allowed to delete.
 *
 * <p>Deliberately NOT {@code @Transactional}: the HTTP call must run in its own transaction with
 * its own tenant scope, the way a real request does.
 */
@DisplayName("Singular contract endpoints, the caller shapes with no tenant selector")
@WithMockUser(isAdmin = true)
class InjectorContractApiSingularEndpointsCallerShapesTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String SELECTOR_REQUIRED = "TENANT_SELECTOR_REQUIRED";
  private static final String UNTOUCHED_UPDATED_AT = "1970-01-01 00:00:00+00";

  /** The endpoints under test. Each one names how it is called and what a successful call does. */
  enum Endpoint {
    CONTRACT_GET(false) {
      @Override
      MockHttpServletRequestBuilder request(String contractId) {
        return get(INJECTOR_CONTRACT_URL + "/" + contractId);
      }
    },
    CONTRACT_PUT(true) {
      @Override
      MockHttpServletRequestBuilder request(String contractId) {
        InjectorContractUpdateInput input = new InjectorContractUpdateInput();
        input.setContent("{\"fields\":[]}");
        input.setLabels(Map.of("en", "written"));
        input.setDomains(Set.of());
        return put(INJECTOR_CONTRACT_URL + "/" + contractId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(input));
      }
    },
    CONTRACT_MAPPING_PUT(true) {
      @Override
      MockHttpServletRequestBuilder request(String contractId) {
        return put(INJECTOR_CONTRACT_URL + "/" + contractId + "/mapping")
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(new InjectorContractUpdateMappingInput()));
      }
    },
    CONTRACT_DELETE(true) {
      @Override
      MockHttpServletRequestBuilder request(String contractId) {
        return delete(INJECTOR_CONTRACT_URL + "/" + contractId);
      }
    },
    THREAT_ARSENAL_GET(false) {
      @Override
      MockHttpServletRequestBuilder request(String contractId) {
        return get(THREAT_ARSENAL_URL + "/" + contractId);
      }

      @Override
      String labelPath() {
        return "$.action_labels.en";
      }
    };

    final boolean writes;

    Endpoint(boolean writes) {
      this.writes = writes;
    }

    abstract MockHttpServletRequestBuilder request(String contractId);

    String labelPath() {
      return "$.injector_contract_labels.en";
    }
  }

  /** The tenants the caller belongs to, and what the request says about them. */
  enum Shape {
    ONE_NON_DEFAULT_TENANT(false, false, false, 200, null, "B"),
    DEFAULT_AND_ANOTHER(true, false, false, 200, null, "DEFAULT"),
    TWO_NON_DEFAULT_TENANTS(false, true, false, 400, SELECTOR_REQUIRED, null),
    NO_MEMBERSHIP(false, false, true, 400, TenantSingleScopeRequiredException.CODE, null),
    SELECTOR_NAMING_TWO_TENANTS(
        true, false, false, 400, TenantSingleScopeRequiredException.CODE, null);

    final boolean inDefault;
    final boolean inSecondNonDefault;
    final boolean inNone;
    final int status;
    final String errorCode;

    /** "B" for the caller's first created tenant, "DEFAULT" for the default one, null for none. */
    final String servedTenant;

    Shape(
        boolean inDefault,
        boolean inSecondNonDefault,
        boolean inNone,
        int status,
        String errorCode,
        String servedTenant) {
      this.inDefault = inDefault;
      this.inSecondNonDefault = inSecondNonDefault;
      this.inNone = inNone;
      this.status = status;
      this.errorCode = errorCode;
      this.servedTenant = servedTenant;
    }
  }

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
  @Autowired private ManualInjectorIntegrationFactory manualInjectorIntegrationFactory;

  private final List<String> createdTenants = new ArrayList<>();
  private String contractId;

  @AfterEach
  void cleanup() {
    if (contractId != null) {
      new JdbcTemplate(dataSource)
          .update("DELETE FROM injectors_contracts WHERE injector_contract_id = ?", contractId);
    }
    createdTenants.forEach(tenantHelper::deleteCommittedTenants);
    createdTenants.clear();
  }

  static Stream<Arguments> endpointsAndShapes() {
    return Stream.of(Endpoint.values())
        .flatMap(endpoint -> Stream.of(Shape.values()).map(shape -> Arguments.of(endpoint, shape)));
  }

  @ParameterizedTest(name = "{0} / {1}")
  @MethodSource("endpointsAndShapes")
  @DisplayName(
      "given_callerShapeAndNoTenantHeader_should_serveTheDocumentedTenantOrRefuseAndTouchNothing")
  void given_callerShapeAndNoTenantHeader_should_serveTheDocumentedTenantOrRefuseAndTouchNothing(
      Endpoint endpoint, Shape shape) throws Exception {
    // Arrange: the caller's tenants, each holding its own copy of one custom contract id.
    String tenantB = tenantWithTheBuiltinContract("ic-shape-b");
    Map<String, String> tenants = new LinkedHashMap<>();
    tenants.put("B", tenantB);
    if (shape.inSecondNonDefault) {
      tenants.put("C", tenantWithTheBuiltinContract("ic-shape-c"));
    }
    if (shape.inDefault) {
      tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
      builtinConnectorRegistration.register(
          manualInjectorIntegrationFactory, Tenant.DEFAULT_TENANT_UUID);
      tenants.put("DEFAULT", Tenant.DEFAULT_TENANT_UUID);
    }
    if (shape.inNone) {
      tenantHelper.detachCurrentUserFromTenant(tenantB);
    }
    contractId = "ic-shape-" + UUID.randomUUID();
    tenants.values().forEach(tenantId -> cloneContract(tenantId, contractId));

    MockHttpServletRequestBuilder request = endpoint.request(contractId).with(csrf());
    if (shape == Shape.SELECTOR_NAMING_TWO_TENANTS) {
      request.header("X-Tenant-Ids", tenantB + "," + Tenant.DEFAULT_TENANT_UUID);
    }

    // Act
    MvcResult result = mvc.perform(request).andReturn();

    // Assert
    assertThat(result.getResponse().getStatus()).isEqualTo(shape.status);
    Set<String> changed = changedTenants(tenants.values());
    if (shape.errorCode != null) {
      assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.message"))
          .isEqualTo(shape.errorCode);
      assertThat(changed).as("a refused call must not touch any row").isEmpty();
      return;
    }
    String served = tenants.get(shape.servedTenant);
    if (endpoint.writes) {
      assertThat(changed)
          .as("the call writes the row of %s and no other", shape.servedTenant)
          .containsExactly(served);
    } else {
      assertThat(changed).as("a read writes nothing").isEmpty();
      assertThat(
              (String)
                  JsonPath.read(result.getResponse().getContentAsString(), endpoint.labelPath()))
          .as("the response is the copy of %s", shape.servedTenant)
          .isEqualTo(labelOf(served));
    }
  }

  private String tenantWithTheBuiltinContract(String name) throws Exception {
    String tenantId = tenantHelper.createTenantWithCurrentUser(name).getId();
    createdTenants.add(tenantId);
    builtinConnectorRegistration.register(manualInjectorIntegrationFactory, tenantId);
    return tenantId;
  }

  private static String labelOf(String tenantId) {
    return "copy-of-" + tenantId;
  }

  /**
   * Copies the tenant's built-in manual contract under a new id, as a custom contract carrying a
   * label that names its tenant and an update date that no endpoint leaves untouched.
   */
  private void cloneContract(String tenantId, String newId) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    List<String> columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns"
                + " WHERE table_name = 'injectors_contracts' AND table_schema = current_schema()",
            String.class);
    Set<String> replaced =
        Set.of(
            "injector_contract_id",
            "injector_contract_external_id",
            "injector_contract_labels",
            "injector_contract_custom",
            "injector_contract_updated_at");
    String copied =
        columns.stream().filter(c -> !replaced.contains(c)).collect(Collectors.joining(", "));
    jdbc.update(
        "INSERT INTO injectors_contracts ("
            + copied
            + ", injector_contract_id, injector_contract_external_id, injector_contract_labels,"
            + " injector_contract_custom, injector_contract_updated_at) SELECT "
            + copied
            + ", ?, NULL, hstore('en', ?), true, TIMESTAMPTZ '"
            + UNTOUCHED_UPDATED_AT
            + "' FROM injectors_contracts WHERE injector_contract_id = ? AND tenant_id = ?",
        newId,
        labelOf(tenantId),
        ManualContract.MANUAL_DEFAULT,
        tenantId);
  }

  /** The tenants whose copy was written or deleted by the call. */
  private Set<String> changedTenants(Iterable<String> tenantIds) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Set<String> changed = new LinkedHashSet<>();
    for (String tenantId : tenantIds) {
      Integer untouched =
          jdbc.queryForObject(
              "SELECT count(*) FROM injectors_contracts WHERE injector_contract_id = ?"
                  + " AND tenant_id = ? AND injector_contract_updated_at = TIMESTAMPTZ '"
                  + UNTOUCHED_UPDATED_AT
                  + "' AND injector_contract_labels -> 'en' = ?",
              Integer.class,
              contractId,
              tenantId,
              labelOf(tenantId));
      if (untouched == null || untouched == 0) {
        changed.add(tenantId);
      }
    }
    return changed;
  }
}
