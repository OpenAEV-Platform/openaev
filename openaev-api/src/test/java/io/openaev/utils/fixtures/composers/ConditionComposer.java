package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Step;
import io.openaev.database.repository.StepRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ConditionComposer extends ComposerBase<Step> {

  @Autowired private StepRepository stepRepository;

  public class Composer extends InnerComposerBase<Step> {

    private final Step step;

    public Composer(Step step) {
      this.step = step;
    }

    @Override
    public ConditionComposer.Composer persist() {
      stepRepository.save(step);
      return this;
    }

    @Override
    public ConditionComposer.Composer delete() {
      stepRepository.delete(step);
      return this;
    }

    @Override
    public Step get() {
      return this.step;
    }
  }

  public ConditionComposer.Composer forStep(Step step) {
    generatedItems.add(step);
    return new ConditionComposer.Composer(step);
  }
}
