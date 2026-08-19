package io.openaev.utils;

import java.nio.file.Path;
import java.nio.file.Paths;
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

  public Path packageToPath(String packageName) {
    return Paths.get(packageName.replaceAll("[.]", "/"));
  }
}
