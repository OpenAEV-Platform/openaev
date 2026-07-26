package io.openaev.utils.mapper;

import io.openaev.api.asset.dto.SecurityPlatformSimpleOutput;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.rest.document.form.RelatedEntityOutput;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class SecurityPlatformMapper {

  private SecurityPlatformMapper() {}

  public static List<SecurityPlatformSimpleOutput> toSimpleOutputs(
      List<SecurityPlatform> securityPlatforms) {
    return securityPlatforms.stream().map(SecurityPlatformMapper::toSimpleOutput).toList();
  }

  public static SecurityPlatformSimpleOutput toSimpleOutput(SecurityPlatform securityPlatform) {
    return SecurityPlatformSimpleOutput.builder()
        .id(securityPlatform.getId())
        .name(securityPlatform.getName())
        .securityPlatformType(securityPlatform.getSecurityPlatformType())
        .build();
  }

  public static Set<RelatedEntityOutput> toRelatedEntityOutputs(
      Set<SecurityPlatform> securityPlatforms) {
    return securityPlatforms.stream()
        .map(securityPlatform -> toRelatedEntityOutput(securityPlatform))
        .collect(Collectors.toSet());
  }

  private static RelatedEntityOutput toRelatedEntityOutput(SecurityPlatform securityPlatform) {
    return RelatedEntityOutput.builder()
        .id(securityPlatform.getId())
        .name(securityPlatform.getName())
        .build();
  }
}
