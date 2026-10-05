package io.openaev.service.stix;

import static io.openaev.database.model.TenantSettingKeys.IOC_VALIDATION_ALLOWED_TEST_KINDS;
import static io.openaev.database.model.TenantSettingKeys.IOC_VALIDATION_ASSET_GROUP;
import static io.openaev.database.model.TenantSettingKeys.IOC_VALIDATION_HTTP_PROXY_URL;
import static io.openaev.database.model.TenantSettingKeys.IOC_VALIDATION_NETWORK_PORT;
import static io.openaev.database.model.TenantSettingKeys.IOC_VALIDATION_SINKHOLE_ADDRESS;

import io.openaev.database.model.IocValidationTestKind;
import io.openaev.database.model.Setting;
import io.openaev.database.model.TenantSettingKeys;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exception.InputValidationException;
import io.openaev.service.AssetGroupService;
import io.openaev.service.settings.TenantSettingsService;
import io.openaev.utils.FilterUtilsJpa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes the tenant IOC validation safety settings. Unset or unreadable values fall back
 * to the safest default: no allowed test kind, no proxy, no sinkhole, port 443, no asset group.
 */
@Service
@RequiredArgsConstructor
public class IocValidationSettingsService {

  static final String ALLOWED_TEST_KINDS_FIELD = "ioc_validation_allowed_test_kinds";
  static final String HTTP_PROXY_URL_FIELD = "ioc_validation_http_proxy_url";
  static final String SINKHOLE_ADDRESS_FIELD = "ioc_validation_sinkhole_address";
  static final String ASSET_GROUP_FIELD = "ioc_validation_asset_group_id";
  static final int MIN_PORT = 1;
  static final int MAX_PORT = 65535;
  static final int MAX_ASSET_GROUP_OPTIONS = 100;

  private final TenantSettingsService tenantSettingsService;
  private final AssetGroupService assetGroupService;

  // -- READ --

  /** The settings in force for the tenant. */
  @Transactional(readOnly = true)
  public IocValidationSettings settings(@NotBlank final String tenantId) {
    return new IocValidationSettings(
        parseTestKinds(value(tenantId, IOC_VALIDATION_ALLOWED_TEST_KINDS)),
        value(tenantId, IOC_VALIDATION_HTTP_PROXY_URL).trim(),
        value(tenantId, IOC_VALIDATION_SINKHOLE_ADDRESS).trim(),
        parsePort(value(tenantId, IOC_VALIDATION_NETWORK_PORT)),
        value(tenantId, IOC_VALIDATION_ASSET_GROUP).trim());
  }

  // -- UPDATE --

