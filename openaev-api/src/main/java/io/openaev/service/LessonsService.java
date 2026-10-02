package io.openaev.service;

import io.openaev.database.model.LessonsAnswer;
import io.openaev.database.model.LessonsCategory;
import io.openaev.database.model.Team;
import io.openaev.database.repository.LessonsAnswerRepository;
import io.openaev.database.repository.LessonsCategoryRepository;
import io.openaev.database.repository.LessonsQuestionRepository;
import io.openaev.database.specification.LessonsAnswerSpecification;
import io.openaev.database.specification.LessonsCategorySpecification;
import io.openaev.database.specification.LessonsQuestionSpecification;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    if (!lessonsAnswers.isEmpty())
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

  /**
   * Reconciles lesson targets against the current workflow scope so removed teams disappear from
   * the UI as soon as the scope changes.
   */
  @Transactional
  public void pruneTeamsForSimulation(String simulationId, List<String> scopedTeamIds) {
    pruneTeams(
        lessonsCategoryRepository.findAll(LessonsCategorySpecification.fromExercise(simulationId)),
        scopedTeamIds);
  }

  /**
   * Reconciles lesson targets against the current workflow scope so removed teams disappear from
   * the UI as soon as the scope changes.
   */
  @Transactional
  public void pruneTeamsForScenario(String scenarioId, List<String> scopedTeamIds) {
    pruneTeams(
        lessonsCategoryRepository.findAll(LessonsCategorySpecification.fromScenario(scenarioId)),
        scopedTeamIds);
  }

  private void pruneTeams(List<LessonsCategory> categories, List<String> scopedTeamIds) {
    Set<String> validTeamIds = new HashSet<>(scopedTeamIds == null ? List.of() : scopedTeamIds);
    for (LessonsCategory category : categories) {
      List<Team> currentTeams = category.getTeams() == null ? List.of() : category.getTeams();
      List<Team> remainingTeams =
          currentTeams.stream().filter(team -> validTeamIds.contains(team.getId())).toList();
      if (remainingTeams.size() != currentTeams.size()) {
        category.setTeams(new ArrayList<>(remainingTeams));
        lessonsCategoryRepository.save(category);
      }
    }
  }
}
