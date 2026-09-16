package io.openaev.scheduler.jobs;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.database.model.*;
import io.openaev.database.repository.InjectDependenciesRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.scheduler.jobs.exception.ErrorMessagesPreExecutionException;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
@DisplayName("InjectsExecutionJob Unit Tests")
class InjectsExecutionJobUnitTest {

  @Mock private InjectDependenciesRepository injectDependenciesRepository;

  @Mock private InjectExpectationRepository injectExpectationRepository;

  @InjectMocks private InjectsExecutionJob injectsExecutionJob;

  // ========================================================================
  // Malicious extensions
  // ========================================================================
  @Nested
  @DisplayName("handleMaliciousExpectationsTests")
  // Because we use the inject composer in this test, we need to use the spring context, despite it
  // being super slow
  // Which is why this test is isolated in it's own nested class
  @SpringBootTest
  @Transactional
  class handleMaliciousExpectationsTests {

    @Autowired private InjectComposer injectComposer;

    @BeforeEach
    void initMocks() {
      // As we are using the spring extension, we need to manually enable the mocks from the parent
      // class
      MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName(
        "When auto closing of NON stix-created simulation, DOES NOT trigger stix coverage job")
    public void shouldRaiseExceptionIfExpectationMalicious() {
      Inject inject = injectComposer.forInject(InjectFixture.getDefaultInject()).get();
      inject.setId(UUID.randomUUID().toString());
      InjectDependency injectDependency = new InjectDependency();
      injectDependency
          .getCompositeId()
          .setInjectParent(
              InjectFixture.createInjectWithManualExpectation(
                  InjectorContractFixture.createDefaultInjectorContract(),
                  "parent",
                  "T(java.lang.Runtime).getRuntime().exec('gedit');"));
      injectDependency.getCompositeId().setInjectChildren(InjectFixture.getDefaultInject());
      injectDependency.setInjectDependencyCondition(
          new InjectDependencyConditions.InjectDependencyCondition());
      InjectDependencyConditions.Condition condition = new InjectDependencyConditions.Condition();
      condition.setOperator(InjectDependencyConditions.DependencyOperator.eq);
      condition.setValue(true);
      condition.setKey("T(java.lang.Runtime).getRuntime().exec('gedit');");
      injectDependency.getInjectDependencyCondition().setConditions(List.of(condition));
      when(injectDependenciesRepository.findParents(any())).thenReturn(List.of(injectDependency));
      try {
        injectsExecutionJob.checkErrorMessagesPreExecution(UUID.randomUUID().toString(), inject);
        fail("Should have raised an exception");
      } catch (Exception e) {
        assertThat(e).isInstanceOf(ErrorMessagesPreExecutionException.class);
        assertThat(e.getMessage())
            .isEqualTo("There was an error during the evaluation of the condition of the inject");
      }
    }
  }
}
