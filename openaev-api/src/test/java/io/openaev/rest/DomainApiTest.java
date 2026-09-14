package io.openaev.rest;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Domain;
import io.openaev.rest.domain.form.DomainBaseInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=domains")
@WithMockUser(isAdmin = true)
@DisplayName("Domain API tests")
class DomainApiTest extends IntegrationTest {

  private static final String TENANT_DOMAIN_UPSERT_URI =
      TENANT_PREFIX + "/domains/{domainId}/upsert";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  @Test
  @DisplayName("When domain does not exist, upsert creates and returns domain")
  void whenDomainDoesNotExist_upsertCreatesAndReturnsDomain() throws Exception {
    String tenantA = tenantHelper.createTenantWithCurrentUser("domain-api-test-create").getId();
    DomainBaseInput input = new DomainBaseInput();
    input.setName("domain");
    input.setColor("#012012");

    String response =
        mvc.perform(
                post(TENANT_DOMAIN_UPSERT_URI, tenantA, "random-id")
                    .header("X-Tenant-Ids", tenantA)
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Domain returnedDomain = mapper.readValue(response, Domain.class);

    Assertions.assertEquals("domain", returnedDomain.getName());
    Assertions.assertNotNull(returnedDomain.getColor());
    Assertions.assertTrue(returnedDomain.getColor().matches("#[0-9a-fA-F]{6}"));
  }

  @Test
  @DisplayName("When domain exists, upsert returns existing domain")
  void whenDomainExists_upsertReturnsExistingDomain() throws Exception {
    String tenantA = tenantHelper.createTenantWithCurrentUser("domain-api-test-existing").getId();
    String existingDomainId = seedDomain(tenantA, "existing", "#123456");

    DomainBaseInput input = new DomainBaseInput();
    input.setName("existing");
    input.setColor("#123456");

    String response =
        mvc.perform(
                post(TENANT_DOMAIN_UPSERT_URI, tenantA, "random-id")
                    .header("X-Tenant-Ids", tenantA)
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Domain returnedDomain = mapper.readValue(response, Domain.class);

    Assertions.assertEquals(existingDomainId, returnedDomain.getId());
    Assertions.assertEquals("existing", returnedDomain.getName());
    Assertions.assertEquals("#123456", returnedDomain.getColor());
  }

  private String seedDomain(String tenantId, String name, String color) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO domains (domain_id, domain_name, domain_color, tenant_id)"
                + " VALUES (?1, ?2, ?3, ?4)")
        .setParameter(1, id)
        .setParameter(2, name)
        .setParameter(3, color)
        .setParameter(4, tenantId)
        .executeUpdate();
    return id;
  }
}
