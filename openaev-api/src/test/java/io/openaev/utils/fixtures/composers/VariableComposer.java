package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Variable;
import io.openaev.database.repository.VariableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VariableComposer extends ComposerBase<Variable> {
  @Autowired private VariableRepository variableRepository;

  public class Composer extends InnerComposerBase<Variable> {
    private final Variable variable;

    public Composer(Variable variable) {
      this.variable = variable;
    }

    public Composer withId(String id) {
      this.variable.setId(id);
      return this;
    }

    @Override
    public Composer persist() {
      variableRepository.save(variable);
      return this;
    }

    @Override
    public Composer delete() {
      variableRepository.delete(variable);
      return this;
    }

    @Override
    public Variable get() {
      return this.variable;
    }
  }

  public Composer forVariable(Variable variable) {
    generatedItems.add(variable);
    return new Composer(variable);
  }
}
