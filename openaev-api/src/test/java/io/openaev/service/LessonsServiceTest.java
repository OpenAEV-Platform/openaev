package io.openaev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import io.openaev.database.model.LessonsAnswer;
import io.openaev.database.model.LessonsCategory;
import io.openaev.database.model.LessonsQuestion;
import io.openaev.database.repository.LessonsAnswerRepository;
import io.openaev.database.repository.LessonsCategoryRepository;
import io.openaev.database.repository.LessonsQuestionRepository;
import java.util.Collections;
import java.util.List;
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
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
@DisplayName("LessonsService Tests")
class LessonsServiceTest {

  @Mock private LessonsQuestionRepository lessonsQuestionRepository;

  @Mock private LessonsAnswerRepository lessonsAnswerRepository;

  @Mock private LessonsCategoryRepository lessonsCategoryRepository;

  @InjectMocks private LessonsService lessonsService;

  // ========================================================================
  // resetLessonsAnswer Tests
  // ========================================================================
  @Nested
  @DisplayName("resetLessonsAnswer")
  class ResetLessonsAnswerTests {

    @Test
    @DisplayName("should delete all answers when categories, questions and answers exist")
    void shouldDeleteAllAnswers() {
      // Prepare
      String exerciseId = UUID.randomUUID().toString();

      LessonsCategory category1 = createMockCategory();
      LessonsCategory category2 = createMockCategory();
      List<LessonsCategory> categories = List.of(category1, category2);

      LessonsQuestion question1 = createMockQuestion();
      LessonsQuestion question2 = createMockQuestion();

      LessonsAnswer answer1 = mock(LessonsAnswer.class);
      LessonsAnswer answer2 = mock(LessonsAnswer.class);
      LessonsAnswer answer3 = mock(LessonsAnswer.class);

      when(lessonsCategoryRepository.findAll(any(Specification.class))).thenReturn(categories);
      when(lessonsQuestionRepository.findAll(any(Specification.class)))
          .thenReturn(List.of(question1))
          .thenReturn(List.of(question2));
      when(lessonsAnswerRepository.findAll(any(Specification.class)))
          .thenReturn(List.of(answer1))
          .thenReturn(List.of(answer2, answer3));

      // Act
      lessonsService.resetLessonsAnswer(exerciseId);

      // Assert
      verify(lessonsCategoryRepository).findAll(any(Specification.class));
      verify(lessonsQuestionRepository, times(2)).findAll(any(Specification.class));
      verify(lessonsAnswerRepository, times(2)).findAll(any(Specification.class));
      verify(lessonsAnswerRepository)
          .deleteAllLessonsAnswersQuestionsCategoriesByExerciseId(exerciseId);
    }

    @Test
    @DisplayName("should not delete when no categories exist")
    void shouldNotDeleteWhenNoCategories() {
      // Prepare
      String exerciseId = UUID.randomUUID().toString();
      when(lessonsCategoryRepository.findAll(any(Specification.class)))
          .thenReturn(Collections.emptyList());

      // Act
      lessonsService.resetLessonsAnswer(exerciseId);

      // Assert
      verify(lessonsCategoryRepository).findAll(any(Specification.class));
      verify(lessonsQuestionRepository, never()).findAll(any(Specification.class));
      verify(lessonsAnswerRepository, never()).findAll(any(Specification.class));
      verify(lessonsAnswerRepository, never())
          .deleteAllLessonsAnswersQuestionsCategoriesByExerciseId(anyString());
    }

    @Test
    @DisplayName("should not delete when no questions exist")
    void shouldNotDeleteWhenNoQuestions() {
      // Prepare
      String exerciseId = UUID.randomUUID().toString();
      LessonsCategory category = createMockCategory();

      when(lessonsCategoryRepository.findAll(any(Specification.class)))
          .thenReturn(List.of(category));
      when(lessonsQuestionRepository.findAll(any(Specification.class)))
          .thenReturn(Collections.emptyList());

      // Act
      lessonsService.resetLessonsAnswer(exerciseId);

      // Assert
      verify(lessonsCategoryRepository).findAll(any(Specification.class));
      verify(lessonsQuestionRepository).findAll(any(Specification.class));
      verify(lessonsAnswerRepository, never()).findAll(any(Specification.class));
      verify(lessonsAnswerRepository, never())
          .deleteAllLessonsAnswersQuestionsCategoriesByExerciseId(anyString());
    }

    @Test
    @DisplayName("should not delete when no answers exist")
    void shouldNotDeleteWhenNoAnswers() {
      // Prepare
      String exerciseId = UUID.randomUUID().toString();
      LessonsCategory category = createMockCategory();
      LessonsQuestion question = createMockQuestion();

      when(lessonsCategoryRepository.findAll(any(Specification.class)))
          .thenReturn(List.of(category));
      when(lessonsQuestionRepository.findAll(any(Specification.class)))
          .thenReturn(List.of(question));
      when(lessonsAnswerRepository.findAll(any(Specification.class)))
          .thenReturn(Collections.emptyList());

      // Act
      lessonsService.resetLessonsAnswer(exerciseId);

      // Assert
      verify(lessonsCategoryRepository).findAll(any(Specification.class));
      verify(lessonsQuestionRepository).findAll(any(Specification.class));
      verify(lessonsAnswerRepository).findAll(any(Specification.class));
      verify(lessonsAnswerRepository, never())
          .deleteAllLessonsAnswersQuestionsCategoriesByExerciseId(anyString());
    }

