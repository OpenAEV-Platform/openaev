package io.openaev.api.import_mapper;

import io.openaev.database.model.ImportMapper;
import io.openaev.database.model.InjectImporter;
import io.openaev.database.model.RuleAttribute;

public final class ImportMapperMapper {

  private ImportMapperMapper() {}

  public static ImportMapperOutput toOutput(ImportMapper mapper) {
    return new ImportMapperOutput(
        mapper.getId(),
        mapper.getName(),
        mapper.getInjectTypeColumn(),
        mapper.getInjectImporters().stream()
            .map(ImportMapperMapper::toInjectImporterOutput)
            .toList(),
        mapper.getCreationDate(),
        mapper.getUpdateDate());
  }

  public static ImportMapperSimpleOutput toSimpleOutput(ImportMapper mapper) {
    return new ImportMapperSimpleOutput(
        mapper.getId(), mapper.getName(), mapper.getCreationDate(), mapper.getUpdateDate());
  }

  public static InjectImporterOutput toInjectImporterOutput(InjectImporter importer) {
    return new InjectImporterOutput(
        importer.getId(),
        importer.getImportTypeValue(),
        importer.getInjectorContract().getId(),
        importer.getRuleAttributes().stream()
            .map(ImportMapperMapper::toRuleAttributeOutput)
            .toList(),
        importer.getCreationDate(),
        importer.getUpdateDate());
  }

  public static RuleAttributeOutput toRuleAttributeOutput(RuleAttribute attribute) {
    return new RuleAttributeOutput(
        attribute.getId(),
        attribute.getName(),
        attribute.getColumns(),
        attribute.getDefaultValue(),
        attribute.getAdditionalConfig(),
        attribute.getCreationDate(),
        attribute.getUpdateDate());
  }
}
