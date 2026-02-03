package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Condition;
import io.openaev.database.model.Step;
import io.openaev.database.repository.ConditionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ConditionComposer extends ComposerBase<Condition> {

  @Autowired private ConditionRepository conditionRepository;

  public class Composer extends InnerComposerBase<Condition> {

    private final Condition condition;

    public Composer(Condition condition) {
      this.condition = condition;
    }

    /** Sets the step to which this condition belongs. */
    public Composer withStep(Step step) {
      condition.setStep(step);
      return this;
    }

    /** Sets the source step for this condition. */
    public Composer withStepFrom(Step stepFrom) {
      condition.setStepFrom(stepFrom);
      return this;
    }

    /** Sets the parent condition and updates its children list. */
    public Composer withParentCondition(Condition parent) {
      condition.setConditionParent(parent);
      parent.getConditionChildren().add(condition);
      return this;
    }

    /** Saves the condition in the database. */
    @Override
    public ConditionComposer.Composer persist() {
      conditionRepository.save(condition);
      return this;
    }

    /** Deletes the condition from the database. */
    @Override
    public ConditionComposer.Composer delete() {
      conditionRepository.delete(condition);
      return this;
    }

    @Override
    public Condition get() {
      return condition;
    }
  }

  /** Entry point for composing a condition. */
  public ConditionComposer.Composer forCondition(Condition condition) {
    generatedItems.add(condition);
    return new Composer(condition);
  }
}
