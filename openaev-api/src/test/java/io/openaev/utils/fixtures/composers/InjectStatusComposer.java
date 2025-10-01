package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.InjectStatus;
import io.openaev.database.repository.InjectStatusRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InjectStatusComposer extends ComposerBase<InjectStatus> {
  @Autowired private InjectStatusRepository injectStatusRepository;

  public class Composer extends InnerComposerBase<InjectStatus> {
    private final InjectStatus injectStatus;
    private final List<ExecutionTraceComposer.Composer> executionTracesComposer = new ArrayList<>();

    public Composer(InjectStatus injectStatus) {
      this.injectStatus = injectStatus;
    }

    public Composer withExecutionTraces(List<ExecutionTraceComposer.Composer> traces) {
      traces.forEach(trace -> withExecutionTrace(trace));
      return this;
    }

    public Composer withExecutionTrace(ExecutionTraceComposer.Composer executionTrace) {
      executionTracesComposer.add(executionTrace);
      executionTrace.get().setInjectStatus(this.injectStatus);
      this.injectStatus.getTraces().add(executionTrace.get());
      return this;
    }

    @Override
    public InjectStatusComposer.Composer persist() {
      injectStatusRepository.save(injectStatus);
      executionTracesComposer.forEach(ExecutionTraceComposer.Composer::persist);
      return this;
    }

    @Override
    public InjectStatusComposer.Composer delete() {
      executionTracesComposer.forEach(ExecutionTraceComposer.Composer::delete);
      injectStatusRepository.delete(injectStatus);
      return this;
    }

    @Override
    public InjectStatus get() {
      return this.injectStatus;
    }
  }

  public InjectStatusComposer.Composer forInjectStatus(InjectStatus injectStatus) {
    generatedItems.add(injectStatus);
    return new InjectStatusComposer.Composer(injectStatus);
  }
}
