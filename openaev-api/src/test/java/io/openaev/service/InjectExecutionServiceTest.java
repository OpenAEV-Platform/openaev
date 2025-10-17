package io.openaev.service;

import static io.openaev.utils.fixtures.InjectExpectationFixture.createVulnerabilityInjectExpectation;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.rest.inject.service.InjectExecutionService;
import io.openaev.utils.ExpectationUtils;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.OutputParserFixture;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InjectExecutionServiceTest {

  @InjectMocks private InjectExecutionService testInjectExecutionService;
  @Mock private InjectExpectationService injectExpectationService;
  @Mock private InjectExpectationRepository injectExpectationRepository;
  private Inject inject;
  private InjectExpectation injectExpectation;
  private Agent agent;

  private ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    agent = AgentFixture.createDefaultAgentService();
    inject = InjectFixture.getDefaultInject();
    injectExpectation = createVulnerabilityInjectExpectation(inject, agent);
    inject.setExpectations(List.of(injectExpectation));
  }

  @Test
  void checkCveExpectation_NoOutputParsers_ShouldSetNotVulnerable() {
    Set<OutputParser> outputParsers = Set.of();
    ObjectNode structuredOutput = null;
    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      testInjectExecutionService.checkCveExpectation(
          outputParsers, structuredOutput, inject, agent);
      mocked.verify(
          () -> ExpectationUtils.setResultExpectationVulnerable(any(), any(), any()), times(1));
    }
  }

  @Test
  void checkCveExpectation_NullStructuredOutput_ShouldSetNotVulnerable() {
    Set<OutputParser> outputParsers = Set.of(OutputParserFixture.getDefaultOutputParser());
    ObjectNode structuredOutput = null;
    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      testInjectExecutionService.checkCveExpectation(
          outputParsers, structuredOutput, inject, agent);
      mocked.verify(
          () -> ExpectationUtils.setResultExpectationVulnerable(any(), any(), any()), times(1));
    }
  }

  @Test
  void checkCveExpectation_NoCveType_ShouldSetNotVulnerable() {
    Set<OutputParser> outputParsers = Set.of(OutputParserFixture.getDefaultOutputParser());
    ObjectNode structuredOutput = mapper.createObjectNode();
    structuredOutput
        .putArray("cve-key")
        .addObject()
        .put("id", "CVE-2025-0234")
        .put("host", "savacano28")
        .put("severity", "7.1");
    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      testInjectExecutionService.checkCveExpectation(
          outputParsers, structuredOutput, inject, agent);
      mocked.verify(
          () -> ExpectationUtils.setResultExpectationVulnerable(any(), any(), any()), times(1));
    }
  }

  @Test
  void checkCveExpectation_HasCveTypeAndCveData_ShouldSetVulnerable() {
    ContractOutputElement CVEOutputElement = OutputParserFixture.getCVEOutputElement();
    Set<OutputParser> outputParsers =
        Set.of(OutputParserFixture.getOutputParser(Set.of(CVEOutputElement)));
    ObjectNode structuredOutput = mapper.createObjectNode();
    structuredOutput
        .putArray("cve-key")
        .addObject()
        .put("id", "CVE-2025-0234")
        .put("host", "savacano28")
        .put("severity", "7.1");

    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      testInjectExecutionService.checkCveExpectation(
          outputParsers, structuredOutput, inject, agent);

      mocked.verify(
          () -> ExpectationUtils.setResultExpectationVulnerable(any(), any(), any()), times(1));
    }
  }
}
