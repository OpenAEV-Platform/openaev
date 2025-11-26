package io.openaev.api.inject_result.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.PayloadCommandBlock;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InjectResultPayloadExecutionOutput {

  @JsonProperty("payload_command_blocks")
  @NotEmpty
  private List<PayloadCommandBlock> payloadCommandBlocks = new ArrayList<>();

  @JsonProperty("execution_execution_traces")
  @NotEmpty
  private List<ExecutionTrace> traces = new ArrayList<>();
}