  /**
   * Validates and stores the settings. Every value is checked before anything is written, so a
   * rejected update leaves the previous settings untouched.
   *
   * @throws InputValidationException when a value is unsafe or inconsistent, naming the field
   */
  @Transactional(rollbackFor = Exception.class)
  public IocValidationSettings update(
      @NotBlank final String tenantId, @NotNull final IocValidationSettings settings)
      throws InputValidationException {
    String proxy = blankToEmpty(settings.httpProxyUrl());
    String sinkhole = blankToEmpty(settings.sinkholeAddress());
    String assetGroupId = blankToEmpty(settings.assetGroupId());

    if (!proxy.isEmpty() && !IocValidationPlanner.isHttpUrl(proxy)) {
      throw new InputValidationException(
          HTTP_PROXY_URL_FIELD, "The egress proxy must be an absolute http or https URL");
    }
    if (hasUserInfo(proxy)) {
      throw new InputValidationException(
          HTTP_PROXY_URL_FIELD,
          "The egress proxy URL must not contain credentials: it is shown in the settings and"
              + " copied into the simulation injects");
    }
    if (settings.allows(IocValidationTestKind.HTTP_HEAD) && proxy.isEmpty()) {
      throw new InputValidationException(
          HTTP_PROXY_URL_FIELD,
          "HTTP HEAD tests can only be allowed once an egress proxy is configured");
    }
    if (!sinkhole.isEmpty() && !IocValidationPlanner.isIpLiteral(sinkhole)) {
      throw new InputValidationException(
          SINKHOLE_ADDRESS_FIELD, "The sinkhole must be an IPv4 or IPv6 address");
    }
    if (settings.networkPort() < MIN_PORT || settings.networkPort() > MAX_PORT) {
      throw new InputValidationException(
          "ioc_validation_network_port", "The network port must be between 1 and 65535");
    }
    if (!assetGroupId.isEmpty()) {
      try {
        assetGroupService.tenantAssetGroup(tenantId, assetGroupId);
      } catch (ElementNotFoundException e) {
        throw new InputValidationException(ASSET_GROUP_FIELD, "The asset group does not exist");
      }
    }

    tenantSettingsService.updateSettingValue(
        tenantId, IOC_VALIDATION_ALLOWED_TEST_KINDS, formatTestKinds(settings.allowedTestKinds()));
    tenantSettingsService.updateSettingValue(tenantId, IOC_VALIDATION_HTTP_PROXY_URL, proxy);
    tenantSettingsService.updateSettingValue(tenantId, IOC_VALIDATION_SINKHOLE_ADDRESS, sinkhole);
    tenantSettingsService.updateSettingValue(
        tenantId, IOC_VALIDATION_NETWORK_PORT, String.valueOf(settings.networkPort()));
    tenantSettingsService.updateSettingValue(tenantId, IOC_VALIDATION_ASSET_GROUP, assetGroupId);
    return new IocValidationSettings(
        settings.allowedTestKinds(), proxy, sinkhole, settings.networkPort(), assetGroupId);
  }

  // -- ASSET GROUP CHOICES --

  /**
   * The asset groups an administrator can pick for the validation tests, by name. The configured
   * group always comes first so the current choice can be displayed.
   */
  @Transactional(readOnly = true)
  public List<FilterUtilsJpa.Option> assetGroupOptions(
      @NotBlank final String tenantId, final String searchText) {
    return assetGroupService.tenantOptionsByName(
        tenantId, searchText, value(tenantId, IOC_VALIDATION_ASSET_GROUP), MAX_ASSET_GROUP_OPTIONS);
  }

  // -- OPTIONS --

  /** Parses the stored comma-separated list; unknown values are ignored. */
  static Set<IocValidationTestKind> parseTestKinds(String value) {
    Set<IocValidationTestKind> kinds = EnumSet.noneOf(IocValidationTestKind.class);
    if (value == null) {
      return kinds;
    }
    Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(token -> !token.isEmpty())
        .forEach(token -> IocValidationTestKind.fromStix(token).ifPresent(kinds::add));
    return kinds;
  }

  static String formatTestKinds(Collection<IocValidationTestKind> kinds) {
    return kinds.stream()
        .sorted()
        .map(IocValidationTestKind::name)
        .collect(Collectors.joining(","));
  }

  static int parsePort(String value) {
    try {
      int port = Integer.parseInt(value.trim());
      return port >= MIN_PORT && port <= MAX_PORT
          ? port
          : IocValidationSettings.DEFAULT_NETWORK_PORT;
    } catch (NumberFormatException e) {
      return IocValidationSettings.DEFAULT_NETWORK_PORT;
    }
  }

  private String value(String tenantId, TenantSettingKeys key) {
    Optional<String> stored =
        tenantSettingsService.findSetting(tenantId, key.key()).map(Setting::getValue);
    return stored.orElse(key.defaultValue());
  }

  static boolean hasUserInfo(String url) {
    if (url.isEmpty()) {
      return false;
    }
    try {
      return new URI(url).getRawUserInfo() != null;
    } catch (URISyntaxException e) {
      return false;
    }
  }

  private static String blankToEmpty(String value) {
    return value == null ? "" : value.trim();
  }
}
