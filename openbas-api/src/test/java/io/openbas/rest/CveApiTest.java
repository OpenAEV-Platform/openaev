package io.openbas.rest;

import static io.openbas.rest.cve.CveApi.CVE_API;
import static io.openbas.utils.JsonUtils.asJsonString;
import static io.openbas.utils.fixtures.CveFixture.CVE_2025_5678;
import static io.openbas.utils.fixtures.CveInputFixture.CVE_EXTERNAL_ID;
import static io.openbas.utils.fixtures.CveInputFixture.createDefaultCveCreateInput;
import static java.time.Instant.now;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.IntegrationTest;
import io.openbas.database.model.Collector;
import io.openbas.database.model.Cve;
import io.openbas.database.repository.CveRepository;
import io.openbas.rest.cve.form.CVEBulkInsertInput;
import io.openbas.rest.cve.form.CveCreateInput;
import io.openbas.rest.cve.form.CveUpdateInput;
import io.openbas.utils.fixtures.CollectorFixture;
import io.openbas.utils.fixtures.composers.CollectorComposer;
import io.openbas.utils.fixtures.composers.CveComposer;
import io.openbas.utils.mockUser.WithMockUser;
import io.openbas.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("CVE API Integration Tests")
class CveApiTest extends IntegrationTest {

  @Resource protected ObjectMapper mapper;
  @Autowired private MockMvc mvc;
  private Collector collector;

  @Autowired private CveComposer cveComposer;
  @Autowired private CollectorComposer collectorComposer;
  @Autowired private CveRepository cveRepository;

  @BeforeAll
  void init() {
    collector =
        collectorComposer
            .forCollector(CollectorFixture.createDefaultCollector("CS"))
            .persist()
            .get();
  }

  @BeforeEach
  void setUp() {
    cveComposer.reset();
  }

  @Nested
  @DisplayName("When working with CVEs")
  @WithMockUser(isAdmin = true)
  class WhenWorkingWithCves {

    @Test
    @DisplayName("Should create a new CVE successfully")
    void shouldCreateNewCve() throws Exception {
      CveCreateInput input = new CveCreateInput();
      input.setExternalId("CVE-2025-1234");
      input.setCvssV31(new BigDecimal("5.2"));
      input.setDescription("Test summary for CVE creation");

      String response =
          mvc.perform(
                  post(CVE_API)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).node("cve_external_id").isEqualTo("CVE-2025-1234");
    }

    @Test
    @DisplayName("Should fetch a CVE by ID")
    void shouldFetchCveById() throws Exception {
      Cve cve = new Cve();
      cve.setExternalId(CVE_2025_5678);
      cve.setCvssV31(new BigDecimal("8.9"));
      cve.setDescription("Test CVE");

      cveComposer.forCve(cve).persist();

      String response =
          mvc.perform(get(CVE_API + "/" + cve.getId()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).node("cve_external_id").isEqualTo(CVE_2025_5678);
    }

    @Test
    @DisplayName("Should update an existing CVE")
    void shouldUpdateCve() throws Exception {
      Cve cve = new Cve();
      cve.setExternalId("CVE-2025-5679");
      cve.setCvssV31(new BigDecimal("4.5"));
      cve.setDescription("Old description");
      cveComposer.forCve(cve).persist();

      CveUpdateInput updateInput = new CveUpdateInput();
      updateInput.setDescription("Updated Summary");

      mvc.perform(
              put(CVE_API + "/" + cve.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateInput)))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      Assertions.assertTrue(
          updateInput
              .getDescription()
              .equals(
                  cveRepository.findById(cve.getId()).map(cve1 -> cve1.getDescription()).get()));
    }

    @Test
    @DisplayName("Should bulk insert multiple CVEs")
    void shouldBulkInsertCVEs() throws Exception {
      // -- PREPARE -
      CveCreateInput input = createDefaultCveCreateInput();
      CVEBulkInsertInput inputs = new CVEBulkInsertInput();
      inputs.setSourceIdentifier(collector.getId());
      inputs.setLastModifiedDateFetched(now());
      inputs.setLastIndex(1234);
      inputs.setInitialDatasetCompleted(false);
      inputs.setCves(List.of(input));

      // -- EXECUTE --

      mvc.perform(
              post(CVE_API + "/bulk")
                  .content(asJsonString(inputs))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      // -- ASSERT --
      Assertions.assertTrue(cveRepository.findByExternalId(CVE_EXTERNAL_ID).isPresent());
    }

    @Test
    @DisplayName("Should delete a CVE")
    void shouldDeleteCve() throws Exception {
      Cve cve = new Cve();
      cve.setExternalId("CVE-2025-5679");
      cve.setCvssV31(new BigDecimal("7.5"));
      cve.setDescription("To be deleted");
      cveComposer.forCve(cve).persist();

      mvc.perform(delete(CVE_API + "/" + cve.getExternalId())).andExpect(status().isOk());

      Assertions.assertFalse(cveRepository.findById(cve.getExternalId()).isPresent());
    }

    @Test
    @DisplayName("Should return CVEs on search")
    void shouldReturnCvesOnSearch() throws Exception {
      Cve cve = new Cve();
      cve.setExternalId("CVE-2024-5679");
      cve.setCvssV31(new BigDecimal("4.5"));
      cve.setDescription("Cve 1");
      cveComposer.forCve(cve).persist();

      Cve cve1 = new Cve();
      cve1.setExternalId("CVE-2025-5671");
      cve1.setCvssV31(new BigDecimal("1.8"));
      cve1.setDescription("Cve 2");
      cveComposer.forCve(cve1).persist();

      SearchPaginationInput input = new SearchPaginationInput();
      input.setSize(10);
      input.setPage(0);

      String response =
          mvc.perform(
                  post(CVE_API + "/search")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response)
          .inPath("content[*].cve_external_id")
          .isArray()
          .contains("CVE-2024-5679", "CVE-2025-5671");
    }
  }
}
