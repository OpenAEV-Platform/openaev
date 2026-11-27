package io.openaev.rest.payload;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.payload.PayloadApi.TENANT_PAYLOAD_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.TagLinkJdbc.column;
import static io.openaev.utils.TagLinkJdbc.saveTag;
import static io.openaev.utils.fixtures.payload_fixture.ContractOutputElementInputFixture.createDefaultContractOutputElementInputIPV6;
import static io.openaev.utils.fixtures.payload_fixture.OutputParserInputFixture.createDefaultOutputParseInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.integration.ManagerFactory;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.rest.payload.contract_output_element.ContractOutputElementInput;
import io.openaev.rest.payload.form.PayloadCreateInput;
import io.openaev.rest.payload.form.PayloadUpdateInput;
import io.openaev.rest.payload.output_parser.OutputParserInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.fixtures.PayloadInputFixture;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The payload, injector contract and attack pattern responses answer the tags of each output
 * element of the payload.
 *
 * <p>The tags of an output element are a lazy collection on a tenant table. Not {@code
 * Transactional}: each request runs and commits its own transaction, then open-in-view serializes
 * the response with no tenant scope, as in production, so a collection left unloaded by the handler
 * comes back empty. The payload is created through the API in a tenant of its own.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Payload responses answer the tags of the output elements")
class PayloadOutputElementTagsResponseTest extends IntegrationTest {

  private static final String TAGS_PATH = "$..contract_output_element_tags[*]";
  private static final String TENANT_INJECTOR_CONTRACT_URI = TENANT_PREFIX + "/injector_contracts";
  private static final String TENANT_ATTACK_PATTERN_URI = TENANT_PREFIX + "/attack_patterns";
  private static final String ELEMENTS_OF_PAYLOAD =
      "SELECT e.contract_output_element_id FROM contract_output_elements e"
          + " JOIN output_parsers p ON p.output_parser_id = e.contract_output_element_output_parser_id"
          + " WHERE p.output_parser_payload_id = ?";
  private static final String CONTRACT_OF_PAYLOAD =
      "SELECT injector_contract_id FROM injectors_contracts WHERE injector_contract_payload = ?";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TagRepository tagRepository;
  @Autowired private AttackPatternRepository attackPatternRepository;
  @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;
  @Autowired private ManagerFactory managerFactory;

  private String tenant;
  private String otherTenant;
  private String tagId;
  private String attackPatternId;
  private String payloadId;

  @BeforeEach
  void seedPayloadWithTaggedOutputElement() throws Exception {
    tenant = tenantHelper.createTenantWithCurrentUser("payload-output-tags").getId();
    // Created before the first request: tenant creation attaches the mock user, which a request
    // clears from the security context.
    otherTenant = tenantHelper.createTenantWithCurrentUser("payload-output-tags-o").getId();
    // A payload write synchronises its contract on the payload-supporting injector of its tenant.
    builtinConnectorRegistration.register(openaevInjectorIntegrationFactory, tenant);
    managerFactory.getManager(tenant).monitorIntegrations();
    tagId = inScope(tenant, () -> saveTag(tagRepository, tenant));
    attackPatternId =
        inScope(
            tenant,
            () ->
                attackPatternRepository
                    .save(AttackPatternFixture.createDefaultAttackPattern(tenant))
                    .getId());
    payloadId = createPayload();
  }

  @AfterEach
  void cleanup() {
    TenantContext.clearCurrentTenant();
    tenantHelper.deleteCommittedTenants(tenant, otherTenant);
  }

  @Nested
  @DisplayName("Reads")
  class Reads {

    @Test
    @DisplayName("given a payload with a tagged output element when read then the tag is answered")
    void given_payload_should_answerOutputElementTags() throws Exception {
      // -- Act --
      List<String> tags = tagsIn(get(TENANT_PAYLOAD_URI + "/{payloadId}", tenant, payloadId));

      // -- Assert --
      assertThat(tags).containsExactly(tagId);
    }

    @Test
    @DisplayName(
        "given a payload with a tagged output element when searched then the tag is answered")
    void given_payloadSearch_should_answerOutputElementTags() throws Exception {
      // -- Act --
      List<String> tags =
          tagsIn(
              post(TENANT_PAYLOAD_URI + "/search", tenant)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"page\":0,\"size\":10}"));

      // -- Assert --
      assertThat(tags).containsExactly(tagId);
    }

    @Test
    @DisplayName("given the contract of the payload when read then the element tag is answered")
    void given_contract_should_answerOutputElementTags() throws Exception {
      // -- Arrange --
      String contractId = single(CONTRACT_OF_PAYLOAD, payloadId);

      // -- Act --
      List<String> tags =
          tagsIn(get(TENANT_INJECTOR_CONTRACT_URI + "/{contractId}", tenant, contractId));

      // -- Assert --
      assertThat(tags).containsExactly(tagId);
    }

