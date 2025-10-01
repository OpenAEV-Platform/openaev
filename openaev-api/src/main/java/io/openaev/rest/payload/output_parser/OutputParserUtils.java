package io.openaev.rest.payload.output_parser;

import io.openaev.database.model.OutputParser;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;

public class OutputParserUtils {

  private OutputParserUtils() {}

  /** Creates a copy of the given {@link OutputParser} entity. */
  public static OutputParser copyFromEntity(@NotNull final OutputParser source) {
    OutputParser copy = new OutputParser();
    BeanUtils.copyProperties(source, copy, "id", "contractOutputElements");
    return copy;
  }

  /**
   * Copies properties from the given {@link OutputParserInput} into the target {@link
   * OutputParser}.
   */
  public static OutputParser copyFromInput(
      @NotNull final OutputParserInput input, @NotNull final OutputParser target) {
    BeanUtils.copyProperties(input, target, "id", "contractOutputElements");
    return target;
  }
}
