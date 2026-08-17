package io.openaev.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {
  public String snakeToPascal(String snake) {
    StringBuilder sb = new StringBuilder();
    Matcher firstChar = Pattern.compile("(^\\w|_\\w)").matcher(snake);
    while (firstChar.find()) {
      firstChar.appendReplacement(sb, firstChar.group(1).replace("_", "").toUpperCase());
    }
    firstChar.appendTail(sb);
    return sb.toString();
  }
}
