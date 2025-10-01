package io.openaev.utils.fixtures;

import io.openaev.database.model.LessonsCategory;
import java.util.UUID;

public class LessonsCategoryFixture {

  public static final String CATEGORY_NAME = "Category test";

  public static LessonsCategory createDefaultLessonsCategory() {
    LessonsCategory lessonsCategory = createLessonCategoryWithDefaultName();
    lessonsCategory.setDescription("Default lessons category for tests");
    lessonsCategory.setOrder(1);
    return lessonsCategory;
  }

  public static LessonsCategory createLessonCategory() {
    LessonsCategory lessonCategory = createLessonCategoryWithName(CATEGORY_NAME);
    lessonCategory.setDescription("This is a description");
    lessonCategory.setOrder(1);
    return lessonCategory;
  }

  private static LessonsCategory createLessonCategoryWithDefaultName() {
    return createLessonCategoryWithName(null);
  }

  private static LessonsCategory createLessonCategoryWithName(String name) {
    String new_name = name == null ? "lessons_category-%s".formatted(UUID.randomUUID()) : name;
    LessonsCategory lessonsCategory = new LessonsCategory();
    lessonsCategory.setName(new_name);
    return lessonsCategory;
  }
}
