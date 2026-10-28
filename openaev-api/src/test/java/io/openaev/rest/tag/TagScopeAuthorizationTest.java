package io.openaev.rest.tag;

import static io.openaev.rest.exception.TenantScopeForbiddenException.MESSAGE;
import static io.openaev.rest.tag.TagApi.TAG_URI;
import static io.openaev.utils.fixtures.TagFixture.getTagWithText;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pins that a tag update or delete by id on the non-prefixed route requires the right in every
 * tenant of the request scope, when that scope holds several tenants: the caller's whole membership
 * when the request carries no selector, or every tenant the {@code X-Tenant-Ids} header names. The
 * caller is not an admin and is a member of the default tenant and of a tenant B, which owns the
 * tag.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser
@DisplayName("Tag update and delete by id are authorized in every tenant of the request scope")
class TagScopeAuthorizationTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;
  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";
  private static final String TAG_BY_ID_URI = TAG_URI + "/{tagId}";
  private static final String NAME = "scope-authorization-tag";
  private static final String UPDATED_NAME = "scope-authorization-tag-updated";
  private static final Set<Capability> WRITE_RIGHTS =
      Set.of(Capability.MANAGE_TAGS, Capability.DELETE_TAGS);
  private static final Set<Capability> READ_RIGHT = Set.of(Capability.ACCESS_TAGS);

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TagComposer tagComposer;

  enum Mutation {
    UPDATE,
    DELETE;

    MockHttpServletRequestBuilder request(String tagId) {
      return switch (this) {
        case UPDATE ->
            put(TAG_BY_ID_URI, tagId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tag_name\":\"" + UPDATED_NAME + "\",\"tag_color\":\"#000000\"}");
        case DELETE -> delete(TAG_BY_ID_URI, tagId);
      };
    }
  }

  @BeforeEach
  void resetComposers() {
    tagComposer.reset();
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Write rights in the default tenant, read only in tenant B")
  class WriteRightsInDefaultTenantOnly {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Mutation.class)
    @DisplayName("No header: the scope is every membership, so the change is refused")
    void given_noHeader_should_requireTheRightInEveryScopeTenant(Mutation mutation)
        throws Exception {
      // Arrange
      String tagId = seedTagInTenantB(READ_RIGHT);

      // Act
      MockHttpServletResponse response = perform(nonPrefixed(mutation.request(tagId)));

      // Assert
      assertRefusedInScopeAndUntouched(response, tagId);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Mutation.class)
    @DisplayName("A header naming both tenants refuses the change")
    void given_headerNamingEveryTenant_should_requireTheRightInEveryScopeTenant(Mutation mutation)
        throws Exception {
      // Arrange
      String tagId = seedTagInTenantB(READ_RIGHT);
      String tenantB = tenantOf(tagId);

      // Act
      MockHttpServletResponse response =
          perform(
              nonPrefixed(mutation.request(tagId))
                  .header(TENANT_IDS_HEADER, DEFAULT_TENANT + "," + tenantB));

      // Assert
      assertRefusedInScopeAndUntouched(response, tagId);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Mutation.class)
    @DisplayName("A header naming B refuses the change")
    void given_headerNamingTenantWithoutTheRight_should_refuseTheChange(Mutation mutation)
        throws Exception {
      // Arrange
      String tagId = seedTagInTenantB(READ_RIGHT);
      String tenantB = tenantOf(tagId);

      // Act
      MockHttpServletResponse response =
          perform(nonPrefixed(mutation.request(tagId)).header(TENANT_IDS_HEADER, tenantB));

      // Assert
      assertRefusedInScopeAndUntouched(response, tagId);
    }

    @ParameterizedTest(name = "{0}, tenant without the right sorts first: {1}")
    @MethodSource("io.openaev.rest.tag.TagScopeAuthorizationTest#mutationsInBothTenantOrders")
    @DisplayName("No header, a third tenant with the write rights: the change is still refused")
    void given_noHeaderAndTheRightMissingInOneOtherTenant_should_requireTheRightInEveryScopeTenant(
        Mutation mutation, boolean tenantWithoutTheRightSortsFirst) throws Exception {
      // Arrange
      String tagId = seedTagInTenantBNextToTenantC(tenantWithoutTheRightSortsFirst);

      // Act
      MockHttpServletResponse response = perform(nonPrefixed(mutation.request(tagId)));

      // Assert
      assertRefusedInScopeAndUntouched(response, tagId);
    }

    @Test
    @DisplayName("No header: a list needing only the read right, held in both tenants, is allowed")
    void given_noHeaderAndReadRightInEveryScopeTenant_should_listTags() throws Exception {
      // Arrange
      String tagId = seedTagInTenantB(READ_RIGHT);

      // Act
      MockHttpServletResponse response =
          mvc.perform(nonPrefixed(get(TAG_URI))).andReturn().getResponse();

      // Assert
      assertAll(
          () -> assertEquals(HttpStatus.OK.value(), response.getStatus(), "the list is allowed"),
          () ->
              assertTrue(response.getContentAsString().contains(tagId), "B's tag is in the scope"));
    }
  }

  @Nested
  @DisplayName("Write rights in both the default tenant and tenant B")
  class WriteRightsInEveryTenant {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Mutation.class)
    @DisplayName("No header: the change is applied")
    void given_noHeaderAndTheRightInEveryScopeTenant_should_applyTheChange(Mutation mutation)
        throws Exception {
      // Arrange
      String tagId = seedTagInTenantB(WRITE_RIGHTS);

      // Act
      int status = perform(nonPrefixed(mutation.request(tagId))).getStatus();

      // Assert
      assertApplied(mutation, status, tagId);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Mutation.class)
    @DisplayName("A header naming both tenants: the change is applied")
    void given_headerNamingEveryTenantWithTheRight_should_applyTheChange(Mutation mutation)
        throws Exception {
      // Arrange
      String tagId = seedTagInTenantB(WRITE_RIGHTS);
      String tenantB = tenantOf(tagId);

      // Act
      int status =
          perform(
                  nonPrefixed(mutation.request(tagId))
                      .header(TENANT_IDS_HEADER, DEFAULT_TENANT + "," + tenantB))
              .getStatus();

      // Assert
      assertApplied(mutation, status, tagId);
    }
  }

  @Nested
  @DisplayName("Write rights in tenant B only, read only in the default tenant")
  class WriteRightsInTenantBOnly {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Mutation.class)
    @DisplayName("No header: the ambient refusal keeps its own body")
    void given_noHeaderAndNoRightInAmbientTenant_should_refuseWithoutTheScopeRule(Mutation mutation)
        throws Exception {
      // Arrange
      String tagId = seedTagInTenantB(READ_RIGHT, WRITE_RIGHTS);

      // Act
      MockHttpServletResponse response = perform(nonPrefixed(mutation.request(tagId)));

      // Assert
      assertAll(
          () -> assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus(), "refused"),
          () -> assertNotNull(response.getErrorMessage(), "the ambient refusal reason"),
          () ->
              assertTrue(
                  response.getErrorMessage().startsWith("Access denied for user"),
                  "the ambient refusal reason"),
          () ->
              assertFalse(
                  response.getContentAsString().contains("TENANT_SCOPE_FORBIDDEN"),
                  "no scope rule in the body"),
          () -> assertEquals(NAME, storedName(tagId), "the tag is untouched"));
    }
  }

  /**
   * Every mutation with the tenant lacking the right on either side of the other one: the scope
   * lists its tenants in id order, so a check that stops at one of them misses the other in one of
   * the two orders.
   */
  static Stream<Arguments> mutationsInBothTenantOrders() {
    return Stream.of(Mutation.values())
        .flatMap(
            mutation -> Stream.of(Arguments.of(mutation, true), Arguments.of(mutation, false)));
  }

  /**
   * The caller holds the write rights in the default tenant and in a new tenant C, and only the
   * read right in a new tenant B, which owns the returned tag. B's id sorts before C's when {@code
   * tenantBSortsFirst}, after it otherwise.
   */
  private String seedTagInTenantBNextToTenantC(boolean tenantBSortsFirst) throws Exception {
    tenantHelper.grantCapabilitiesInTenant(DEFAULT_TENANT, WRITE_RIGHTS);
    List<String> tenants =
        Stream.of(
                tenantHelper.createTenantWithCurrentUser("tag-scope-x").getId(),
                tenantHelper.createTenantWithCurrentUser("tag-scope-y").getId())
            .sorted()
            .toList();
    String tenantB = tenantBSortsFirst ? tenants.get(0) : tenants.get(1);
    String tenantC = tenantBSortsFirst ? tenants.get(1) : tenants.get(0);
    tenantHelper.grantCapabilitiesInTenant(tenantB, READ_RIGHT);
    tenantHelper.grantCapabilitiesInTenant(tenantC, WRITE_RIGHTS);
    return persistTagIn(tenantB);
  }

  /**
   * The caller holds the write rights in the default tenant and {@code rightsInB} in a new tenant
   * B, which owns the returned tag.
   */
  private String seedTagInTenantB(Set<Capability> rightsInB) throws Exception {
    return seedTagInTenantB(WRITE_RIGHTS, rightsInB);
  }

  /**
   * The caller holds {@code rightsInDefault} in the default tenant and {@code rightsInB} in a new
   * tenant B, which owns the returned tag.
   */
  private String seedTagInTenantB(Set<Capability> rightsInDefault, Set<Capability> rightsInB)
      throws Exception {
    tenantHelper.grantCapabilitiesInTenant(DEFAULT_TENANT, rightsInDefault);
    String tenantB = tenantHelper.createTenantWithCapabilities("tag-scope-b", rightsInB).getId();
    return persistTagIn(tenantB);
  }

  private String persistTagIn(String tenantB) {
    Tag tag = getTagWithText(NAME);
    tag.setTenant(new Tenant(tenantB));
    String tagId = tagComposer.forTag(tag).persist().get().getId();
    entityManager.flush();
    return tagId;
  }

  private MockHttpServletRequestBuilder nonPrefixed(MockHttpServletRequestBuilder request) {
    // The non-prefixed route never sets the ambient tenant: reproduce production's empty
    // thread-local instead of inheriting what the fixtures left on the test thread.
    TenantContext.clearCurrentTenant();
    assertFalse(TenantContext.hasCurrentTenant(), "precondition: no ambient tenant");
    return request.with(csrf());
  }

  private MockHttpServletResponse perform(MockHttpServletRequestBuilder request) throws Exception {
    MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
    entityManager.flush();
    return response;
  }

  private void assertRefusedInScopeAndUntouched(MockHttpServletResponse response, String tagId) {
    assertAll(
        () -> assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus(), "refused"),
        () ->
            assertEquals(
                MESSAGE,
                JsonPath.read(response.getContentAsString(), "$.message"),
                "the body names the scope rule and its remedy"),
        () -> assertEquals(NAME, storedName(tagId), "the tag is untouched"));
  }

  private void assertApplied(Mutation mutation, int status, String tagId) {
    assertAll(
        () -> assertEquals(HttpStatus.OK.value(), status, "the change is accepted"),
        () -> {
          if (mutation == Mutation.DELETE) {
            assertNull(storedName(tagId), "the tag is deleted");
          } else {
            assertEquals(UPDATED_NAME, storedName(tagId), "the tag is renamed");
          }
        });
  }

  private String tenantOf(String tagId) {
    return readColumn("tenant_id", tagId);
  }

  private String storedName(String tagId) {
    return readColumn("tag_name", tagId);
  }

  /**
   * Reads on the current connection, past the statement inspector, so that rows of every tenant are
   * seen whatever scope the request left on the test transaction.
   */
  private String readColumn(String column, String tagId) {
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (var stmt =
                  connection.prepareStatement("SELECT " + column + " FROM tags WHERE tag_id = ?")) {
                stmt.setString(1, tagId);
                try (var rows = stmt.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }
}
