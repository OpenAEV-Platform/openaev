package io.openaev.utils.fixtures;

import static io.openaev.expectation.ExpectationType.*;

import io.openaev.database.model.InjectExpectation;
import io.openaev.expectation.ExpectationType;
import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import io.openaev.utils.InjectExpectationResultUtils.ResultDistribution;
import java.util.List;

public class ExpectationResultByTypeFixture {
  public static ExpectationResultsByType createDefaultExpectationResultsByType(
      ExpectationType type,
      InjectExpectation.EXPECTATION_STATUS avgResult,
      int successCount,
      int pendingCount,
      int partialCount,
      int failureCount) {
    return new ExpectationResultsByType(
        type,
        avgResult,
        List.of(
            new ResultDistribution(SUCCESS_ID, type.successLabel, successCount),
            new ResultDistribution(PENDING_ID, type.pendingLabel, pendingCount),
            new ResultDistribution(PARTIAL_ID, type.partialLabel, partialCount),
            new ResultDistribution(FAILED_ID, type.failureLabel, failureCount)));
  }
}
