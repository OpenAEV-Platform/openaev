package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.InjectTestStatus;
import io.openaev.database.repository.InjectTestStatusRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InjectTestStatusComposer extends ComposerBase<InjectTestStatus> {

  @Autowired private InjectTestStatusRepository injectTestStatusRepository;

  public class Composer extends InnerComposerBase<InjectTestStatus> {

    private final InjectTestStatus InjectTestStatus;
    private final List<ExecutionTraceComposer.Composer> executionTracesComposer = new ArrayList<>();

    public Composer(InjectTestStatus InjectTestStatus) {
      this.InjectTestStatus = InjectTestStatus;
    }

    public Composer withExecutionTraces(List<ExecutionTraceComposer.Composer> traces) {
      traces.forEach(trace -> withExecutionTrace(trace));
      return this;
    }

    public Composer withExecutionTrace(ExecutionTraceComposer.Composer executionTrace) {
      executionTracesComposer.add(executionTrace);
      executionTrace.get().setInjectTestStatus(this.InjectTestStatus);
      this.InjectTestStatus.getTraces().add(executionTrace.get());
      return this;
    }

    @Override
    public InjectTestStatusComposer.Composer persist() {
      injectTestStatusRepository.save(InjectTestStatus);
      executionTracesComposer.forEach(ExecutionTraceComposer.Composer::persist);
      return this;
    }

    @Override
    public InjectTestStatusComposer.Composer delete() {
      executionTracesComposer.forEach(ExecutionTraceComposer.Composer::delete);
      injectTestStatusRepository.delete(InjectTestStatus);
      return this;
    }

    @Override
    public InjectTestStatus get() {
      return this.InjectTestStatus;
    }
  }

  public InjectTestStatusComposer.Composer forInjectTestStatus(InjectTestStatus InjectTestStatus) {
    generatedItems.add(InjectTestStatus);
    return new InjectTestStatusComposer.Composer(InjectTestStatus);
  }
}
