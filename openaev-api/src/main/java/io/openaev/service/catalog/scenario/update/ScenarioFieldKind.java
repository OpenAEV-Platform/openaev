package io.openaev.service.catalog.scenario.update;

/** What a field of a scenario's content is: a named field, an inject argument or a variable. */
public enum ScenarioFieldKind {
  NAME,
  DESCRIPTION,
  SEVERITY,
  CATEGORY,
  TITLE,
  CONTRACT,
  DEPENDS_DURATION,
  DEPENDS_ON,
  ARGUMENT {
    @Override
    String key(String field) {
      return field.substring(ScenarioContentReader.CONTENT.length());
    }
  },
  VARIABLE {
    @Override
    String key(String field) {
      return field;
    }
  };

  /** The key a variable or an inject argument goes by; none for a named field. */
  String key(String field) {
    return null;
  }
}
