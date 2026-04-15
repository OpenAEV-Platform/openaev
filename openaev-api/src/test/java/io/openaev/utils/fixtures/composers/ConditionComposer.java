package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Condition;
import io.openaev.database.model.Step;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.service.chaining.ConditionService;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ConditionComposer extends ComposerBase<Condition> {

  @Autowired private ConditionRepository conditionRepository;
  @Autowired private ConditionService conditionService;

  public class Composer extends InnerComposerBase<Condition> {

    private final Condition condition;
    private Optional<StepComposer.Composer> stepComposer = Optional.empty();
    private Optional<ConditionComposer.Composer> conditionComposer = Optional.empty();

    public Composer(Condition condition) {
      this.condition = condition;
    }

    /** Sets the step to which this condition belongs. */
    public Composer withStep(StepComposer.Composer stepOriginComposer) {
      Step step = stepOriginComposer.get();
      conditionService.linkToStep(condition, step, true);
      return this;
    }

    /** Sets the source step for this condition. */
    public Composer withStepFrom(StepComposer.Composer stepFromComposer) {
      Step stepFrom = stepFromComposer.get();
      condition.setStepFrom(stepFrom);
      return this;
    }

    /** Sets the parent condition and updates its children list. */
    public Composer withParentCondition(ConditionComposer.Composer parentComposer) {
      Condition parent = parentComposer.get();
      condition.setConditionParent(parent);
      parent.getConditionChildren().add(condition);
      return this;
    }

    /** Saves the condition in the database. */
    @Override
    public ConditionComposer.Composer persist() {
      stepComposer.ifPresent(StepComposer.Composer::persist);
      conditionComposer.ifPresent(ConditionComposer.Composer::persist);
      conditionRepository.save(condition);
      return this;
    }

    /** Deletes the condition from the database. */
    @Override
    public ConditionComposer.Composer delete() {
      conditionRepository.delete(condition);
      stepComposer.ifPresent(StepComposer.Composer::delete);
      conditionComposer.ifPresent(ConditionComposer.Composer::delete);
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
