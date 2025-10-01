package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.LessonsQuestion;
import io.openaev.database.repository.LessonsQuestionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LessonsQuestionsComposer extends ComposerBase<LessonsQuestion> {

  @Autowired private LessonsQuestionRepository lessonsQuestionRepository;

  public class Composer extends InnerComposerBase<LessonsQuestion> {
    private final LessonsQuestion lessonsQuestion;

    public Composer(LessonsQuestion lessonsQuestion) {
      this.lessonsQuestion = lessonsQuestion;
    }

    public Composer withId(String id) {
      this.lessonsQuestion.setId(id);
      return this;
    }

    @Override
    public Composer persist() {
      lessonsQuestionRepository.save(lessonsQuestion);
      return this;
    }

    @Override
    public Composer delete() {
      lessonsQuestionRepository.delete(lessonsQuestion);
      return this;
    }

    @Override
    public LessonsQuestion get() {
      return this.lessonsQuestion;
    }
  }

  public Composer forLessonsQuestion(LessonsQuestion lessonsQuestion) {
    generatedItems.add(lessonsQuestion);
    return new Composer(lessonsQuestion);
  }
}
