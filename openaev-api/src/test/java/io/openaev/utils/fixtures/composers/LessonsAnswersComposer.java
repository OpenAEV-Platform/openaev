package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.LessonsAnswer;
import io.openaev.database.repository.LessonsAnswerRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LessonsAnswersComposer extends ComposerBase<LessonsAnswer> {

  @Autowired private LessonsAnswerRepository lessonsAnswerRepository;

  public class Composer extends InnerComposerBase<LessonsAnswer> {
    private final LessonsAnswer lessonAnswer;
    private Optional<UserComposer.Composer> userComposer = Optional.empty();

    public Composer(LessonsAnswer lessonAnswer) {
      this.lessonAnswer = lessonAnswer;
    }

    public Composer withUser(UserComposer.Composer userWrapper) {
      userComposer = Optional.of(userWrapper);
      this.lessonAnswer.setUser(userWrapper.get());
      return this;
    }

    @Override
    public Composer persist() {
      userComposer.ifPresent(UserComposer.Composer::persist);
      lessonsAnswerRepository.save(lessonAnswer);
      return this;
    }

    @Override
    public Composer delete() {
      userComposer.ifPresent(UserComposer.Composer::delete);
      lessonsAnswerRepository.delete(lessonAnswer);
      return this;
    }

    @Override
    public LessonsAnswer get() {
      return this.lessonAnswer;
    }
  }

  public Composer forLessonsAnswer(LessonsAnswer lessonsAnswer) {
    generatedItems.add(lessonsAnswer);
    return new Composer(lessonsAnswer);
  }
}
