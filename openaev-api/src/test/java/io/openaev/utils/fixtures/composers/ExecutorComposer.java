package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Executor;
import io.openaev.database.repository.ExecutorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ExecutorComposer extends ComposerBase<Executor> {
  @Autowired private ExecutorRepository executorRepository;

  public class Composer extends InnerComposerBase<Executor> {
    private final Executor executor;

    public Composer(Executor executor) {
      this.executor = executor;
    }

    @Override
    public Composer persist() {
      executorRepository.save(executor);
      return this;
    }

    @Override
    public Composer delete() {
      executorRepository.delete(executor);
      return this;
    }

    @Override
    public Executor get() {
      return this.executor;
    }
  }

  public Composer forExecutor(Executor executor) {
    generatedItems.add(executor);
    return new Composer(executor);
  }
}
