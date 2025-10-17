package io.openaev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectation;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InjectExecutionServiceTest {

  static Long EXPIRATION_TIME_SIX_HOURS = 21600L;

  @Mock private InjectExpectationRepository mockedInjectExpectationRepository;
  @InjectMocks private InjectExpectationService testInjectExpectationService;

  @Test
  void preventionExpectationsNotExpired_NoneExpired() {
    // Arrange
    Inject inject = InjectFixture.getDefaultInject();
    InjectExpectation preventionExpectation =
        InjectExpectationFixture.createPreventionInjectExpectation(inject, null);
    InjectExpectation preventionExpectation2 =
        InjectExpectationFixture.createPreventionInjectExpectation(inject, null);

    when(mockedInjectExpectationRepository.findAll(any()))
        .thenReturn(List.of(preventionExpectation, preventionExpectation2));

    // Act
    List<InjectExpectation> result =
        testInjectExpectationService.preventionExpectationsNotExpired(
            EXPIRATION_TIME_SIX_HOURS.intValue() * 2);

    // Assert
    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals(preventionExpectation.getId(), result.get(0).getId());
  }
}
