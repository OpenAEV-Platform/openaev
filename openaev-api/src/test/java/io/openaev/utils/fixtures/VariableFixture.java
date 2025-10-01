package io.openaev.utils.fixtures;

import io.openaev.database.model.Variable;
import org.apache.commons.lang3.RandomStringUtils;

public class VariableFixture {
  public static final String VARIABLE_KEY = "variable_key";

  public static Variable getDefaultVariable() {
    Variable variable = createVariableWithDefaultKey();
    variable.setValue("default var value");
    variable.setDescription("default variable for tests");
    return variable;
  }

  public static Variable getVariable() {
    Variable var = createVariableWithKey(VARIABLE_KEY);
    var.setValue("variable value");
    var.setDescription("variable description");
    return var;
  }

  private static Variable createVariableWithDefaultKey() {
    return createVariableWithKey(null);
  }

  private static Variable createVariableWithKey(String key) {
    String new_key =
        key == null
            ? "variable_%s".formatted(RandomStringUtils.randomAlphabetic(24).toLowerCase())
            : key;
    Variable variable = new Variable();
    variable.setKey(new_key);
    return variable;
  }
}
