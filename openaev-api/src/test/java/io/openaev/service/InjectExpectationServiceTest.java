package io.openaev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectation;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InjectExpectationServiceTest {

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

  @Test
  void detectionExpectationsNotExpired_NoneExpired() {
    // Arrange
    Inject inject = InjectFixture.getDefaultInject();
    InjectExpectation detectionExpectation =
        InjectExpectationFixture.createDetectionInjectExpectation(inject, null);
    InjectExpectation detectionExpectation2 =
        InjectExpectationFixture.createDetectionInjectExpectation(inject, null);

    when(mockedInjectExpectationRepository.findAll(any()))
        .thenReturn(List.of(detectionExpectation, detectionExpectation2));

    // Act
    List<InjectExpectation> result =
        testInjectExpectationService.detectionExpectationsNotExpired(
            EXPIRATION_TIME_SIX_HOURS.intValue() * 2);

    // Assert
    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals(detectionExpectation.getId(), result.get(0).getId());
  }

  @Test
  void manualExpectationsNotExpired_NoneExpired() {
    // Arrange
    Inject inject = InjectFixture.getDefaultInject();
    InjectExpectation manualExpectation =
        InjectExpectationFixture.createManualInjectExpectation(null, inject);
    InjectExpectation manualExpectation2 =
        InjectExpectationFixture.createManualInjectExpectation(null, inject);

    when(mockedInjectExpectationRepository.findAll(any()))
        .thenReturn(List.of(manualExpectation, manualExpectation2));

    // Act
    List<InjectExpectation> result =
        testInjectExpectationService.manualExpectationsNotExpired(
            EXPIRATION_TIME_SIX_HOURS.intValue() * 2);

    // Assert
    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals(manualExpectation.getId(), result.get(0).getId());
  }

  // ========================================================================
  // findDistinctInjectIdsByInjectExpectationIds Tests
  // ========================================================================
  @Nested
  @DisplayName("findDistinctInjectIdsByInjectExpectationIds")
  class FindDistinctInjectIdsByInjectExpectationIdsTests {

    @Captor private ArgumentCaptor<Set<String>> expectationIdsCaptor;

    private static Stream<Arguments> testCases() {
      String expectationId1 = UUID.randomUUID().toString();
      String expectationId2 = UUID.randomUUID().toString();
      String expectationId3 = UUID.randomUUID().toString();

      String injectId1 = UUID.randomUUID().toString();
      String injectId2 = UUID.randomUUID().toString();

      return Stream.of(
          Arguments.of(
              "multiple expectation IDs returning multiple inject IDs",
              Set.of(expectationId1, expectationId2, expectationId3),
              Set.of(injectId1, injectId2)),
          Arguments.of(
              "multiple expectation IDs returning single inject ID",
              Set.of(expectationId1, expectationId2),
              Set.of(injectId1)),
          Arguments.of("single expectation ID", Set.of(expectationId1), Set.of(injectId1)),
          Arguments.of("empty expectation IDs", Collections.emptySet(), Collections.emptySet()),
          Arguments.of(
              "expectation IDs with no matching injects",
              Set.of(expectationId1, expectationId2),
              Collections.emptySet()));
    }

    @ParameterizedTest(name = "should handle {0}")
    @MethodSource("testCases")
    void shouldReturnDistinctInjectIds(
        String name, Set<String> expectationIds, Set<String> expectedInjectIds) {
      // Prepare
      when(mockedInjectExpectationRepository.findDistinctInjectIdsByInjectExpectationIds(
              expectationIds))
          .thenReturn(expectedInjectIds);

      // Act
      Set<String> result =
          testInjectExpectationService.findDistinctInjectIdsByInjectExpectationIds(expectationIds);

      // Assert
      verify(mockedInjectExpectationRepository)
          .findDistinctInjectIdsByInjectExpectationIds(expectationIdsCaptor.capture());
      assertEquals(expectationIds, expectationIdsCaptor.getValue());
      assertNotNull(result);
      assertEquals(expectedInjectIds.size(), result.size());
      assertEquals(expectedInjectIds, result);
      verifyNoMoreInteractions(mockedInjectExpectationRepository);
    }
  }
}
