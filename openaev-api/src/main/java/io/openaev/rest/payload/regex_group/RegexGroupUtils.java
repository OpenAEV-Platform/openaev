package io.openaev.rest.payload.regex_group;

import io.openaev.database.model.RegexGroup;
import org.jetbrains.annotations.NotNull;

public class RegexGroupUtils {

  private RegexGroupUtils() {}

  /** Creates a shallow copy of the given {@link RegexGroup} entity. */
  public static RegexGroup copyFromEntity(@NotNull final RegexGroup source) {
    RegexGroup copy = new RegexGroup();
    copy.setField(source.getField());
    copy.setIndexValues(source.getIndexValues());
    return copy;
  }

  /**
   * Copies properties from the given {@link RegexGroupInput} into the target {@link RegexGroup}.
   */
  public static RegexGroup copyFromInput(
      @NotNull final RegexGroupInput input, @NotNull final RegexGroup target) {
    target.setField(input.getField());
    target.setIndexValues(input.getIndexValues());
    return target;
  }
}