    @Test
    @DisplayName("should handle single category with single question and single answer")
    void shouldHandleSingleCategoryQuestionAnswer() {
      // Prepare
      String exerciseId = UUID.randomUUID().toString();
      LessonsCategory category = createMockCategory();
      LessonsQuestion question = createMockQuestion();
      LessonsAnswer answer = mock(LessonsAnswer.class);

      when(lessonsCategoryRepository.findAll(any(Specification.class)))
          .thenReturn(List.of(category));
      when(lessonsQuestionRepository.findAll(any(Specification.class)))
          .thenReturn(List.of(question));
      when(lessonsAnswerRepository.findAll(any(Specification.class))).thenReturn(List.of(answer));

      // Act
      lessonsService.resetLessonsAnswer(exerciseId);

      // Assert
      verify(lessonsAnswerRepository)
          .deleteAllLessonsAnswersQuestionsCategoriesByExerciseId(exerciseId);
    }

    @Test
    @DisplayName("should handle multiple questions per category")
    void shouldHandleMultipleQuestionsPerCategory() {
      // Prepare
      String exerciseId = UUID.randomUUID().toString();
      LessonsCategory category = createMockCategory();
      LessonsQuestion question1 = createMockQuestion();
      LessonsQuestion question2 = createMockQuestion();
      LessonsAnswer answer1 = mock(LessonsAnswer.class);
      LessonsAnswer answer2 = mock(LessonsAnswer.class);

      when(lessonsCategoryRepository.findAll(any(Specification.class)))
          .thenReturn(List.of(category));
      when(lessonsQuestionRepository.findAll(any(Specification.class)))
          .thenReturn(List.of(question1, question2));
      when(lessonsAnswerRepository.findAll(any(Specification.class)))
          .thenReturn(List.of(answer1))
          .thenReturn(List.of(answer2));

      // Act
      lessonsService.resetLessonsAnswer(exerciseId);

      // Assert
      verify(lessonsQuestionRepository).findAll(any(Specification.class));
      verify(lessonsAnswerRepository, times(2)).findAll(any(Specification.class));
      verify(lessonsAnswerRepository)
          .deleteAllLessonsAnswersQuestionsCategoriesByExerciseId(exerciseId);
    }

    private LessonsCategory createMockCategory() {
      LessonsCategory category = mock(LessonsCategory.class);
      when(category.getId()).thenReturn(UUID.randomUUID().toString());
      return category;
    }

    private LessonsQuestion createMockQuestion() {
      LessonsQuestion question = mock(LessonsQuestion.class);
      when(question.getId()).thenReturn(UUID.randomUUID().toString());
      return question;
    }
  }

  // ========================================================================
  // removeTeamsForSimulation Tests
  // ========================================================================
  @Nested
  @DisplayName("removeTeamsForSimulation")
  class RemoveTeamsForSimulationTests {

    @Captor private ArgumentCaptor<String> simulationIdCaptor;

    @Captor private ArgumentCaptor<List<String>> teamIdsCaptor;

    private static Stream<Arguments> testCases() {
      String simulationId = UUID.randomUUID().toString();
      String teamId1 = UUID.randomUUID().toString();
      String teamId2 = UUID.randomUUID().toString();
      String teamId3 = UUID.randomUUID().toString();

      return Stream.of(
          Arguments.of("multiple team IDs", simulationId, List.of(teamId1, teamId2, teamId3)),
          Arguments.of("single team ID", simulationId, List.of(teamId1)),
          Arguments.of("empty team IDs list", simulationId, Collections.emptyList()));
    }

    @ParameterizedTest(name = "should remove teams for {0}")
    @MethodSource("testCases")
    void shouldRemoveTeams(String name, String simulationId, List<String> teamIds) {
      // Act
      lessonsService.removeTeamsForSimulation(simulationId, teamIds);

      // Assert
      verify(lessonsCategoryRepository)
          .removeTeamsForExercise(simulationIdCaptor.capture(), teamIdsCaptor.capture());
      assertEquals(simulationId, simulationIdCaptor.getValue());
      assertEquals(teamIds, teamIdsCaptor.getValue());
      verifyNoMoreInteractions(lessonsCategoryRepository);
    }

    @Test
    @DisplayName("should not interact with other repositories")
    void shouldNotInteractWithOtherRepositories() {
      // Prepare
      String simulationId = UUID.randomUUID().toString();
      List<String> teamIds = List.of(UUID.randomUUID().toString());

      // Act
      lessonsService.removeTeamsForSimulation(simulationId, teamIds);

      // Assert
      verifyNoInteractions(lessonsQuestionRepository);
      verifyNoInteractions(lessonsAnswerRepository);
    }
  }
}
