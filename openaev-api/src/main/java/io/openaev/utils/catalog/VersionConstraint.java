package io.openaev.utils.catalog;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A platform version constraint of the catalogue, such as {@code >=2.1} or {@code ~=2.1.0}. */
public final class VersionConstraint {

  private static final Pattern CONSTRAINT =
      Pattern.compile("^(>=|>|==|~=)(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?$");

  private VersionConstraint() {}

  // An unreadable constraint or version never blocks: the catalogue CI checks the form.
  public static boolean allows(String constraint, String version) {
    Matcher matcher = CONSTRAINT.matcher(constraint.trim());
    if (!matcher.matches() || !SemanticVersion.isValid(version)) {
      return true;
    }
    SemanticVersion current = SemanticVersion.parse(version);
    SemanticVersion wanted =
        new SemanticVersion(number(matcher, 2), number(matcher, 3), number(matcher, 4), null);
    int order = current.compareTo(wanted);
    return switch (matcher.group(1)) {
      case ">=" -> order >= 0;
      case ">" -> order > 0;
      case "==" -> order == 0;
      // Compatible release: at least the wanted version, all its numbers but the last kept.
      default -> order >= 0 && sameRelease(current, wanted, matcher.group(4) != null);
    };
  }

  private static boolean sameRelease(
      SemanticVersion current, SemanticVersion wanted, boolean patchGiven) {
    return current.major() == wanted.major() && (!patchGiven || current.minor() == wanted.minor());
  }

  private static int number(Matcher matcher, int group) {
    return matcher.group(group) == null ? 0 : Integer.parseInt(matcher.group(group));
  }
}
