package io.openaev.rest;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Domain;
import io.openaev.database.model.Tenant;
import io.openaev.rest.domain.form.DomainBaseInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.DomainFixture;
import io.openaev.utils.fixtures.composers.DomainComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Domain API tests")
public class DomainApiTest extends IntegrationTest {

  @Autowired private DomainComposer domainComposer;
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void beforeEach() {
    domainComposer.reset();
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("When domain does not exist, upsert creates and returns domain")
  public void whenDomainDoesNotExist_upsertCreatesAndReturnsDomain() throws Exception {
    DomainBaseInput input = new DomainBaseInput();
    input.setName("domain");
    input.setColor("#012012");

    String response =
        mvc.perform(
                post("/api/domains/{domainId}/upsert", "random-id")
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
  @WithMockUser(isAdmin = true)
  @DisplayName("When domain exists, upsert returns existing domain")
  public void whenDomainExists_upsertReturnsExistingDomain() throws Exception {
    Domain existingDomain =
        domainComposer
            .forDomain(DomainFixture.getDomainWithNameAndColour("existing", "#123456"))
            .persist()
            .get();

    DomainBaseInput input = new DomainBaseInput();
    input.setName("existing");
    input.setColor("#123456");

    String response =
        mvc.perform(
                post("/api/domains/{domainId}/upsert", "random-id")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Domain returnedDomain = mapper.readValue(response, Domain.class);

    Assertions.assertEquals(existingDomain.getName(), returnedDomain.getName());
    Assertions.assertEquals(existingDomain.getColor(), returnedDomain.getColor());
  }

  // -- TENANT ISOLATION TESTS --

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser(isAdmin = true)
  class TenantIsolation {

    private String createDomainInTenant(String tenantId, String name) throws Exception {
      DomainBaseInput input = new DomainBaseInput();
      input.setName(name);
      input.setColor("#AABBCC");

      String response =
          mvc.perform(
                  post("/api/tenants/" + tenantId + "/domains/new/upsert")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      return JsonPath.read(response, "$.domain_id");
    }

    @Test
    @DisplayName("Domain created in tenant X should NOT be readable from tenant Y")
    void given_domainInTenantX_should_notBeReadableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX = tenantIsolationHelper.createTenantWithCurrentUser("Tenant X");
      Tenant tenantY = tenantIsolationHelper.createTenantWithCurrentUser("Tenant Y");

      String domainId = createDomainInTenant(tenantX.getId(), "isolation_domain_read");
      entityManager.flush();
      entityManager.clear();

      // Act — read from tenant Y
      int responseStatus =
          mvc.perform(
                  get("/api/tenants/" + tenantY.getId() + "/domains/" + domainId)
                      .accept(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Domain created in tenant X should be readable from tenant X")
    void given_domainInTenantX_should_beReadableFromTenantX() throws Exception {
      // Arrange
      Tenant tenantX = tenantIsolationHelper.createTenantWithCurrentUser("Tenant X");

      String domainId = createDomainInTenant(tenantX.getId(), "isolation_domain_same");

      // Act — read from same tenant
      mvc.perform(
              get("/api/tenants/" + tenantX.getId() + "/domains/" + domainId)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
    }
  }
}
