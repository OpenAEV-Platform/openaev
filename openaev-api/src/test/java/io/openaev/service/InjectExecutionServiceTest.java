package io.openaev.service;

import static io.openaev.utils.ExpectationUtils.setExpectationsNotVulnerable;
import static io.openaev.utils.ExpectationUtils.setExpectationsVulnerable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Inject;
import io.openaev.database.model.OutputParser;
import io.openaev.rest.inject.service.InjectExecutionService;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.OutputParserFixture;
import jakarta.annotation.Resource;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InjectExecutionServiceTest {

  @InjectMocks private InjectExecutionService testInjectExecutionService;

  @Resource protected ObjectMapper mapper;

  @Test
  void checkCveExpectation_NoOutputParsers_ShouldSetNotVulnerable() {
    Inject inject = InjectFixture.getDefaultInject();
    Agent agent = AgentFixture.createDefaultAgentService();
    Set<OutputParser> outputParsers = Set.of();
    ObjectNode structuredOutput = null;
    testInjectExecutionService.checkCveExpectation(outputParsers, structuredOutput, inject, agent);
    verify(setExpectationsNotVulnerable(any(), any()), times(1));
  }

  @Test
  void checkCveExpectation_NullStructuredOutput_ShouldSetNotVulnerable() {
    Inject inject = InjectFixture.getDefaultInject();
    Agent agent = AgentFixture.createDefaultAgentService();
    Set<OutputParser> outputParsers = Set.of(OutputParserFixture.getDefaultOutputParser());
    ObjectNode structuredOutput = null;
    testInjectExecutionService.checkCveExpectation(outputParsers, structuredOutput, inject, agent);
    verify(setExpectationsNotVulnerable(any(), any()), times(1));
  }

  @Test
  void checkCveExpectation_NoCveType_ShouldSetNotVulnerable() {
    Inject inject = InjectFixture.getDefaultInject();
    Agent agent = AgentFixture.createDefaultAgentService();
    Set<OutputParser> outputParsers = Set.of(OutputParserFixture.getDefaultOutputParser());
    ObjectNode structuredOutput = ObjectMapper.createObjectNode();
    structuredOutput
        .putArray("cve_key")
        .addObject()
        .put("id", "CVE-2025-0234")
        .put("host", "savacano28")
        .put("severity", "7.1");
    testInjectExecutionService.checkCveExpectation(outputParsers, structuredOutput, inject, agent);
    verify(setExpectationsNotVulnerable(any(), any()), times(1));
  }

  @Test
  void checkCveExpectation_HasCveTypeAndCveData_ShouldSetVulnerable() {
    Inject inject = InjectFixture.getDefaultInject();
    Agent agent = AgentFixture.createDefaultAgentService();
    Set<OutputParser> outputParsers = Set.of(OutputParserFixture.getCVEOutputElement());
    ObjectNode structuredOutput = ObjectMapper.createObjectNode();
    structuredOutput
        .putArray("cve_key")
        .addObject()
        .put("id", "CVE-2025-0234")
        .put("host", "savacano28")
        .put("severity", "7.1");
    testInjectExecutionService.checkCveExpectation(outputParsers, structuredOutput, inject, agent);
    verify(setExpectationsVulnerable(any(), any()), times(1));
  }
}
