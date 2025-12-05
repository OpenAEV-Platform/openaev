package io.openaev.rest;

import static io.openaev.utils.JsonUtils.asJsonString;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Domain;
import io.openaev.rest.domain.form.DomainBaseInput;
import io.openaev.utils.fixtures.composers.DomainComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Domain API tests")
public class DomainApiTest extends IntegrationTest {

  @Autowired private DomainComposer domainComposer; // Injection du composer
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;

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
                    .accept(MediaType.APPLICATION_JSON))
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
        domainComposer.forDomain(null).withName("existing").withColor("#123456").persist().get();

    DomainBaseInput input = new DomainBaseInput();
    input.setName("existing");
    input.setColor("#123456");

    String response =
        mvc.perform(
                post("/api/domains/{domainId}/upsert", "random-id")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Domain returnedDomain = mapper.readValue(response, Domain.class);

    Assertions.assertEquals(existingDomain.getName(), returnedDomain.getName());
    Assertions.assertEquals(existingDomain.getColor(), returnedDomain.getColor());
  }
}
