package io.openaev.service.chaining;

import io.openaev.database.model.PrimitiveType;
import io.openaev.utils.IpAddressUtils;
import java.util.Locale;
import org.apache.commons.validator.routines.DomainValidator;

/** Validation rules for primitive chaining values before they are persisted in workflow state. */
public final class PrimitiveValueValidator {

  private static final DomainValidator DOMAIN_VALIDATOR = DomainValidator.getInstance(true);

  private PrimitiveValueValidator() {}

  /**
   * Validates a value against the expected primitive type.
   *
   * @param primitiveType primitive type being persisted
   * @param value candidate value
   * @param context precomputed scope validation context
   * @return true when the value is accepted for this primitive type
   */
  public static boolean isValidForPrimitiveType(
      PrimitiveType primitiveType, String value, PrimitiveValidationContext context) {
    if (value == null) {
      return false;
    }
    return switch (primitiveType) {
      case IPv4 -> IpAddressUtils.isIpv4Address(value) && isIpAllowedByScope(value, context);
      case IPv6 -> IpAddressUtils.isIpv6Address(value) && isIpAllowedByScope(value, context);
      case Domain -> DOMAIN_VALIDATOR.isValid(value) && isDomainAllowedByScope(value, context);
      case IpSubnet ->
          (IpAddressUtils.isIpv4Subnet(value) || IpAddressUtils.isIpv6Subnet(value))
              && isSubnetAllowedByScope(value, context);
      case AssetId -> isAssetIdAllowedByScope(value, context);
      case AssetGroupId -> isAssetGroupIdAllowedByScope(value, context);
      default -> true;
    };
  }

  private static boolean isAssetIdAllowedByScope(String id, PrimitiveValidationContext context) {
    if (context.denylistedAssetIds().contains(id)) {
      return false;
    }
    if (context.allowlistedAssetIds().isEmpty()) {
      return true;
    }
    return context.allowlistedAssetIds().contains(id);
  }

  private static boolean isAssetGroupIdAllowedByScope(
      String id, PrimitiveValidationContext context) {
    if (context.denylistedAssetGroupIds().contains(id)) {
      return false;
    }
    if (context.allowlistedAssetGroupIds().isEmpty()) {
      return true;
    }
    return context.allowlistedAssetGroupIds().contains(id);
  }

  private static boolean isIpAllowedByScope(String ip, PrimitiveValidationContext context) {
    if (context.denylistedIps().contains(ip)
        || context.denylistedSubnets().stream()
            .anyMatch(subnet -> IpAddressUtils.isIpInSubnet(ip, subnet))) {
      return false;
    }
    boolean hasAllowlist =
        !context.allowlistedIps().isEmpty() || !context.allowlistedSubnets().isEmpty();
    if (!hasAllowlist) {
      return true;
    }
    return context.allowlistedIps().contains(ip)
        || context.allowlistedSubnets().stream()
            .anyMatch(subnet -> IpAddressUtils.isIpInSubnet(ip, subnet));
  }

  private static boolean isSubnetAllowedByScope(String subnet, PrimitiveValidationContext context) {
    if (context.denylistedSubnets().contains(subnet)) {
      return false;
    }
    if (context.allowlistedSubnets().isEmpty()) {
      return true;
    }
    return context.allowlistedSubnets().contains(subnet);
  }

  private static boolean isDomainAllowedByScope(String domain, PrimitiveValidationContext context) {
    String normalizedDomain = domain.toLowerCase(Locale.ROOT);
    if (context.denylistedDomains().contains(normalizedDomain)) {
      return false;
    }
    if (context.allowlistedDomains().isEmpty()) {
      return true;
    }
    return context.allowlistedDomains().contains(normalizedDomain);
  }
}
