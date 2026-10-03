package io.openaev.service.stix;

import io.openaev.database.model.IocValidation;
import io.openaev.database.model.IocValidationOutcome;
import io.openaev.database.model.IocValidationPair;
import io.openaev.stix.objects.Bundle;
import io.openaev.stix.objects.DomainObject;
import io.openaev.stix.objects.ObjectBase;
import io.openaev.stix.objects.RelationshipObject;
import io.openaev.stix.objects.constants.CommonProperties;
import io.openaev.stix.objects.constants.ObjectTypes;
import io.openaev.stix.types.BaseType;
import io.openaev.stix.types.Boolean;
import io.openaev.stix.types.Identifier;
import io.openaev.stix.types.StixString;
import io.openaev.stix.types.Timestamp;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the result bundle pushed to OpenCTI for a finished IOC validation (contract section 5).
 *
 * <p>Per pair: the {@code deployed-on} relationship, re-emitted under its own OpenCTI STIX id so
 * the worker upserts it with {@code validation_status}, {@code last_validation_at} and {@code
 * validation_run_id}; and, for every pair the test actually evaluated, one sighting of the
 * indicator on the security platform, negative when it was missed. The indicators and platform
 * identities are not re-sent: they are OpenCTI's own objects, referenced by their STIX ids.
 */
public final class IocValidationResultBundle {

  public static final String DEPLOYED_ON = "deployed-on";
  static final String VALIDATION_STATUS = "validation_status";
  static final String LAST_VALIDATION_AT = "last_validation_at";
  static final String VALIDATION_RUN_ID = "validation_run_id";
  static final String SIGHTING_OF_REF = "sighting_of_ref";
  static final String WHERE_SIGHTED_REFS = "where_sighted_refs";
  static final String FIRST_SEEN = "first_seen";
  static final String LAST_SEEN = "last_seen";
  static final String COUNT = "count";
  static final String NEGATIVE = "x_opencti_negative";
  private static final String SPEC_VERSION = "2.1";

  private IocValidationResultBundle() {}

  /**
   * The result bundle of a validation whose pairs all have an outcome.
   *
   * @param validation the finished validation
   * @param now fallback evaluation time for pairs without one
   */
  public static Bundle build(IocValidation validation, Instant now) {
    List<ObjectBase> objects = new ArrayList<>();
    for (IocValidationPair pair : validation.getPairs()) {
      if (pair.getOutcome() == null) {
        continue;
      }
      Instant evaluatedAt = pair.getEvaluatedAt() == null ? now : pair.getEvaluatedAt();
      objects.add(relationship(pair, validation.getExternalId(), evaluatedAt));
      if (pair.getOutcome().isEvaluated()) {
        objects.add(sighting(pair, validation.getExternalId(), evaluatedAt));
      }
    }
    return new Bundle(new Identifier("bundle", UUID.randomUUID().toString()), objects);
  }

  private static RelationshipObject relationship(
      IocValidationPair pair, String requestId, Instant evaluatedAt) {
    Map<String, BaseType<?>> properties = new HashMap<>();
    properties.put(CommonProperties.ID.toString(), new Identifier(pair.getDeployedOnRef()));
    properties.put(
        CommonProperties.TYPE.toString(), new StixString(ObjectTypes.RELATIONSHIP.toString()));
    properties.put(CommonProperties.SPEC_VERSION.toString(), new StixString(SPEC_VERSION));
    properties.put(
        RelationshipObject.Properties.RELATIONSHIP_TYPE.toString(), new StixString(DEPLOYED_ON));
    properties.put(
        RelationshipObject.Properties.SOURCE_REF.toString(),
        new Identifier(pair.getIndicatorRef()));
    properties.put(
        RelationshipObject.Properties.TARGET_REF.toString(), new Identifier(pair.getPlatformRef()));
    properties.put(VALIDATION_STATUS, new StixString(pair.getOutcome().toStix()));
    properties.put(LAST_VALIDATION_AT, new Timestamp(evaluatedAt));
    properties.put(VALIDATION_RUN_ID, new StixString(requestId));
    return new RelationshipObject(properties);
  }

  private static DomainObject sighting(
      IocValidationPair pair, String requestId, Instant evaluatedAt) {
    Map<String, BaseType<?>> properties = new HashMap<>();
    properties.put(
        CommonProperties.ID.toString(),
        new Identifier(ObjectTypes.SIGHTING.toString(), sightingUuid(pair, requestId)));
    properties.put(
        CommonProperties.TYPE.toString(), new StixString(ObjectTypes.SIGHTING.toString()));
    properties.put(CommonProperties.SPEC_VERSION.toString(), new StixString(SPEC_VERSION));
    properties.put(SIGHTING_OF_REF, new Identifier(pair.getIndicatorRef()));
    properties.put(
        WHERE_SIGHTED_REFS,
        new io.openaev.stix.types.List<>(List.of(new Identifier(pair.getPlatformRef()))));
    properties.put(FIRST_SEEN, new Timestamp(evaluatedAt));
    properties.put(LAST_SEEN, new Timestamp(evaluatedAt));
    properties.put(COUNT, new io.openaev.stix.types.Integer(1));
    properties.put(NEGATIVE, new Boolean(pair.getOutcome() == IocValidationOutcome.MISSED));
    return new DomainObject(properties);
  }

  /** Deterministic per (deployment, request): a replayed push upserts the same sighting. */
  static String sightingUuid(IocValidationPair pair, String requestId) {
    return UUID.nameUUIDFromBytes(
            ("ioc-validation:" + requestId + ":" + pair.getDeployedOnRef())
                .getBytes(StandardCharsets.UTF_8))
        .toString();
  }
}
