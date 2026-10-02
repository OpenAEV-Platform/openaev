package io.openaev.service.chaining;

import io.openaev.database.model.PrimitiveType;
import io.openaev.validator.IpAddressUtils;
import io.openaev.validator.primitive.FormatRuleKind;
import io.openaev.validator.primitive.PrimitiveFormatValidator;
import java.util.Locale;

/**
 * Acceptance rules for primitive chaining values before they are persisted in workflow state.
 *
 * <p>This validator is the <b>runtime</b> composition of two independent concerns:
 *
 * <ol>
 *   <li>the intrinsic format of the value, delegated to {@link PrimitiveFormatValidator} - pure,
 *       stateless and therefore exposable as API metadata;
 *   <li>membership in the workflow's scope allow/deny lists, which depends on the workflow context
 *       and can never be serialized as a rule.
 * </ol>
 *
 * <p>Only the format policies marked as runtime-enforced are applied here, so introducing a new
 * rule for a type that used to accept anything never silently discards data emitted by a
 * third-party injector. Scope-restrictable types (IPs, subnets, domains, asset and asset-group IDs)
 * additionally get the allowlist/denylist check. Port validation is also reused by output
 * processors before generating findings.
 */
public final class PrimitiveValueValidator {

  private PrimitiveValueValidator() {}

  /**
   * Decides whether a value is accepted into workflow state for the given primitive type.
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
    if (!PrimitiveFormatValidator.isAcceptedAtRuntime(primitiveType, value)) {
      return false;
    }
    return switch (primitiveType) {
      case IPv4, IPv6 -> isIpAllowedByScope(value, context);
      case Domain -> isDomainAllowedByScope(value, context);
      case IpSubnet -> isSubnetAllowedByScope(value, context);
      case AssetId -> isAssetIdAllowedByScope(value, context);
      case AssetGroupId -> isAssetGroupIdAllowedByScope(value, context);
      default -> true;
    };
  }

  /**
   * Format-only port check, used by output processors before generating findings.
   *
   * <p>Delegates to {@link FormatRuleKind#PORT} so the accepted syntax cannot drift from the rule
   * advertised to API consumers and to the frontend.
   */
  public static boolean isValidPort(String value) {
    return FormatRuleKind.PORT.matches(value);
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
