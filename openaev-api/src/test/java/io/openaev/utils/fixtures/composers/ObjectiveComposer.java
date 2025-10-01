package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Objective;
import io.openaev.database.repository.ObjectiveRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ObjectiveComposer extends ComposerBase<Objective> {
  @Autowired private ObjectiveRepository objectiveRepository;

  public class Composer extends InnerComposerBase<Objective> {
    private final Objective objective;

    public Composer(Objective objective) {
      this.objective = objective;
    }

    public Composer withId(String id) {
      this.objective.setId(id);
      return this;
    }

    @Override
    public Composer persist() {
      objectiveRepository.save(objective);
      return this;
    }

    @Override
    public Composer delete() {
      objectiveRepository.delete(objective);
      return this;
    }

    @Override
    public Objective get() {
      return this.objective;
    }
  }

  public Composer forObjective(Objective objective) {
    generatedItems.add(objective);
    return new Composer(objective);
  }
}
