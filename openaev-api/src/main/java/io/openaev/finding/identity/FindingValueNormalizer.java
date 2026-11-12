package io.openaev.finding.identity;

import static io.openaev.helper.CryptoHelper.hashWithSHA256;

import io.openaev.database.model.ContractOutputType;
import io.openaev.finding.identity.LocationIdentity.AssetLocation;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes a finding value into the form its identity is hashed from, per contract output type.
 */
public final class FindingValueNormalizer {

  private static final Pattern CVE_ID =
      Pattern.compile("CVE-\\d{4}-\\d{4,}", Pattern.CASE_INSENSITIVE);
  private static final Pattern TRAILING_PARENTHESES = Pattern.compile(" \\([^()]*\\)$");
  private static final Pattern TRAILING_BRACKETS = Pattern.compile(" \\[[^\\[\\]]*]$");
  private static final Pattern TRAILING_MEMBER_COUNT = Pattern.compile(" \\([^()]* members\\)$");
  private static final Pattern HOST_PORT = Pattern.compile("^(.*):(\\d{1,5})$");
  private static final Pattern UNC_PATH = Pattern.compile("^\\\\\\\\([^\\\\]+)\\\\(.*)$");
  private static final Pattern HOST_LOCAL_PATH = Pattern.compile("^(.{2,}?):(/.*)$");
  private static final String PASSWORD_POLICY_SEPARATOR = ": ";
  private static final String SERVICE_SEPARATOR = " (";

  private FindingValueNormalizer() {}

  /**
   * The {@code finding_value_hash} column: SHA-256 of the normalized value, lower-case hex. The
   * hash, not the value, goes into the unique key of {@code findings}: fixed size, and no cleartext
   * secret in an index (ADR-017).
   */
  public static String hash(ContractOutputType type, String value, LocationIdentity location) {
    return hashWithSHA256(normalize(type, value, location));
  }

  /**
   * Normalizes a cleartext finding value for its identity hash.
   *
   * @param type the contract output type of the finding
   * @param value the cleartext value, as built by the output processor
   * @param location where the finding was detected: it decides whether the host stays in the value
   * @return the normalized value
   * @throws IllegalArgumentException for a type that is not a finding
   */
  public static String normalize(ContractOutputType type, String value, LocationIdentity location) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(location, "location");
    String stripped = value.strip();
    // No default: a new contract output type does not compile until it declares its rule
    return switch (type) {
      case Text, ActionOutput, Number, Port, IPv4 -> stripped;
      // Not only Active Directory: an SSH or FTP username is case sensitive.
      case Credentials -> stripped;
      case IPv6,
              Username,
              AdminUsername,
              AsreproastableAccount,
              KerberoastableAccount,
              Computer,
              Email,
              Delegation ->
          lower(stripped);
      case CVE -> cveId(stripped);
      case Sid -> stripped.toUpperCase(Locale.ROOT);
      case Group -> lower(strip(stripped, TRAILING_MEMBER_COUNT));
      case AccountWithPasswordNotRequired -> lower(strip(stripped, TRAILING_PARENTHESES));
      case Vulnerability -> lower(strip(stripped, TRAILING_BRACKETS));
      case PasswordPolicy -> lower(passwordPolicySetting(stripped));
      case PortsScan -> portScan(stripped, location);
      case Share -> share(stripped, location);
      case File -> file(stripped, location);
      case Asset, ExpectationSignature ->
          throw new IllegalArgumentException(type + " is not a finding type");
    };
  }

  // "cve-2026-0234 (critical)" -> "CVE-2026-0234", the whole value upper-cased when no id is found
  private static String cveId(String value) {
    Matcher matcher = CVE_ID.matcher(value);
    return (matcher.find() ? matcher.group() : value).toUpperCase(Locale.ROOT);
  }

  // "host:port (service (version))" -> "port" on an asset, "host:port" otherwise
  private static String portScan(String value, LocationIdentity location) {
    int service = value.indexOf(SERVICE_SEPARATOR);
    Matcher matcher = HOST_PORT.matcher(service < 0 ? value : value.substring(0, service));
    if (!matcher.matches()) {
      return lower(value);
    }
    String port = matcher.group(2);
    return onAsset(location) ? port : lower(matcher.group(1)) + ":" + port;
  }

  // "\\host\share (permissions)" -> "share" on an asset, "\\host\share" otherwise
  private static String share(String value, LocationIdentity location) {
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
  private static String file(String value, LocationIdentity location) {
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

  private static boolean onAsset(LocationIdentity location) {
    return location instanceof AssetLocation;
  }

  private static String strip(String value, Pattern suffix) {
    return suffix.matcher(value).replaceFirst("");
  }

  private static String lower(String value) {
    return value.toLowerCase(Locale.ROOT);
  }
}
