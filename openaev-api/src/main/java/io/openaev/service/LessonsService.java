package io.openaev.service;

import io.openaev.database.repository.LessonsAnswerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class LessonsService {
  private final LessonsAnswerRepository lessonsAnswerRepository;

  /**
   * Reset the answers for all lessons of a given simulation
   *
   * @param simulationId the simulation ID
   */
  public void resetLessonsAnswer(String simulationId) {
    lessonsAnswerRepository.deleteAllLessonsAnswersQuestionsCategoriesByExerciseId(simulationId);
  }
}
