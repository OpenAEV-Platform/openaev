package io.openaev.service;

import io.openaev.database.model.Exercise;
import io.openaev.database.repository.ExerciseRepository;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoadService {

  private final ExerciseRepository exerciseRepository;

  @Transactional
  public Exercise exercise(@NotBlank final String exerciseId) {
    Exercise exercise = this.exerciseRepository.findById(exerciseId).orElseThrow();
    Hibernate.initialize(exercise.getTeams());
    Hibernate.initialize(exercise.getTeamUsers());
    Hibernate.initialize(exercise.getTags());
    Hibernate.initialize(exercise.getObjectives());
    Hibernate.initialize(exercise.getDocuments());
    Hibernate.initialize(exercise.getArticles());
    exercise.getArticles().forEach(article -> Hibernate.initialize(article.getDocuments()));
    Hibernate.initialize(exercise.getLessonsCategories());
    exercise
        .getLessonsCategories()
        .forEach(
            lessonsCategory -> {
              Hibernate.initialize(lessonsCategory.getQuestions());
              Hibernate.initialize(lessonsCategory.getTeams());
            });
    Hibernate.initialize(exercise.getInjects());
    return exercise;
  }
}
