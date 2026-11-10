package io.openaev.utils.fixtures;

import static io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig.COLLECTOR_ID;

import io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService;
import io.openaev.database.model.*;
import java.time.Instant;

public class InjectExpectationResultFixture {
  public static InjectExpectationResult getExpiredResult() {
    return InjectExpectationResult.builder()
        .date(Instant.now())
        .result(ExpectationsExpirationManagerService.EXPIRED)
        .sourceId(COLLECTOR_ID)
        .sourceName("Expiration Manager")
        .sourceType("expiration-type")
        .sourceAssetId(null)
        .sourcePlatform(null)
        .score(0.0)
        .metadata(null)
        .build();
  }
}
