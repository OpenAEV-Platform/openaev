package io.openaev.utils;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal Semantic Versioning 2.0.0 ordering for content versions (MAJOR.MINOR.PATCH, optional
 * pre-release, build metadata ignored). An unparsable version sorts below every valid one.
 */
public record SemanticVersion(int major, int minor, int patch, String preRelease)
    implements Comparable<SemanticVersion> {

  private static final Pattern SEMVER =
      Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$");

  private static final SemanticVersion INVALID = new SemanticVersion(-1, -1, -1, null);

  /** Orders version strings by semver precedence; null and unparsable values come first. */
  public static final Comparator<String> ORDER =
      Comparator.comparing(SemanticVersion::parse, Comparator.naturalOrder());

  public static SemanticVersion parse(String version) {
    if (version == null) {
      return INVALID;
    }
    Matcher matcher = SEMVER.matcher(version.trim());
    if (!matcher.matches()) {
      return INVALID;
    }
    try {
      return new SemanticVersion(
          Integer.parseInt(matcher.group(1)),
          Integer.parseInt(matcher.group(2)),
          Integer.parseInt(matcher.group(3)),
          matcher.group(4));
    } catch (NumberFormatException e) {
      return INVALID;
    }
  }

  @Override
  public int compareTo(SemanticVersion other) {
    int result = Integer.compare(major, other.major);
    if (result == 0) {
      result = Integer.compare(minor, other.minor);
    }
    if (result == 0) {
      result = Integer.compare(patch, other.patch);
    }
    return result != 0 ? result : comparePreRelease(preRelease, other.preRelease);
  }

  // A release ranks above any of its pre-releases; identifiers compare numerically when both are.
  private static int comparePreRelease(String left, String right) {
    if (left == null || right == null) {
      return left == null ? (right == null ? 0 : 1) : -1;
    }
    String[] leftParts = left.split("\\.");
    String[] rightParts = right.split("\\.");
    for (int i = 0; i < Math.min(leftParts.length, rightParts.length); i++) {
      boolean leftNumeric = leftParts[i].matches("\\d+");
      boolean rightNumeric = rightParts[i].matches("\\d+");
      int result;
      if (leftNumeric && rightNumeric) {
        result =
            leftParts[i].length() != rightParts[i].length()
                ? Integer.compare(leftParts[i].length(), rightParts[i].length())
                : leftParts[i].compareTo(rightParts[i]);
      } else if (leftNumeric != rightNumeric) {
        result = leftNumeric ? -1 : 1;
      } else {
        result = leftParts[i].compareTo(rightParts[i]);
      }
      if (result != 0) {
        return result;
      }
    }
    return Integer.compare(leftParts.length, rightParts.length);
  }
}
