package io.openaev.config;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.rest.mapper.form.ImportMapperUpdateInput;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A caller that belongs to no tenant is a real production state: a user created with {@code
 * UserCreationScope.PLATFORM} never gets a {@code users_tenants} row, and detaching a user from its
 * last tenant leaves it in the same place. Such a caller resolves an empty scope on the
 * non-prefixed route, which {@code TenantScopeTransactionAspect} serializes as an empty {@code
 * app.current_tenants}, so {@code can_access_tenant} denies every row of an active table: the read
 * sees nothing and the write matches nothing.
 *
 * <p>This class pins that state deliberately, which is the point of it existing. Most of the suite
 * reaches the same state by accident, because {@code @WithMockUser} does not grant a membership
 * unless {@code autoJoinDefaultTenant = true}. That is a test-fixture default, not a contract, and
 * a change to it would silently take the coverage away everywhere. So this test does not rely on
 * it: it deletes whatever memberships the caller has, evicts the membership cache and asserts the
 * empty membership set as a precondition. Flipping the fixture default leaves it red-sensitive.
 *
 * <p>The row is seeded in the <b>default</b> tenant on purpose. A caller with no membership and no
 * selector could plausibly regress to the platform-wide "no explicit tenant context means the
 * default tenant" fallback, and that is also exactly what granting the fixture's default-tenant
 * membership would produce. Seeded anywhere else, the read would answer 404 under both the correct
 * and the regressed behaviour and the test would prove nothing.
 *
 * <p>The class is NOT {@code @Transactional}: the row must be committed and out of any live
 * persistence context, or the request's {@code findById} is a first-level-cache hit that never
 * queries the database and the endpoint answers 200 whatever the scope is. Ground truth goes
 * through raw JDBC, which the Hibernate statement inspector never rewrites, so a scope leaked into
 * the connection cannot mask it. {@code @TestPropertySource} arms {@code import_mappers} for this
 * class only; the test classpath declares no active table, so without it the inspector is inert.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=import_mappers")
@WithMockUser(isAdmin = true)
@DisplayName("A caller with no tenant membership reads and writes nothing on an active table")
class CallerWithoutTenantMembershipTest extends IntegrationTest {

  private static final String MAPPER_BY_ID = "/api/mappers/{mapperId}";
  private static final String MAPPER_SEARCH = "/api/mappers/search";
  private static final String SEEDED_NAME = "no-membership-seed";

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private TenantMembershipCacheManager membershipCache;

  private JdbcTemplate jdbc;
  private String callerId;
  private String mapperId;

  @BeforeEach
  void stripTheCallerMembershipsAndSeedADefaultTenantRow() {
    // Arrange: the caller belongs to no tenant, whatever the fixture default does.
    jdbc = new JdbcTemplate(dataSource);
    callerId = testUserHolder.get().getId();
    List<String> granted =
        jdbc.queryForList(
            "SELECT tenant_id FROM users_tenants WHERE user_id = ?", String.class, callerId);
    jdbc.update("DELETE FROM users_tenants WHERE user_id = ?", callerId);
    membershipCache.evictForUser(callerId, granted);
    assertTrue(
        membershipCache.findTenantIdsByUserId(callerId).isEmpty(),
        "precondition: the caller must belong to no tenant, else this test proves nothing");

    // The ambient v1 tenant is set by other fixtures and never cleared before a request, so the
    // non-prefixed route would run with whatever was left behind instead of production's empty one.
    TenantContext.clearCurrentTenant();

    mapperId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO import_mappers (mapper_id, mapper_name, mapper_inject_type_column, tenant_id)"
            + " VALUES (CAST(? AS uuid), ?, ?, CAST(? AS uuid))",
        mapperId,
        SEEDED_NAME,
        "A",
        Tenant.DEFAULT_TENANT_UUID);
  }

  @AfterEach
  void removeTheSeededRow() {
    jdbc.update("DELETE FROM import_mappers WHERE mapper_id = CAST(? AS uuid)", mapperId);
    TenantContext.clearCurrentTenant();
  }

  private String seededName() {
    return jdbc.queryForObject(
        "SELECT mapper_name FROM import_mappers WHERE mapper_id = CAST(? AS uuid)",
        String.class,
        mapperId);
  }

  @Nested
  @DisplayName("Reads see nothing although the row exists")
  class Reads {

    @Test
    @DisplayName("given a caller with no tenant membership should return 404 on the by-id read")
    void given_aCallerWithNoTenantMembership_should_return404OnTheByIdRead() throws Exception {
      // Act & Assert
      mvc.perform(get(MAPPER_BY_ID, mapperId)).andExpect(status().isNotFound());

      // Assert: the row really exists in the default tenant, it is only out of scope.
      assertEquals(
          SEEDED_NAME, seededName(), "the row must exist; the read is refused, not missing");
    }

    @Test
    @DisplayName("given a caller with no tenant membership should return no row from the search")
    void given_aCallerWithNoTenantMembership_should_returnNoRowFromTheSearch() throws Exception {
      // Arrange
      String body = asJsonString(PaginationFixture.getDefault().textSearch("").build());

      // Act
      String response =
          mvc.perform(
                  post(MAPPER_SEARCH)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(body)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertFalse(
          response.contains(mapperId), "a caller with no membership must not list the seeded row");
    }
  }

  @Nested
  @DisplayName("The by-id write matches nothing and leaves the row unchanged")
  class Writes {

    @Test
    @DisplayName("given a caller with no tenant membership should return 404 on the by-id update")
    void given_aCallerWithNoTenantMembership_should_return404OnTheByIdUpdate() throws Exception {
      // Arrange
      ImportMapperUpdateInput input = new ImportMapperUpdateInput();
      input.setName("renamed-by-a-member-less-caller");
      input.setInjectTypeColumn("A");

      // Act
      mvc.perform(
              put(MAPPER_BY_ID, mapperId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertEquals(SEEDED_NAME, seededName(), "a refused update must leave the row unchanged");
    }
  }
}
