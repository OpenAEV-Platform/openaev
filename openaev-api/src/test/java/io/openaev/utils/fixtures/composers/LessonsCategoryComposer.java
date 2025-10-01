package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.LessonsCategory;
import io.openaev.database.model.LessonsQuestion;
import io.openaev.database.repository.LessonsCategoryRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LessonsCategoryComposer extends ComposerBase<LessonsCategory> {

  @Autowired private LessonsCategoryRepository lessonsCategoryRepository;

  public class Composer extends InnerComposerBase<LessonsCategory> {
    private final LessonsCategory lessonsCategory;
    private final List<LessonsQuestionsComposer.Composer> lessonsQuestionComposers =
        new ArrayList<>();

    public Composer(LessonsCategory lessonsCategory) {
      this.lessonsCategory = lessonsCategory;
    }

    public Composer withLessonsQuestion(LessonsQuestionsComposer.Composer lessonsQuestionComposer) {
      lessonsQuestionComposers.add(lessonsQuestionComposer);
      List<LessonsQuestion> tempQuestions = this.lessonsCategory.getQuestions();
      lessonsQuestionComposer.get().setCategory(lessonsCategory);
      tempQuestions.add(lessonsQuestionComposer.get());
      this.lessonsCategory.setQuestions(tempQuestions);
      return this;
    }

    public Composer withId(String id) {
      this.lessonsCategory.setId(id);
      return this;
    }

    @Override
    public Composer persist() {
      lessonsCategoryRepository.save(lessonsCategory);
      lessonsQuestionComposers.forEach(
          composer -> {
            composer.get().setCategory(lessonsCategory);
            composer.persist();
          });
      return this;
    }

    @Override
    public Composer delete() {
      lessonsQuestionComposers.forEach(LessonsQuestionsComposer.Composer::delete);
      lessonsCategoryRepository.delete(lessonsCategory);
      return this;
    }

    @Override
    public LessonsCategory get() {
      return lessonsCategory;
    }
  }

  public Composer forLessonsCategory(LessonsCategory lessonsCategory) {
    generatedItems.add(lessonsCategory);
    return new Composer(lessonsCategory);
  }
}
