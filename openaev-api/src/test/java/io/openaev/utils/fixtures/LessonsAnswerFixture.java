package io.openaev.utils.fixtures;

import io.openaev.database.model.LessonsAnswer;
import java.time.Instant;

public class LessonsAnswerFixture {

  public static LessonsAnswer createLessonsAnswer() {
    LessonsAnswer lessonsAnswer = new LessonsAnswer();
    lessonsAnswer.setPositive("Positive");
    lessonsAnswer.setNegative("Negative");
    lessonsAnswer.setScore(10);
    lessonsAnswer.setCreated(Instant.now());
    lessonsAnswer.setUpdated(Instant.now());
    return lessonsAnswer;
  }
}
