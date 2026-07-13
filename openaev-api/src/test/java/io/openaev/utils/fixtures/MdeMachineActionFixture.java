package io.openaev.utils.fixtures;

import io.openaev.executors.mde.model.MdeMachineAction;
import java.time.Instant;
import java.util.UUID;

public class MdeMachineActionFixture {

  public static MdeMachineAction createPendingMachineAction(String creationDateTimeUtc) {
    MdeMachineAction action = new MdeMachineAction();
    action.setId("action-" + UUID.randomUUID());
    action.setType("LiveResponse");
    action.setStatus("Pending");
    action.setCreationDateTimeUtc(creationDateTimeUtc);
    return action;
  }

  public static MdeMachineAction createPendingMachineAction(Instant creationDateTimeUtc) {
    // Instant#toString yields an ISO-8601 UTC value that MdeExecutorClient parses with
    // Instant#parse.
    return createPendingMachineAction(creationDateTimeUtc.toString());
  }
}
