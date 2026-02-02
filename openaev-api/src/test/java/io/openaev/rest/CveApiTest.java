package io.openaev.rest;

import static io.openaev.rest.cve.CveApi.CVE_API;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.VulnerabilityFixture.CVE_2025_5678;
import static io.openaev.utils.fixtures.VulnerabilityFixture.VULNERABILITY_EXTERNAL_ID;
import static java.time.Instant.now;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Collector;
import io.openaev.database.model.Vulnerability;
import io.openaev.database.repository.VulnerabilityRepository;
import io.openaev.rest.cve.form.CVEBulkInsertInput;
import io.openaev.rest.cve.form.CveCreateInput;
import io.openaev.rest.vulnerability.form.VulnerabilityCreateInput;
import io.openaev.rest.vulnerability.form.VulnerabilityUpdateInput;
import io.openaev.utils.fixtures.CollectorFixture;
import io.openaev.utils.fixtures.composers.CollectorComposer;
import io.openaev.utils.fixtures.composers.VulnerabilityComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
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

  @Autowired private VulnerabilityComposer vulnerabilityComposer;
  @Autowired private CollectorComposer collectorComposer;
  @Autowired private VulnerabilityRepository vulnerabilityRepository;

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
    collectorComposer.reset();
  }

  @Nested
  @DisplayName("When working with CVEs")
  @WithMockUser(isAdmin = true)
  class WhenWorkingWithCves {

    @Test
    @DisplayName("Should create a new CVE successfully")
    void shouldCreateNewCve() throws Exception {
      VulnerabilityCreateInput input = new VulnerabilityCreateInput();
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
      Vulnerability cve = new Vulnerability();
      cve.setExternalId(CVE_2025_5678);
      cve.setCvssV31(new BigDecimal("8.9"));
      cve.setDescription("Test CVE");

      vulnerabilityComposer.forVulnerability(cve).persist();

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
      Vulnerability cve = new Vulnerability();
      cve.setExternalId("CVE-2025-5679");
      cve.setCvssV31(new BigDecimal("4.5"));
      cve.setDescription("Old description");
      vulnerabilityComposer.forVulnerability(cve).persist();

      VulnerabilityUpdateInput updateInput = new VulnerabilityUpdateInput();
      updateInput.setDescription("Updated Summary");

      mvc.perform(
              put(CVE_API + "/" + cve.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateInput)))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      Assertions.assertEquals(
          updateInput.getDescription(),
          vulnerabilityRepository.findById(cve.getId()).map(cve1 -> cve1.getDescription()).get());
    }

    @Test
    @DisplayName("Should bulk insert multiple CVEs")
    void shouldBulkInsertCVEs() throws Exception {

      CveCreateInput cveInput = new CveCreateInput();
      cveInput.setExternalId(VULNERABILITY_EXTERNAL_ID);
      cveInput.setSourceIdentifier(collector.getId());
      cveInput.setCvssV31(BigDecimal.valueOf(7.8));
      cveInput.setPublished(now());
      cveInput.setDescription("Sample CVE for testing");
      cveInput.setReferenceUrls(List.of("https://example.com/cve"));

      CVEBulkInsertInput input = new CVEBulkInsertInput();
      input.setSourceIdentifier(collector.getId());
      input.setLastModifiedDateFetched(now());
      input.setLastIndex(1234);
      input.setInitialDatasetCompleted(false);
      input.setCves(List.of(cveInput));

      mvc.perform(
              post(CVE_API + "/bulk")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      Assertions.assertTrue(
          vulnerabilityRepository.findByExternalId(VULNERABILITY_EXTERNAL_ID).isPresent());
    }

    @Test
    @DisplayName("Should delete a CVE")
    void shouldDeleteCve() throws Exception {
      Vulnerability cve = new Vulnerability();
      cve.setExternalId("CVE-2025-5679");
      cve.setCvssV31(new BigDecimal("7.5"));
      cve.setDescription("To be deleted");
      vulnerabilityComposer.forVulnerability(cve).persist();

      mvc.perform(delete(CVE_API + "/" + cve.getExternalId())).andExpect(status().isOk());

      Assertions.assertFalse(vulnerabilityRepository.findById(cve.getExternalId()).isPresent());
    }

    @Test
    @DisplayName("Should return CVEs on search")
    void shouldReturnCvesOnSearch() throws Exception {
      Vulnerability cve = new Vulnerability();
      cve.setExternalId("CVE-2024-5679");
      cve.setCvssV31(new BigDecimal("4.5"));
      cve.setDescription("Cve 1");
      vulnerabilityComposer.forVulnerability(cve).persist();

      Vulnerability cve1 = new Vulnerability();
      cve1.setExternalId("CVE-2025-5671");
      cve1.setCvssV31(new BigDecimal("1.8"));
      cve1.setDescription("Cve 2");
      vulnerabilityComposer.forVulnerability(cve1).persist();

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
