package io.openaev.injector_contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.helper.SupportedLanguage;
import java.util.Map;
import lombok.Getter;

@Getter
public class ContractConfig {

  private final String type;

  private final boolean expose;

  private final Map<SupportedLanguage, String> label;

  @JsonProperty("color_dark")
  private final String colorDark;

  @JsonProperty("color_light")
  private final String colorLight;

  /**
   * Creates a new ContractConfig.
   *
   * @param type the injector type
   * @param label the label map for supported languages
   * @param colorDark the dark theme color
   * @param colorLight the light theme color
   * @param icon the icon path (currently unused, kept for API compatibility)
   * @param expose whether the contract is exposed
   */
  @SuppressWarnings("java:S1172") // icon parameter kept for API compatibility
  public ContractConfig(
      String type,
      Map<SupportedLanguage, String> label,
      String colorDark,
      String colorLight,
      String icon,
      boolean expose) {
    this.type = type;
    this.expose = expose;
    this.colorDark = colorDark;
    this.colorLight = colorLight;
    this.label = label;
  }
}
