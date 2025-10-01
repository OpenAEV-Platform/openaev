package io.openaev.utils.fixtures;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.LessonsCategory;
import io.openaev.database.model.Team;
import java.util.List;

public class ExerciseLessonsCategoryFixture {

  public static final String LESSON_CATEGORY_NAME = "Category name";
  public static final String LESSON_CATEGORY_DESCRIPTION = "Category description";
  public static final int LESSON_CATEGORY_ORDER = 0;

  public static LessonsCategory getLessonsCategory(Exercise exercise, List<Team> categoryTeams) {
    LessonsCategory lessonsCategory = new LessonsCategory();
    lessonsCategory.setExercise(exercise);
    lessonsCategory.setName(LESSON_CATEGORY_NAME);
    lessonsCategory.setDescription(LESSON_CATEGORY_DESCRIPTION);
    lessonsCategory.setOrder(LESSON_CATEGORY_ORDER);
    lessonsCategory.setTeams(categoryTeams);
    return lessonsCategory;
  }
}
