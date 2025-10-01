package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Pause;
import io.openaev.database.repository.PauseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PauseComposer extends ComposerBase<Pause> {
  @Autowired private PauseRepository pauseRepository;

  public class Composer extends InnerComposerBase<Pause> {
    private final Pause pause;

    public Composer(Pause pause) {
      this.pause = pause;
    }

    @Override
    public Composer persist() {
      pauseRepository.save(pause);
      return this;
    }

    @Override
    public Composer delete() {
      pauseRepository.delete(pause);
      return this;
    }

    @Override
    public Pause get() {
      return this.pause;
    }
  }

  public Composer forPause(Pause pause) {
    this.generatedItems.add(pause);
    return new Composer(pause);
  }
}
