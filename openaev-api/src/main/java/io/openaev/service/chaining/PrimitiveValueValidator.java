package io.openaev.service.chaining;

import io.openaev.database.model.PrimitiveType;
import io.openaev.utils.IpAddressUtils;
import java.util.Locale;
import org.apache.commons.validator.routines.DomainValidator;

/**
 * Acceptance rules for primitive chaining values before they are persisted in workflow state.
 *
 * <p>Validation is intentionally scope-driven: types that can be restricted by workflow scope rules
 * (IPs, subnets, domains, asset and asset-group IDs) get a format check plus an allowlist/denylist
 * check. Port and Number get a cheap format sanity check. Port validation is also reused by output
 * processors before generating findings. Every other primitive type has no defined rule yet and is
 * accepted as-is - hence the "accepted" naming, to make explicit that this is not a full semantic
 * validation of every primitive type.
 */
public final class PrimitiveValueValidator {

  private static final DomainValidator DOMAIN_VALIDATOR = DomainValidator.getInstance(true);

  private static final int MAX_PORT = 65535;

  private PrimitiveValueValidator() {}

  /**
   * Decides whether a value is accepted into workflow state for the given primitive type.
   *
   * <p>Scope-restrictable types are checked for format and against the workflow scope rules. Port
   * and Number are checked for format only. Types without defined rules are always accepted.
   *
   * @param primitiveType primitive type being persisted
   * @param value candidate value
   * @param context precomputed scope validation context
   * @return true when the value is accepted for this primitive type
   */
  public static boolean isAcceptedForPrimitiveType(
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
      case Port -> isValidPort(value);
      case Number -> isValidNumber(value);
      default -> true;
    };
  }

  public static boolean isValidPort(String value) {
    try {
      int port = Integer.parseInt(value.trim());
      return port >= 0 && port <= MAX_PORT;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private static boolean isValidNumber(String value) {
    try {
      Double.parseDouble(value.trim());
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
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

  static boolean isAssetGroupIdAllowedByScope(String id, PrimitiveValidationContext context) {
    if (context.denylistedAssetGroupIds().contains(id)) {
      return false;
    }
    if (context.allowlistedAssetGroupIds().isEmpty()) {
      return true;
    }
    return context.allowlistedAssetGroupIds().contains(id);
  }

  /**
   * Returns {@code true} if the IP is excluded by the scope denylist, either by an exact IP match
   * or by falling inside a denied subnet. Unlike {@link #isIpAllowedByScope}, this ignores the
   * allowlist: it only enforces denylist exclusion.
   */
  static boolean isIpDeniedByScope(String ip, PrimitiveValidationContext context) {
    return context.denylistedIps().contains(ip)
        || context.denylistedSubnets().stream()
            .anyMatch(subnet -> IpAddressUtils.isIpInSubnet(ip, subnet));
  }

  static boolean isIpAllowedByScope(String ip, PrimitiveValidationContext context) {
    if (isIpDeniedByScope(ip, context)) {
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

  static boolean isSubnetAllowedByScope(String subnet, PrimitiveValidationContext context) {
    if (context.denylistedSubnets().contains(subnet)) {
      return false;
    }
    if (context.allowlistedSubnets().isEmpty()) {
      return true;
    }
    return context.allowlistedSubnets().contains(subnet);
  }

  static boolean isDomainAllowedByScope(String domain, PrimitiveValidationContext context) {
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
