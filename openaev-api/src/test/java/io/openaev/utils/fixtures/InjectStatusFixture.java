package io.openaev.utils.fixtures;

import static io.openaev.database.model.Command.COMMAND_TYPE;

import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.PayloadCommandBlock;
import io.openaev.database.model.StatusPayload;
import java.time.Instant;
import java.util.List;

public class InjectStatusFixture {

  private static InjectStatus createInjectStatus(ExecutionStatus status) {
    InjectStatus injectStatus = new InjectStatus();
    injectStatus.setTrackingSentDate(Instant.now());
    injectStatus.setName(status);
    injectStatus.setPayloadOutput(
        new StatusPayload(
            null,
            null,
            COMMAND_TYPE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(new PayloadCommandBlock("cmd", "content", List.of("clean cmd"))),
            "cmd"));
    return injectStatus;
  }

  public static InjectStatus createPendingInjectStatus() {
    return createInjectStatus(ExecutionStatus.PENDING);
  }

  public static InjectStatus createDraftInjectStatus() {
    return createInjectStatus(ExecutionStatus.DRAFT);
  }

  public static InjectStatus createQueuingInjectStatus() {
    return createInjectStatus(ExecutionStatus.QUEUING);
  }

  public static InjectStatus createSuccessStatus() {
    return createInjectStatus(ExecutionStatus.SUCCESS);
  }
}
