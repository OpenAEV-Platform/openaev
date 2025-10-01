package io.openaev.database.model;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;

public class InjectDependencyConditions {

  @Getter
  public enum DependencyMode {
    and("&&"),
    or("||");

    private final String evaluationValue;

    DependencyMode(String evaluationValue) {
      this.evaluationValue = evaluationValue;
    }
  }

  @Getter
  public enum DependencyOperator {
    eq("==");
    private final String evaluationValue;

    DependencyOperator(String evaluationValue) {
      this.evaluationValue = evaluationValue;
    }
  }

  @Data
  public static class InjectDependencyCondition {

    @NotNull private DependencyMode mode; // Between filters
    private List<Condition> conditions;

    @Override
    public String toString() {
      StringBuilder result = new StringBuilder(Strings.EMPTY);
      for (var i = 0; i < conditions.size(); i++) {
        if (i > 0) {
          result.append(mode.getEvaluationValue());
          result.append(StringUtils.SPACE);
        }
        result.append(conditions.get(i).toString());
        result.append(StringUtils.SPACE);
      }
      return result.toString().trim();
    }
  }

  @Data
  public static class Condition {

    @NotNull private String key;
    private boolean value;
    @NotNull private DependencyOperator operator;

    @Override
    public String toString() {
      return String.format("%s %s %s", key, operator.getEvaluationValue(), value);
    }
  }
}
