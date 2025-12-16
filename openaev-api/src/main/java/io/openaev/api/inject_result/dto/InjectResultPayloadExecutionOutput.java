package io.openaev.api.inject_result.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.PayloadCommandBlock;
import io.openaev.rest.atomic_testing.form.ExecutionTraceOutput;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InjectResultPayloadExecutionOutput {

  @JsonProperty("payload_command_blocks")
  @NotEmpty
  private List<PayloadCommandBlock> payloadCommandBlocks = new ArrayList<>();

  @JsonProperty("execution_traces")
  @NotEmpty
  private Map<String, List<ExecutionTraceOutput>> traces = new HashMap<>();
}
