package io.openaev.injects.manual;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Execution;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectation;
import io.openaev.database.model.Injection;
import io.openaev.execution.ExecutableInject;
import io.openaev.injectors.manual.ManualExecutor;
import io.openaev.injectors.manual.model.ManualContent;
import io.openaev.model.expectation.ManualExpectation;
import io.openaev.model.inject.form.Expectation;
import io.openaev.service.InjectExpectationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
public class ManualExecutorTest extends IntegrationTest {

  @Mock InjectExpectationService injectExpectationService;

  @Mock ObjectMapper mapper;

  @InjectMocks private ManualExecutor manualExecutor;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(manualExecutor, "mapper", mapper);
  }

  @Test
  void process() throws Exception {

    // mock input
    Expectation expectation = new Expectation();
    expectation.setExpectationGroup(false);
    expectation.setName("Expectation 1");
    expectation.setDescription("Expectation 1");
    expectation.setType(InjectExpectation.EXPECTATION_TYPE.MANUAL);
    expectation.setScore(80D);
    expectation.setExpirationTime(Instant.now().toEpochMilli());
    ManualContent manualContent = new ManualContent();
    manualContent.setExpectations(List.of(expectation));
    Execution execution = mock(Execution.class);
    ExecutableInject executableInject = mock(ExecutableInject.class);
    Injection injection = mock(Injection.class);
    Inject inject = mock(Inject.class);
    ObjectNode content = mock(ObjectNode.class);
    when(inject.getContent()).thenReturn(content);
    when(injection.getInject()).thenReturn(inject);
    when(executableInject.getInjection()).thenReturn(injection);
    when(mapper.treeToValue(content, ManualContent.class)).thenReturn(manualContent);

    this.manualExecutor.process(execution, executableInject);

    // verify that the expectations are saved
    verify(injectExpectationService)
        .buildAndSaveInjectExpectations(
            executableInject, List.of(new ManualExpectation(expectation)));
  }
}
