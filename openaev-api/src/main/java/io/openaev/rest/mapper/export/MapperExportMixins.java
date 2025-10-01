package io.openaev.rest.mapper.export;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;

public class MapperExportMixins {

  private MapperExportMixins() {}

  @JsonIncludeProperties(
      value = {
        "import_mapper_name",
        "import_mapper_inject_type_column",
        "import_mapper_inject_importers",
      })
  public static class ImportMapper {}

  @JsonIncludeProperties(
      value = {
        "inject_importer_type_value",
        "inject_importer_injector_contract",
        "inject_importer_rule_attributes",
      })
  public static class InjectImporter {}

  @JsonIncludeProperties(
      value = {
        "rule_attribute_columns",
        "rule_attribute_name",
        "rule_attribute_default_value",
        "rule_attribute_additional_config",
      })
  public static class RuleAttribute {}
}
