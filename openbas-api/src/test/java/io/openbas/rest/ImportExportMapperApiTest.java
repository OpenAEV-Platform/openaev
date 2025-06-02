package io.openbas.rest;

import static io.openbas.utils.JsonUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openbas.IntegrationTest;
import io.openbas.database.model.Endpoint;
import io.openbas.database.model.Tag;
import io.openbas.database.repository.EndpointRepository;
import io.openbas.database.repository.TagRepository;
import io.openbas.helper.StreamHelper;
import io.openbas.utils.TargetType;
import io.openbas.utils.fixtures.EndpointFixture;
import io.openbas.utils.mockUser.WithMockAdminUser;
import io.openbas.utils.pagination.SearchPaginationInput;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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

  @DisplayName("Test testing an export csv with endpoints target")
  @Test
  @WithMockAdminUser
  void testExportCsvWithEndpointsTarget() throws Exception {
    // -- PREPARE --
    endpointRepository.save(EndpointFixture.createEndpoint());

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                MockMvcRequestBuilders.post(
                        "/api/mappers/export/csv?targetType=" + TargetType.ENDPOINTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(new SearchPaginationInput())))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
  }

  @DisplayName("Test testing an export csv with unknown target")
  @Test
  @WithMockAdminUser
  void testExportCsvWithUnknownTarget() throws Exception {
    // -- PREPARE --
    endpointRepository.save(EndpointFixture.createEndpoint());

    // -- EXECUTE --
    this.mvc
        .perform(
            MockMvcRequestBuilders.post("/api/mappers/export/csv?targetType=" + TargetType.AGENT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(new SearchPaginationInput())))
        .andExpect(status().is4xxClientError())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @DisplayName("Test testing an import csv with endpoints target")
  @Test
  @WithMockAdminUser
  void testImportCsvWithEndpointsTarget() throws Exception {
    // -- PREPARE --
    endpointRepository.deleteAll();
    File testFile = ResourceUtils.getFile("classpath:csv-test-files/Endpoints.csv");

    InputStream in = new FileInputStream(testFile);
    MockMultipartFile csvFile =
        new MockMultipartFile("file", "my-awesome-file.csv", "text/csv", in.readAllBytes());

    // -- EXECUTE --
    this.mvc
        .perform(
            MockMvcRequestBuilders.multipart(
                    "/api/mappers/import/csv?targetType=" + TargetType.ENDPOINTS)
                .file(csvFile))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();

    List<Endpoint> endpoints = StreamHelper.fromIterable(endpointRepository.findAll());
    Optional<Tag> tag = tagRepository.findByName("ransomware");

    // -- ASSERT --
    assertEquals(1, endpoints.size());
    assertTrue(tag.isPresent());
    assertEquals("titi", endpoints.getFirst().getName());
    assertEquals("ransomware", tag.get().getName());
    assertEquals("#8fd671", tag.get().getColor());

    endpointRepository.deleteAll();
    tagRepository.deleteById(tag.get().getId());
  }

  @DisplayName("Test testing an import csv with unknown target")
  @Test
  @WithMockAdminUser
  void testImportCsvWithUnknownTarget() throws Exception {
    // -- PREPARE --
    File testFile = ResourceUtils.getFile("classpath:csv-test-files/Endpoints.csv");

    InputStream in = new FileInputStream(testFile);
    MockMultipartFile csvFile =
        new MockMultipartFile("file", "my-awesome-file.csv", "text/csv", in.readAllBytes());

    // -- EXECUTE --
    this.mvc
        .perform(
            MockMvcRequestBuilders.multipart(
                    "/api/mappers/import/csv?targetType=" + TargetType.AGENT)
                .file(csvFile))
        .andExpect(status().is4xxClientError())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }
}
