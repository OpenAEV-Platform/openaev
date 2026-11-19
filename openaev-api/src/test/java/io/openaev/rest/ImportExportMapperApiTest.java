package io.openaev.rest;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.helper.StreamHelper;
import io.openaev.utils.CsvType;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.ResourceUtils;

@TestInstance(PER_CLASS)
public class ImportExportMapperApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private MarkingDefinitionRepository markingDefinitionRepository;
  @Autowired private TenantScopedTransaction tenantTx;

  private <T> T inDefaultTenant(Supplier<T> work) {
    return tenantTx.execute(TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID), work);
  }

  private void inDefaultTenant(Runnable work) {
    tenantTx.execute(TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID), work);
  }

  @DisplayName("Test testing an export csv with endpoints target")
  @Test
  @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
  void testExportCsvWithEndpoints() throws Exception {
    // -- PREPARE --
    endpointRepository.save(EndpointFixture.createEndpoint());

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                MockMvcRequestBuilders.post("/api/mappers/export/csv?csvType=" + CsvType.ENDPOINTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(new SearchPaginationInput()))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
  }

  @DisplayName("Test testing an export csv with unknown csv type")
  @Test
  @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
  void testExportCsvWithUnknownCsvType() throws Exception {
    // -- PREPARE --
    endpointRepository.save(EndpointFixture.createEndpoint());

    // -- EXECUTE --
    this.mvc
        .perform(
            MockMvcRequestBuilders.post("/api/mappers/export/csv?csvType=" + CsvType.AGENT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(new SearchPaginationInput()))
                .with(csrf()))
        .andExpect(status().is4xxClientError())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @DisplayName("Test testing an import csv with endpoints csv type")
  @Test
  @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
  void testImportCsvWithEndpointsCsvType() throws Exception {
    // -- PREPARE --
    inDefaultTenant(() -> endpointRepository.deleteAll());
    File testFile = ResourceUtils.getFile("classpath:csv-test-files/Endpoints.csv");

    InputStream in = new FileInputStream(testFile);
    MockMultipartFile csvFile =
        new MockMultipartFile("file", "my-awesome-file.csv", "text/csv", in.readAllBytes());

    // -- EXECUTE --
    this.mvc
        .perform(
            MockMvcRequestBuilders.multipart("/api/mappers/import/csv?csvType=" + CsvType.ENDPOINTS)
                .file(csvFile)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();

    List<Endpoint> endpoints =
        inDefaultTenant(() -> StreamHelper.fromIterable(endpointRepository.findAll()));
    Optional<Tag> tag = inDefaultTenant(() -> tagRepository.findByName("ransomware"));

    // -- ASSERT --
    assertEquals(1, endpoints.size());
    assertTrue(tag.isPresent());
    assertEquals("titi", endpoints.getFirst().getName());
    assertEquals("ransomware", tag.get().getName());
    assertEquals("#8fd671", tag.get().getColor());

    inDefaultTenant(
        () -> {
          endpointRepository.deleteAll();
          tagRepository.deleteById(tag.get().getId());
        });
  }

  @DisplayName("Test testing an import csv with unknown csv type")
  @Test
  @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
  void testImportCsvWithUnknownCsvType() throws Exception {
    // -- PREPARE --
    File testFile = ResourceUtils.getFile("classpath:csv-test-files/Endpoints.csv");

    InputStream in = new FileInputStream(testFile);
    MockMultipartFile csvFile =
        new MockMultipartFile("file", "my-awesome-file.csv", "text/csv", in.readAllBytes());

    // -- EXECUTE --
    this.mvc
        .perform(
            MockMvcRequestBuilders.multipart("/api/mappers/import/csv?csvType=" + CsvType.AGENT)
                .file(csvFile)
                .with(csrf()))
        .andExpect(status().is4xxClientError())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @DisplayName("A csv type with its own API is refused on the mapper endpoints")
  @ParameterizedTest(name = "export of {0}")
  @EnumSource(
      value = CsvType.class,
      names = {"MARKING_DEFINITIONS", "INJECTOR_CONTRACTS"})
  @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
  void given_aTypeWithItsOwnApi_should_refuseTheMapperExport(CsvType csvType) throws Exception {
    // -- EXECUTE / ASSERT --
    this.mvc
        .perform(
            MockMvcRequestBuilders.post("/api/mappers/export/csv?csvType=" + csvType)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(new SearchPaginationInput()))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @DisplayName("A marking definitions file is refused on the mapper import, nothing is created")
  @Test
  @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
  void given_markingDefinitionsFile_should_refuseTheMapperImportAndCreateNothing()
      throws Exception {
    // -- PREPARE --
    MockMultipartFile csvFile =
        new MockMultipartFile(
            "file",
            "markings.csv",
            "text/csv",
            "type,definition,color,order\nGUARDTEST,GUARDTEST:A,#123456,1\n"
                .getBytes(StandardCharsets.UTF_8));

    // -- EXECUTE --
    this.mvc
        .perform(
            MockMvcRequestBuilders.multipart(
                    "/api/mappers/import/csv?csvType=" + CsvType.MARKING_DEFINITIONS)
                .file(csvFile)
                .with(csrf()))
        .andExpect(status().isBadRequest());

    // -- ASSERT --
    assertFalse(
        inDefaultTenant(
            () ->
                markingDefinitionRepository.existsByTypeAndDefinitionAndTenantIdExcludingId(
                    "GUARDTEST", "GUARDTEST:A", Tenant.DEFAULT_TENANT_UUID, null)));
  }
}
