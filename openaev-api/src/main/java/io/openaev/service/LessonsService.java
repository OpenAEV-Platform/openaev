package io.openaev.service;

import io.openaev.database.model.LessonsAnswer;
import io.openaev.database.repository.LessonsAnswerRepository;
import io.openaev.database.repository.LessonsCategoryRepository;
import io.openaev.database.repository.LessonsQuestionRepository;
import io.openaev.database.specification.LessonsAnswerSpecification;
import io.openaev.database.specification.LessonsCategorySpecification;
import io.openaev.database.specification.LessonsQuestionSpecification;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class LessonsService {
  private final LessonsQuestionRepository lessonsQuestionRepository;
  private final LessonsAnswerRepository lessonsAnswerRepository;
  private final LessonsCategoryRepository lessonsCategoryRepository;

  /**
   * Reset the answers for all lessons of a given simulation
   *
   * @param simulationId the simulation ID
   */
  public void resetLessonsAnswer(String simulationId) {
    List<LessonsAnswer> lessonsAnswers =
        lessonsCategoryRepository
            .findAll(LessonsCategorySpecification.fromExercise(simulationId))
            .stream()
            .flatMap(
                lessonsCategory ->
                    lessonsQuestionRepository
                        .findAll(LessonsQuestionSpecification.fromCategory(lessonsCategory.getId()))
                        .stream()
                        .flatMap(
                            lessonsQuestion ->
                                lessonsAnswerRepository
                                    .findAll(
                                        LessonsAnswerSpecification.fromQuestion(
                                            lessonsQuestion.getId()))
                                    .stream()))
            .toList();
    if (!lessonsAnswers.isEmpty()) lessonsAnswerRepository.deleteAll(lessonsAnswers);
  }

  /**
   * Removes a list of teams from a simulation
   *
   * @param simulationId simulation ID
   * @param teamIds teams to remove
   */
  public void removeTeamsForSimulation(String simulationId, List<String> teamIds) {
    this.lessonsCategoryRepository.removeTeamsForExercise(simulationId, teamIds);
  }
}
