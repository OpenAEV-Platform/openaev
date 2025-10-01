package io.openaev.injector_contract;

import io.openaev.helper.SupportedLanguage;
import java.util.Map;
import lombok.Getter;

@Getter
public class ContractConfig {

  private final String type;

  private final boolean expose;

  private final Map<SupportedLanguage, String> label;
  private final String color_dark;
  private final String color_light;

  public ContractConfig(
      String type,
      Map<SupportedLanguage, String> label,
      String color_dark,
      String color_light,
      String icon,
      boolean expose) {
    this.type = type;
    this.expose = expose;
    this.color_dark = color_dark;
    this.color_light = color_light;
    this.label = label;
  }
}
