package io.openaev.utils;

import io.openaev.ocsf.parser.schema.Version;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {
  public String snakeToCamel(String snake) {
    String pascal = snakeToPascal(snake);
    return pascal.substring(0, 1).toLowerCase() + pascal.substring(1);
  }

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

  public String toVersionedPackage(Version version, String prefix, String... parts) {
    String pkg = prefix + ".v" + version.versionNumber().getValue().replaceAll("[.]", "");
    if (Arrays.stream(parts).findAny().isPresent()) {
      pkg += "." + String.join(".", parts);
    }
    return pkg;
  }
}
