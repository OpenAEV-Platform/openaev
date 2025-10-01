package io.openaev.utils.fixtures;

import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.InjectTestStatus;
import java.time.Instant;

public class InjectTestStatusFixture {

  private static InjectTestStatus createInjectTestStatus(ExecutionStatus status) {
    InjectTestStatus injectTestStatus = new InjectTestStatus();
    injectTestStatus.setTrackingSentDate(Instant.now());
    injectTestStatus.setName(status);
    return injectTestStatus;
  }

  public static InjectTestStatus createSuccessInjectStatus() {
    return createInjectTestStatus(ExecutionStatus.SUCCESS);
  }
}
