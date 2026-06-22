package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.LessonsAnswer;
import io.openaev.database.model.LessonsQuestion;
import io.openaev.database.repository.LessonsQuestionRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LessonsQuestionsComposer extends ComposerBase<LessonsQuestion> {

  @Autowired private LessonsQuestionRepository lessonsQuestionRepository;

  public class Composer extends InnerComposerBase<LessonsQuestion> {
    private final LessonsQuestion lessonsQuestion;
    private final List<LessonsAnswersComposer.Composer> lessonsAnswerComposers = new ArrayList<>();

    public Composer(LessonsQuestion lessonsQuestion) {
      this.lessonsQuestion = lessonsQuestion;
    }

    public Composer withId(String id) {
      this.lessonsQuestion.setId(id);
      return this;
    }

    public Composer withAnswer(LessonsAnswersComposer.Composer lessonsAnswerWrapper) {
      lessonsAnswerComposers.add(lessonsAnswerWrapper);
      List<LessonsAnswer> tempAnswers = this.lessonsQuestion.getAnswers();
      tempAnswers.add(lessonsAnswerWrapper.get());
      lessonsAnswerWrapper.get().setQuestion(this.lessonsQuestion);
      this.lessonsQuestion.setAnswers(tempAnswers);
      return this;
    }

    @Override
    public Composer persist() {
      lessonsAnswerComposers.forEach(LessonsAnswersComposer.Composer::persist);
      lessonsQuestionRepository.save(lessonsQuestion);
      return this;
    }

    @Override
    public Composer delete() {
      lessonsAnswerComposers.forEach(LessonsAnswersComposer.Composer::delete);
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
