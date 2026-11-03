package io.openaev.database.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExecutionTraceTest {

  @Test
  @DisplayName("a trace with no agent and no context identifier is global")
  void isGlobal_no_agent_no_identifier() {
    ExecutionTrace trace = new ExecutionTrace();

    assertThat(trace.isGlobal()).isTrue();
  }

  @Test
  @DisplayName("a trace with an agent is not global")
  void isGlobal_with_agent() {
    ExecutionTrace trace = new ExecutionTrace();
    trace.setAgent(new Agent());

    assertThat(trace.isGlobal()).isFalse();
  }

  @Test
  @DisplayName("a trace with a context identifier is not global")
  void isGlobal_with_identifier() {
    ExecutionTrace trace = new ExecutionTrace();
    trace.setIdentifiers(new String[] {"asset-1"});

    assertThat(trace.isGlobal()).isFalse();
  }
}
