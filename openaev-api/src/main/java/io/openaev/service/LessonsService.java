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

  public void resetLessonsAnswer(String exerciseId) {
    List<LessonsAnswer> lessonsAnswers =
        lessonsCategoryRepository
            .findAll(LessonsCategorySpecification.fromExercise(exerciseId))
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

  public void removeTeamsForExercise(String exerciseId, List<String> teamIds) {
    this.lessonsCategoryRepository.removeTeamsForExercise(exerciseId, teamIds);
  }
}
