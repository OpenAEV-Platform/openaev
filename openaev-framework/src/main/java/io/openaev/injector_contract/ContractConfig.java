package io.openaev.injector_contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.helper.SupportedLanguage;
import java.util.Map;
import lombok.Getter;

/**
 * Configuration metadata for an injector contract.
 *
 * <p>This class holds the display and identification properties used to present an injector in the
 * user interface, including:
 *
 * <ul>
 *   <li>Type identifier for the injector
 *   <li>Localized labels for different languages
 *   <li>Theme-specific colors for UI presentation
 * </ul>
 *
 * @see Contract
 * @see Contractor
 */
@Getter
public class ContractConfig {

  /** Unique type identifier for this injector (e.g., "email", "sms", "caldera"). */
  private final String type;

  /** Localized display labels, keyed by supported language. */
  private final Map<SupportedLanguage, String> label;

  /** Color to use in dark theme UI (hex format, e.g., "#FF5733"). */
  @JsonProperty("color_dark")
  private final String colorDark;

  /** Color to use in light theme UI (hex format, e.g., "#FF5733"). */
  @JsonProperty("color_light")
  private final String colorLight;

  /**
   * Creates a new ContractConfig.
   *
   * @param type the injector type identifier
   * @param label the localized labels for supported languages
   * @param colorDark the color for dark theme UI
   * @param colorLight the color for light theme UI
   * @param icon the icon path (currently unused, kept for API compatibility)
   */
  @SuppressWarnings("java:S1172") // icon parameter kept for API compatibility
  public ContractConfig(
      String type,
      Map<SupportedLanguage, String> label,
      String colorDark,
      String colorLight,
      String icon) {
    this.type = type;
    this.colorDark = colorDark;
    this.colorLight = colorLight;
    this.label = label;
  }
}
