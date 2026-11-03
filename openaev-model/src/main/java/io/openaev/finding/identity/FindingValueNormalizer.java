package io.openaev.finding.identity;

import io.openaev.database.model.ContractOutputType;
import io.openaev.finding.identity.FindingLocation.AssetLocation;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes a finding value into the form its identity is hashed from, per contract output type.
 *
 * <p>It works on the value string the output processors build ({@code toFindingValue}), because it
 * is all the data conversion of existing findings has: the write path and the conversion call the
 * same code. {@code FindingIdentityConsistencyTest} walks the processors to prove the rules still
 * match what they build.
 *
 * <p>Three kinds of rules:
 *
 * <ul>
 *   <li>case: folded where the platform the value comes from ignores it (CVE, SID, Active
 *       Directory names, emails, hosts), kept everywhere else (passwords, hashes, file paths);
 *   <li>changing state: removed (member count, status, service, permissions, policy setting
 *       value), so a finding whose state changes stays the same finding;
 *   <li>host: removed from {@code portscan}, {@code share} and {@code file} when the location is
 *       an asset, which already carries it, so "Also detected on" finds the same finding on other
 *       assets. Kept otherwise, so two hosts never merge into one {@code unknown} finding.
 * </ul>
 *
 * <p>Any rule change MUST bump {@link #NORMALIZATION_VERSION} and ship a migration that rehashes
 * every finding of the changed types, merging the findings that now collide.
 */
public final class FindingValueNormalizer {

  public static final int NORMALIZATION_VERSION = 1;

  @FunctionalInterface
  interface Rule {
    String apply(String value, FindingLocation location);
  }

  private static final Pattern TRAILING_PARENTHESES = Pattern.compile(" \\([^()]*\\)$");
  private static final Pattern TRAILING_BRACKETS = Pattern.compile(" \\[[^\\[\\]]*]$");
  private static final Pattern TRAILING_MEMBER_COUNT = Pattern.compile(" \\([^()]* members\\)$");
  private static final Pattern HOST_PORT = Pattern.compile("^(.*):(\\d{1,5})$");
  private static final Pattern UNC_PATH = Pattern.compile("^\\\\\\\\([^\\\\]+)\\\\(.*)$");
  private static final Pattern HOST_LOCAL_PATH = Pattern.compile("^(.{2,}?):(/.*)$");
  private static final String PASSWORD_POLICY_SEPARATOR = ": ";

  private static final Rule AS_IS = (value, location) -> value;
  private static final Rule LOWER = (value, location) -> lower(value);
  private static final Rule UPPER = (value, location) -> value.toUpperCase(Locale.ROOT);

  private static final Map<ContractOutputType, Rule> RULES = buildRules();

  private FindingValueNormalizer() {}

  private static Map<ContractOutputType, Rule> buildRules() {
    Map<ContractOutputType, Rule> rules = new EnumMap<>(ContractOutputType.class);
    rules.put(ContractOutputType.Text, AS_IS);
    rules.put(ContractOutputType.ActionOutput, AS_IS);
    rules.put(ContractOutputType.Number, AS_IS);
    rules.put(ContractOutputType.Port, AS_IS);
    rules.put(ContractOutputType.IPv4, AS_IS);
    rules.put(ContractOutputType.IPv6, LOWER);
    // Not only Active Directory: an SSH or FTP username is case sensitive.
    rules.put(ContractOutputType.Credentials, AS_IS);
    rules.put(ContractOutputType.CVE, UPPER);
    rules.put(ContractOutputType.Sid, UPPER);
    rules.put(ContractOutputType.Username, LOWER);
    rules.put(ContractOutputType.AdminUsername, LOWER);
    rules.put(ContractOutputType.AsreproastableAccount, LOWER);
    rules.put(ContractOutputType.KerberoastableAccount, LOWER);
    rules.put(ContractOutputType.Computer, LOWER);
    rules.put(ContractOutputType.Email, LOWER);
    rules.put(ContractOutputType.Delegation, LOWER);
    rules.put(ContractOutputType.Group, (value, location) -> lower(strip(value, TRAILING_MEMBER_COUNT)));
    rules.put(
        ContractOutputType.AccountWithPasswordNotRequired,
        (value, location) -> lower(strip(value, TRAILING_PARENTHESES)));
    rules.put(ContractOutputType.Vulnerability, (value, location) -> lower(strip(value, TRAILING_BRACKETS)));
    rules.put(ContractOutputType.PasswordPolicy, (value, location) -> lower(passwordPolicySetting(value)));
    rules.put(ContractOutputType.PortsScan, FindingValueNormalizer::portScan);
    rules.put(ContractOutputType.Share, FindingValueNormalizer::share);
    rules.put(ContractOutputType.File, FindingValueNormalizer::file);
    return Collections.unmodifiableMap(rules);
  }

  /**
   * Normalizes a cleartext finding value for its identity hash.
   *
   * @param type the contract output type of the finding
   * @param value the cleartext value, as built by the output processor
   * @param location where the finding was detected: it decides whether the host stays in the value
   * @return the normalized value; a type without a rule is only trimmed
   */
  public static String normalize(
      ContractOutputType type, String value, FindingLocation location) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(location, "location");
    return RULES.getOrDefault(type, AS_IS).apply(value.strip(), location);
  }

  /** The types with a declared rule, for the test that forces a new type to declare one. */
  static Map<ContractOutputType, Rule> rules() {
    return RULES;
  }

  // "host:port (service)" -> "port" on an asset, "host:port" otherwise
  private static String portScan(String value, FindingLocation location) {
    Matcher matcher = HOST_PORT.matcher(strip(value, TRAILING_PARENTHESES));
    if (!matcher.matches()) {
      return lower(value);
    }
    String port = matcher.group(2);
    return onAsset(location) ? port : lower(matcher.group(1)) + ":" + port;
  }

  // "\\host\share (permissions)" -> "share" on an asset, "\\host\share" otherwise
  private static String share(String value, FindingLocation location) {
    String withoutPermissions = strip(value, TRAILING_PARENTHESES);
    Matcher matcher = UNC_PATH.matcher(withoutPermissions);
    if (!matcher.matches()) {
      return lower(withoutPermissions);
    }
    String shareName = lower(matcher.group(2));
    return onAsset(location) ? shareName : "\\\\" + lower(matcher.group(1)) + "\\" + shareName;
  }

  // "\\host\share\dir\file" or "host:/dir/file" -> "share/dir/file" or "/dir/file" on an asset,
  // the same with the host lower-cased otherwise. The path keeps its case (NFS, FTP).
  private static String file(String value, FindingLocation location) {
    Matcher unc = UNC_PATH.matcher(value);
    if (unc.matches()) {
      String relative = unc.group(2);
      return onAsset(location)
          ? relative.replace('\\', '/')
          : "\\\\" + lower(unc.group(1)) + "\\" + relative;
    }
    Matcher local = HOST_LOCAL_PATH.matcher(value);
    if (local.matches()) {
      String path = local.group(2);
      return onAsset(location) ? path : lower(local.group(1)) + ":" + path;
    }
    return value;
  }

  // "MinimumPasswordLength: 7" -> "MinimumPasswordLength"
  private static String passwordPolicySetting(String value) {
    int separator = value.indexOf(PASSWORD_POLICY_SEPARATOR);
    return separator < 0 ? value : value.substring(0, separator);
  }

  private static boolean onAsset(FindingLocation location) {
    return location instanceof AssetLocation;
  }

  private static String strip(String value, Pattern suffix) {
    return suffix.matcher(value).replaceFirst("");
  }

  private static String lower(String value) {
    return value.toLowerCase(Locale.ROOT);
  }
}
