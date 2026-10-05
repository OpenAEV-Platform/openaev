package io.openaev.api.ioc_validation;

import io.openaev.api.ioc_validation.dto.IocValidationIocOutput;
import io.openaev.api.ioc_validation.dto.IocValidationOutput;
import io.openaev.api.ioc_validation.dto.IocValidationPairOutput;
import io.openaev.api.ioc_validation.dto.IocValidationSettingsInput;
import io.openaev.api.ioc_validation.dto.IocValidationSettingsOutput;
import io.openaev.api.ioc_validation.dto.IocValidationSimpleOutput;
import io.openaev.database.model.IocValidation;
import io.openaev.database.model.IocValidationIoc;
import io.openaev.database.model.IocValidationPair;
import io.openaev.database.model.IocValidationTestKind;
import io.openaev.service.stix.IocValidationSettings;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;

public class IocValidationMapper {

  private IocValidationMapper() {}

  public static IocValidationSimpleOutput toSimpleOutput(IocValidation validation) {
    return new IocValidationSimpleOutput(
        validation.getId(),
        validation.getExternalId(),
        validation.getName(),
        validation.getRequestedBy(),
        validation.getStatus(),
        List.copyOf(validation.getRequestedTestKinds()),
        validation.getIocsCount(),
        validation.getPairsCount(),
        validation.getPreventedCount(),
        validation.getDetectedCount(),
        validation.getMissedCount(),
        validation.getErrorCount(),
        validation.getScenarioId(),
        validation.getSimulationId(),
        validation.getCreatedAt(),
        validation.getUpdatedAt());
  }

  public static IocValidationOutput toOutput(IocValidation validation) {
    return new IocValidationOutput(
        validation.getId(),
        validation.getExternalId(),
        validation.getName(),
        validation.getDescription(),
        validation.getRequestedBy(),
        validation.getStatus(),
        validation.getStatusMessage(),
        List.copyOf(validation.getRequestedTestKinds()),
        List.copyOf(validation.getAllowedTestKinds()),
        validation.getIocs().stream().map(IocValidationMapper::toIocOutput).toList(),
        validation.getPairs().stream().map(IocValidationMapper::toPairOutput).toList(),
        validation.getScenarioId(),
        validation.getSimulationId(),
        validation.getOpenctiUrl(),
        validation.getDecidedById(),
        validation.getDecidedByName(),
        validation.getDecidedAt(),
        validation.getCompletedAt(),
        validation.getCreatedAt(),
        validation.getUpdatedAt());
  }

  static IocValidationIocOutput toIocOutput(IocValidationIoc ioc) {
    return new IocValidationIocOutput(
        ioc.getIndicatorRef(),
        ioc.getIndicatorName(),
        ioc.getObservableType(),
        ioc.getValue(),
        ioc.getRequestedTestKind(),
        ioc.getTestKind(),
        ioc.getFileName(),
        ioc.getHashes() == null ? null : new LinkedHashMap<>(ioc.getHashes()),
        ioc.getInjectIds() == null ? List.of() : List.copyOf(ioc.getInjectIds()),
        ioc.getMessage(),
        ioc.isRefused());
  }

  static IocValidationPairOutput toPairOutput(IocValidationPair pair) {
    return new IocValidationPairOutput(
        pair.getIndicatorRef(),
        pair.getPlatformRef(),
        pair.getDeployedOnRef(),
        pair.getPlatformName(),
        pair.getSecurityPlatformId(),
        pair.getOutcome(),
        pair.getOutcomeReason(),
        pair.getEvaluatedAt());
  }

  public static IocValidationSettingsOutput toSettingsOutput(
      IocValidationSettings settings, boolean openctiEnabled, boolean connectorRegistered) {
    return new IocValidationSettingsOutput(
        new ArrayList<>(settings.allowedTestKinds().stream().sorted().toList()),
        emptyToNull(settings.httpProxyUrl()),
        emptyToNull(settings.sinkholeAddress()),
        settings.networkPort(),
        emptyToNull(settings.assetGroupId()),
        openctiEnabled,
        connectorRegistered);
  }

  public static IocValidationSettings fromSettingsInput(IocValidationSettingsInput input) {
    EnumSet<IocValidationTestKind> allowed = EnumSet.noneOf(IocValidationTestKind.class);
    allowed.addAll(input.allowedTestKinds());
    return new IocValidationSettings(
        allowed,
        input.httpProxyUrl(),
        input.sinkholeAddress(),
        input.networkPort(),
        input.assetGroupId());
  }

  private static String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
