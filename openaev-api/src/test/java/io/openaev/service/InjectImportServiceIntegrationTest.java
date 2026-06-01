package io.openaev.service;

import static io.openaev.utils.fixtures.import_mapper.RuleAttributeFixture.createRuleAttribute;
import static org.junit.jupiter.api.Assertions.*;

import io.openaev.IntegrationTest;
import io.openaev.database.model.ImportMapper;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectImporter;
import io.openaev.database.model.Scenario;
import io.openaev.rest.scenario.response.ImportTestSummary;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.XlsFixture;
import io.openaev.utils.fixtures.import_mapper.ImportMapperFixture;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
public class InjectImportServiceIntegrationTest extends IntegrationTest {

  @Autowired private InjectImportService injectImportService;

  @DisplayName(
      "Expectation rule attributes with no mapped columns and no default value"
          + " should not produce expectations on the imported inject")
  @Test
  void given_unmappedExpectationRuleAttributes_should_notCreateExpectationsOnInject()
      throws Exception {
    // -- ARRANGE --
    String importId = XlsFixture.createDefaultXlsFile();

    ImportMapper importMapper =
        ImportMapperFixture.createImportMapper(XlsFixture.DEFAULT_INJECT_TYPE);
    InjectImporter importer = importMapper.getInjectImporters().getFirst();
    importer.getRuleAttributes().add(createRuleAttribute("expectation_name"));
    importer.getRuleAttributes().add(createRuleAttribute("expectation_description"));
    importer.getRuleAttributes().add(createRuleAttribute("expectation_score"));

    Scenario scenario = ScenarioFixture.getScheduledScenario();

    // -- ACT --
    ImportTestSummary result =
        injectImportService.importInjectIntoScenarioFromXLS(
            scenario, importMapper, importId, XlsFixture.DEFAULT_SHEET_NAME, 0, false);

    // -- ASSERT --
    assertNotNull(result);
    assertEquals(1, result.getTotalNumberOfInjects());
    assertFalse(result.getInjects().isEmpty());

    Inject importedInject = result.getInjects().getFirst();
    assertNotNull(importedInject.getContent());
    assertNull(importedInject.getContent().get("expectations"));
  }
}
