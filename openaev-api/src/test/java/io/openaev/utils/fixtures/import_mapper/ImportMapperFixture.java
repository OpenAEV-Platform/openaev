package io.openaev.utils.fixtures.import_mapper;

import static io.openaev.utils.fixtures.InjectorContractFixture.createDefaultInjectorContract;
import static io.openaev.utils.fixtures.import_mapper.RuleAttributeFixture.createRuleAttribute;

import io.openaev.database.model.ImportMapper;
import io.openaev.database.model.InjectImporter;
import java.util.ArrayList;
import java.util.Map;

public class ImportMapperFixture {

  public static final String DEFAULT_MAPPER_NAME = "TestMapper";

  public static final String DEFAULT_INJECT_TYPE_COLUMN = "A";

  public static final String DEFAULT_TITLE_NAME = "title";
  public static final String DEFAULT_TITLE_COLUMN = "B";

  public static final String DEFAULT_DESCRIPTION_NAME = "description";
  public static final String DEFAULT_DESCRIPTION_COLUMN = "C";

  public static final String DEFAULT_TRIGGER_TIME_NAME = "trigger_time";
  public static final String DEFAULT_TRIGGER_TIME_COLUMN = "D";

  public static ImportMapper createImportMapper(String injectTypeValue) {
    return createImportMapper(DEFAULT_MAPPER_NAME, injectTypeValue, DEFAULT_INJECT_TYPE_COLUMN);
  }

  public static ImportMapper createImportMapper(
      String mapperName, String injectTypeValue, String injectTypeColumn) {
    ImportMapper importMapper = new ImportMapper();
    importMapper.setName(mapperName);
    importMapper.setInjectTypeColumn(injectTypeColumn);
    importMapper.setInjectImporters(new ArrayList<>());
    importMapper.getInjectImporters().add(createInjectImporter(injectTypeValue));
    return importMapper;
  }

  public static InjectImporter createInjectImporter(String importTypeValue) {
    InjectImporter injectImporter = new InjectImporter();
    injectImporter.setImportTypeValue(importTypeValue);
    injectImporter.setInjectorContract(createDefaultInjectorContract());
    injectImporter.setRuleAttributes(new ArrayList<>());
    injectImporter
        .getRuleAttributes()
        .add(createRuleAttribute(DEFAULT_TITLE_NAME, DEFAULT_TITLE_COLUMN));
    injectImporter
        .getRuleAttributes()
        .add(createRuleAttribute(DEFAULT_DESCRIPTION_NAME, DEFAULT_DESCRIPTION_COLUMN));
    injectImporter
        .getRuleAttributes()
        .add(
            createRuleAttribute(
                DEFAULT_TRIGGER_TIME_NAME, DEFAULT_TRIGGER_TIME_COLUMN, Map.of("timePattern", "")));
    return injectImporter;
  }
}