    @Test
    @DisplayName(
        "given the contract of the payload when listed by attack pattern then the element tag is"
            + " answered")
    void given_attackPatternContracts_should_answerOutputElementTags() throws Exception {
      // -- Act --
      List<String> tags =
          tagsIn(
              get(
                  TENANT_ATTACK_PATTERN_URI + "/{attackPatternId}/injector_contracts",
                  tenant,
                  attackPatternId));

      // -- Assert --
      assertThat(tags).containsExactly(tagId);
    }
  }

  @Nested
  @DisplayName("Writes")
  class Writes {

    @Test
    @DisplayName(
        "given an update that leaves the output parsers unchanged when answered then the element"
            + " tag is answered")
    void given_updateWithoutOutputParsers_should_answerOutputElementTags() throws Exception {
      // -- Arrange --
      PayloadUpdateInput input = PayloadInputFixture.getDefaultCommandPayloadUpdateInput(List.of());
      input.setAttackPatternsIds(List.of(attackPatternId));
      // Null leaves the stored output parsers as they are.
      input.setOutputParsers(null);

      // -- Act --
      List<String> tags =
          tagsIn(
              put(TENANT_PAYLOAD_URI + "/{payloadId}", tenant, payloadId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)));

      // -- Assert --
      assertThat(tags).containsExactly(tagId);
    }

    @Test
    @DisplayName(
        "given a duplicated payload when answered then the element tags match the rows of the"
            + " duplicate")
    void given_duplicate_should_answerTheElementTagsOfTheDuplicate() throws Exception {
      // -- Act --
      String body = bodyOf(post(TENANT_PAYLOAD_URI + "/{payloadId}/duplicate", tenant, payloadId));
      String duplicateId = JsonPath.read(body, "$.payload_id");

      // -- Assert --
      List<String> tags = JsonPath.read(body, TAGS_PATH);
      assertThat(tags)
          .containsExactlyInAnyOrderElementsOf(
              inScope(
                  tenant,
                  () ->
                      column(
                          entityManager,
                          "SELECT t.tag_id FROM contract_output_elements_tags t"
                              + " JOIN contract_output_elements e"
                              + " ON e.contract_output_element_id = t.contract_output_element_id"
                              + " JOIN output_parsers p"
                              + " ON p.output_parser_id = e.contract_output_element_output_parser_id"
                              + " WHERE p.output_parser_payload_id = ?",
                          duplicateId)));
    }
  }

  @Nested
  @DisplayName("Isolation")
  class Isolation {

    @Test
    @DisplayName(
        "given a tag of another tenant linked to the output element when read then only the tag of"
            + " the payload tenant is answered")
    void given_tagOfOtherTenantOnElement_should_answerOnlyTheTagOfThePayloadTenant()
        throws Exception {
      // -- Arrange --
      String otherTagId = inScope(otherTenant, () -> saveTag(tagRepository, otherTenant));
      String elementId = single(ELEMENTS_OF_PAYLOAD, payloadId);
      inScope(
          tenant,
          () ->
              entityManager
                  .createNativeQuery(
                      "INSERT INTO contract_output_elements_tags (contract_output_element_id,"
                          + " tag_id) VALUES (:element, :tag)")
                  .setParameter("element", elementId)
                  .setParameter("tag", otherTagId)
                  .executeUpdate());

      // -- Act --
      List<String> tags = tagsIn(get(TENANT_PAYLOAD_URI + "/{payloadId}", tenant, payloadId));

      // -- Assert --
      assertThat(tags).containsExactly(tagId).doesNotContain(otherTagId);
    }
  }

  // -- Helpers --

  private String createPayload() throws Exception {
    ContractOutputElementInput element = createDefaultContractOutputElementInputIPV6();
    element.setTagIds(List.of(tagId));
    OutputParserInput parser = createDefaultOutputParseInput();
    parser.setContractOutputElements(Set.of(element));
    PayloadCreateInput input =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(List.of());
    input.setAttackPatternsIds(List.of(attackPatternId));
    input.setOutputParsers(Set.of(parser));
    String body =
        bodyOf(
            post(TENANT_PAYLOAD_URI, tenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input)));
    return JsonPath.read(body, "$.payload_id");
  }

  private List<String> tagsIn(MockHttpServletRequestBuilder request) throws Exception {
    return JsonPath.read(bodyOf(request), TAGS_PATH);
  }

  private String bodyOf(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.with(csrf()))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String single(String sql, String parameter) {
    List<String> values = inScope(tenant, () -> column(entityManager, sql, parameter));
    assertThat(values).hasSize(1);
    return values.getFirst();
  }

  private <T> T inScope(String tenantId, Supplier<T> work) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenantId), work);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }
}
