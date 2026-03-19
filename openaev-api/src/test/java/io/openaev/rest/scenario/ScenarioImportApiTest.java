package io.openaev.rest.scenario;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.ImportMapper;
import io.openaev.database.repository.ImportMapperRepository;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.scenario.form.InjectsImportInput;
import io.openaev.rest.scenario.response.ImportTestSummary;
import io.openaev.service.InjectImportService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@ExtendWith(MockitoExtension.class)
public class ScenarioImportApiTest extends IntegrationTest {

  private MockMvc mvc;

  @Mock private InjectService injectService;
  @Mock private InjectImportService injectImportService;
  @Mock private ImportMapperRepository importMapperRepository;
  @Mock private ScenarioService scenarioService;

  private ScenarioImportApi scenarioImportApi;

  private String SCENARIO_ID;

  @BeforeEach
  public void setUp() {
    // Injecting mocks into the controller
    scenarioImportApi =
        new ScenarioImportApi(injectImportService, importMapperRepository, scenarioService);

    SCENARIO_ID = UUID.randomUUID().toString();

    mvc = MockMvcBuilders.standaloneSetup(scenarioImportApi).build();
  }

  @DisplayName("Test dry run import xls")
  @Test
  void testDryRunXls() throws Exception {
    // -- PREPARE --
    InjectsImportInput injectsImportInput = new InjectsImportInput();
    injectsImportInput.setImportMapperId(UUID.randomUUID().toString());
    injectsImportInput.setName("TEST");
    injectsImportInput.setTimezoneOffset(120);

    when(importMapperRepository.findById(any())).thenReturn(Optional.of(new ImportMapper()));
    when(injectImportService.importInjectIntoScenarioFromXLS(
            any(), any(), any(), any(), anyInt(), anyBoolean()))
        .thenReturn(new ImportTestSummary());

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                MockMvcRequestBuilders.post(
                        "/api/scenarios/{scenarioId}/xls/{importId}/dry",
                        SCENARIO_ID,
                        UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(injectsImportInput)))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
  }

  @DisplayName("Test import xls")
  @Test
  void testImportXls() throws Exception {
    // -- PREPARE --
    InjectsImportInput injectsImportInput = new InjectsImportInput();
    injectsImportInput.setImportMapperId(UUID.randomUUID().toString());
    injectsImportInput.setName("TEST");
    injectsImportInput.setTimezoneOffset(120);

    when(importMapperRepository.findById(any())).thenReturn(Optional.of(new ImportMapper()));
    when(injectImportService.importInjectIntoScenarioFromXLS(
            any(), any(), any(), any(), anyInt(), anyBoolean()))
        .thenReturn(new ImportTestSummary());

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                MockMvcRequestBuilders.post(
                        "/api/scenarios/{scenarioId}/xls/{importId}/import",
                        SCENARIO_ID,
                        UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(injectsImportInput)))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
  }
}
