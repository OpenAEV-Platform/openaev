package io.openaev.rest.payload;

import static io.openaev.rest.payload.PayloadApi.PAYLOAD_URI;
import static io.openaev.rest.payload.PayloadApi.TENANT_PAYLOAD_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.TagLinkJdbc.column;
import static io.openaev.utils.TagLinkJdbc.saveTag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TagRepository;
import io.openaev.integration.ManagerFactory;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.rest.payload.form.PayloadCreateInput;
import io.openaev.rest.payload.form.PayloadUpdateInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.fixtures.PayloadInputFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The payload update links the payload to the tags of the payload's own tenant.
 *
 * <p>The payload lives in the default tenant, the one the ambient thread-local falls back to. On
 * the non-prefixed route {@code X-Tenant-Ids} can name several tenants, and a payload update
 * carries no tenant selector, so the request scope holds all of them. The tag ids of the body
 * resolve in the tenant of the payload alone. The payload keeps its tags on its injector contract,
 * read from {@code injector_contract_tags} through JDBC on the test connection, which the statement
 * inspector does not rewrite. The class arms the production list.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Payload update links only the tags of the payload tenant")
class PayloadTagLinkTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String CONTRACT_TAGS =
      "SELECT t.tag_id FROM injector_contract_tags t"
          + " JOIN injectors_contracts c ON c.injector_contract_id = t.injector_contract_id"
          + " WHERE c.injector_contract_payload = ?";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TagRepository tagRepository;
  @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;
  @Autowired private ManagerFactory managerFactory;

  private String otherTenant;
  private String tagInOther;
  private String tagInDefault;

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The caller belongs to both tenants, so the header can name both of them.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    // The payload of the default tenant needs its payload-supporting injector, which carries the
    // contract the tags are linked to.
    builtinConnectorRegistration.register(
        openaevInjectorIntegrationFactory, Tenant.DEFAULT_TENANT_UUID);
    managerFactory.getManager(Tenant.DEFAULT_TENANT_UUID).monitorIntegrations();
    otherTenant = tenantHelper.createTenantWithCurrentUser("payload-tag-link-o").getId();
    tagInOther = saveTag(tagRepository, otherTenant);
    tagInDefault = saveTag(tagRepository, Tenant.DEFAULT_TENANT_UUID);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Header route naming two tenants")
  class HeaderRouteNamingTwoTenants {

    @Test
    @DisplayName(
        "given a payload of the default tenant when the body names a tag of each tenant then only"
            + " the tag of the default tenant is linked")
    void given_payloadOfDefaultTenant_should_linkOnlyTheTagOfDefaultTenant() throws Exception {
      // -- Arrange --
      String payload = createPayloadInDefaultTenant();
      armRequest(payload);

      // -- Act --
      mvc.perform(
              put(PAYLOAD_URI + "/" + payload)
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateBody(List.of(tagInOther, tagInDefault))))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, CONTRACT_TAGS, payload))
          .as("tags linked to the payload: kept %s, dropped %s", tagInDefault, tagInOther)
          .containsExactly(tagInDefault);
    }
  }

  @Nested
  @DisplayName("Prefixed route")
  class PrefixedRoute {

    @Test
    @DisplayName(
        "given a payload of the default tenant when the body under its path names its tag then the"
            + " tag is linked")
    void given_payloadOfDefaultTenant_should_linkItsTagOnThePrefixedRoute() throws Exception {
      // -- Arrange --
      String payload = createPayloadInDefaultTenant();
      armRequest(payload);

      // -- Act --
      mvc.perform(
              put(TENANT_PAYLOAD_URI + "/" + payload, Tenant.DEFAULT_TENANT_UUID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateBody(List.of(tagInDefault))))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, CONTRACT_TAGS, payload)).containsExactly(tagInDefault);
    }
  }

  // -- Helpers --

  private PayloadUpdateInput updateBody(List<String> tags) {
    PayloadUpdateInput input = PayloadInputFixture.getDefaultCommandPayloadUpdateInput(List.of());
    input.setTagIds(tags);
    return input;
  }

  private String createPayloadInDefaultTenant() throws Exception {
    PayloadCreateInput input =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(List.of());
    String response =
        mvc.perform(
                post(TENANT_PAYLOAD_URI, Tenant.DEFAULT_TENANT_UUID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.payload_id");
  }

  /** The tags and the payload carry the tenant the scenario relies on, then the ambient reset. */
  private void armRequest(String payload) {
    assertThat(
            column(entityManager, "SELECT tenant_id FROM payloads WHERE payload_id = ?", payload))
        .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    assertThat(column(entityManager, "SELECT tenant_id FROM tags WHERE tag_id = ?", tagInOther))
        .containsExactly(otherTenant);
    assertThat(column(entityManager, "SELECT tenant_id FROM tags WHERE tag_id = ?", tagInDefault))
        .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }
}
