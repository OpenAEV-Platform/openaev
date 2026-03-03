package io.openaev.service;

import io.openaev.database.repository.LessonsAnswerRepository;
import io.openaev.database.repository.LessonsCategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class LessonsService {
  private final LessonsAnswerRepository lessonsAnswerRepository;
  private final LessonsCategoryRepository lessonsCategoryRepository;

  /**
   * Reset the answers for all lessons of a given simulation
   *
   * @param simulationId the simulation ID
   */
  public void resetLessonsAnswer(String simulationId) {
    lessonsAnswerRepository.deleteAllLessonsAnswersQuestionsCategoriesByExerciseId(simulationId);
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
