package io.openbas.utils.fixtures;

import static io.openbas.database.model.Command.COMMAND_TYPE;

import io.openbas.database.model.ExecutionStatus;
import io.openbas.database.model.InjectStatus;
import io.openbas.database.model.PayloadCommandBlock;
import io.openbas.database.model.StatusPayload;
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
