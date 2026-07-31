package io.openaev.rest.inject;

import static io.openaev.database.model.ExecutionTraceAction.EXECUTION;
import static io.openaev.utils.mapper.InjectStatusMapper.toExecutionTracesOutput;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.api.inject_result.dto.InjectResultPayloadExecutionOutput;
import io.openaev.api.inject_result.dto.InjectResultPayloadExecutionOutput.InjectResultPayloadExecutionOutputBuilder;
import io.openaev.database.model.Agent;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.PayloadArgument;
import io.openaev.database.model.PayloadCommandBlock;
import io.openaev.database.model.StatusPayload;
import io.openaev.rest.atomic_testing.form.ExecutionTraceOutput;
import io.openaev.rest.inject.service.ExecutableInjectService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.utils.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class InjectExecutionResultService {

  // Synthetic grouping key for traces produced by agentless executions (no agent attached, e.g. an
  // injector scanning a non-endpoint asset).
  static final String AGENTLESS_TRACE_KEY = "agentless";

  private final InjectService injectService;
  private final InjectStatusService injectStatusService;
  private final ExecutableInjectService executableInjectService;

  public InjectResultPayloadExecutionOutput injectExecutionResultPayload(
      @NotBlank final String injectId,
      @NotBlank final String targetId,
      @NotNull final TargetType targetType) {
    InjectStatus injectStatus = this.injectStatusService.findInjectStatusByInjectId(injectId);
    List<PayloadCommandBlock> payloadCommandBlocks =
        Optional.of(injectStatus)
            .map(InjectStatus::getPayloadOutput)
            .map(StatusPayload::getPayloadCommandBlocks)
            .orElse(new ArrayList<>());
    InjectResultPayloadExecutionOutputBuilder output =
        InjectResultPayloadExecutionOutput.builder()
            .payloadCommandBlocks(resolveArgumentPlaceholders(injectId, payloadCommandBlocks));

    // Group execution traces per target key. Agent-based executions are keyed by agent id;
    // agentless
    // executions (e.g. an injector scanning a non-endpoint asset such as a web application) carry
    // no
    // agent, so they are bucketed under a single synthetic key. Keying on the agent id directly
    // used
    // to throw a NullPointerException for agentless traces and also dropped them from the response,
    // leaving the terminal view empty.
    List<ExecutionTrace> traces =
        injectService.getInjectTracesFromInjectAndTarget(injectId, targetId, targetType);

    Set<String> targetKeys =
        traces.stream().map(InjectExecutionResultService::traceKey).collect(toSet());

    Map<String, List<ExecutionTraceOutput>> executionByKey =
        toExecutionTracesOutput(
                traces.stream().filter(t -> EXECUTION.equals(t.getAction())).toList())
            .stream()
            .collect(
                groupingBy(t -> t.getAgent() != null ? t.getAgent().getId() : AGENTLESS_TRACE_KEY));

    Map<String, List<ExecutionTraceOutput>> result = new LinkedHashMap<>();

    targetKeys.forEach(
        key -> result.put(key, new ArrayList<>(executionByKey.getOrDefault(key, List.of()))));

    output.traces(result);
    return output.build();
  }

  /**
   * Resolves every {@code #{argumentKey}} placeholder in the payload's command/cleanup templates
   * (e.g. {@code echo #{host}:#{port}}) to its actual value, mirroring what is actually sent for
   * execution ({@link ExecutableInjectService#replaceArgumentsByValue}) instead of the raw template
   * — so the terminal view shows {@code echo localhost:22} rather than the unresolved placeholders.
   * Falls back to the raw blocks if the inject has no payload to resolve against.
   */
  private List<PayloadCommandBlock> resolveArgumentPlaceholders(
      final String injectId, final List<PayloadCommandBlock> payloadCommandBlocks) {
    if (payloadCommandBlocks.isEmpty()) {
      return payloadCommandBlocks;
    }
    Inject inject = injectService.inject(injectId);
    return inject
        .getPayload()
        .map(
            payload -> {
              List<PayloadArgument> arguments =
                  Optional.ofNullable(payload.getArguments()).orElse(List.of());
              List<ObjectNode> injectorContractFields =
                  inject
                      .getInjectorContract()
                      .map(
                          contract -> {
                            JsonNode fields = contract.getConvertedContent().get("fields");
                            return fields == null
                                ? List.<ObjectNode>of()
                                : StreamSupport.stream(fields.spliterator(), false)
                                    .map(ObjectNode.class::cast)
                                    .toList();
                          })
                      .orElse(List.of());
              return payloadCommandBlocks.stream()
                  .map(
                      block -> {
                        String resolvedContent =
                            executableInjectService.replaceArgumentsByValue(
                                block.getContent(),
                                arguments,
                                injectorContractFields,
                                inject.getContent());
                        List<String> resolvedCleanup =
                            block.getCleanupCommand() == null
                                ? null
                                : block.getCleanupCommand().stream()
                                    .map(
                                        cmd ->
                                            executableInjectService.replaceArgumentsByValue(
                                                cmd,
                                                arguments,
                                                injectorContractFields,
                                                inject.getContent()))
                                    .toList();
                        return new PayloadCommandBlock(
                            block.getExecutor(), resolvedContent, resolvedCleanup);
                      })
                  .toList();
            })
        .orElse(payloadCommandBlocks);
  }

  private static String traceKey(final ExecutionTrace trace) {
    Agent agent = trace.getAgent();
    return agent != null ? agent.getId() : AGENTLESS_TRACE_KEY;
  }
}
