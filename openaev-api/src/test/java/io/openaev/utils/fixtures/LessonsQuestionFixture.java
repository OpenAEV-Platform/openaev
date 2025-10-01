package io.openaev.utils.fixtures;

import io.openaev.database.model.LessonsQuestion;
import java.util.UUID;

public class LessonsQuestionFixture {

  public static LessonsQuestion createDefaultLessonsQuestion() {
    LessonsQuestion lessonsQuestion = createLessonsQuestionWithDefaultContent();
    lessonsQuestion.setExplanation("Default question for testing");
    lessonsQuestion.setOrder(0);
    return lessonsQuestion;
  }

  public static LessonsQuestion createLessonsQuestion() {
    LessonsQuestion lessonsQuestion = createLessonsQuestionWithContent("Question content");
    lessonsQuestion.setExplanation("Explanation");
    lessonsQuestion.setOrder(0);
    return lessonsQuestion;
  }

  private static LessonsQuestion createLessonsQuestionWithDefaultContent() {
    return createLessonsQuestionWithContent(null);
  }

  private static LessonsQuestion createLessonsQuestionWithContent(String content) {
    String new_content =
        content == null ? "lessons_question-%s".formatted(UUID.randomUUID()) : content;
    LessonsQuestion lessonsQuestion = new LessonsQuestion();
    lessonsQuestion.setContent(new_content);
    return lessonsQuestion;
  }
}
