package io.openaev.validator.primitive;

import io.openaev.database.model.PrimitiveType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth describing every {@link PrimitiveType}: what a user can <i>do</i> with its
 * values, and what those values must <i>look like</i>.
 *
 * <p>Both facets are declared together, once per type, because they are two answers about the same
 * type and drift apart as soon as they live in separate tables. They are still consumed at
 * different moments: the capabilities decide which operators and controls the editor offers, before
 * anything is typed; the rules validate the value afterwards.
 *
 * <p>A type only receives a format rule when the three following conditions hold:
 *
 * <ol>
 *   <li>a normative specification exists (RFC, Microsoft SID spec, MITRE CVE ID syntax) - a mere
 *       convention is not enough;
 *   <li>emitters actually conform to it, i.e. the value is produced by an output processor under
 *       that specification rather than as free text;
 *   <li>no partial form is legitimate for an equality comparison.
 * </ol>
 *
 * <p>Every other type falls back to free case-sensitive text with no format rule. That default is
 * deliberately the permissive one: a new primitive type must never start by rejecting values the
 * platform used to accept. Narrowing it is an opt-in, made here.
 *
 * <p>Semantic validation (does this asset exist? is this label part of a known set?) is
 * deliberately absent: it needs repository or context access, which would break the stateless
 * contract this class relies on to be serialized as API metadata. Such checks belong to the runtime
 * layer, next to the workflow scope allow/deny evaluation.
 */
public final class PrimitiveTypePolicy {

  /**
   * Where a format rule is enforced.
   *
   * <p>The distinction exists because tightening the <i>runtime ingestion</i> path is not the same
   * risk as tightening <i>user input</i>: a third-party injector emitting a non-standard value
   * would silently lose data. Newly introduced rules therefore start as {@link #INPUT_ONLY} and may
   * be promoted once observed in the field.
   */
  public enum FormatEnforcement {
    /** Validated when a user types the value, not when an injector emits it. */
    INPUT_ONLY,
    /** Also validated when a value is ingested into the workflow state. */
    RUNTIME_AND_INPUT
  }

  /**
   * Everything declared about one primitive type.
   *
   * @param numericValue the values are numbers. The backend compares {@code GT} / {@code GTE} /
   *     {@code LT} / {@code LTE} with {@code Double.parseDouble}, so this single flag decides both
   *     that those operators may be offered and that the value must be numeric.
   * @param caseSensitivity comparing the value is case-dependent, so the case-sensitivity toggle is
   *     meaningful. False for values whose canonical form carries no case distinction, where the
   *     toggle could only ever produce a false negative.
   * @param rules alternative format rules, combined with OR semantics. Empty when the type
   *     constrains no format, in which case any value is accepted.
   * @param enforcement where the rules apply. Meaningless, and left at {@link
   *     FormatEnforcement#INPUT_ONLY}, when there is no rule.
   */
  public record TypePolicy(
      boolean numericValue,
      boolean caseSensitivity,
      List<FormatRuleKind> rules,
      FormatEnforcement enforcement) {

    /** A number: comparable, no case to speak of. Always carries a rule. */
    static TypePolicy numeric(FormatEnforcement enforcement, FormatRuleKind rule) {
      return new TypePolicy(true, false, List.of(rule), enforcement);
    }

    /** Compared as text, case matters, no format constraint. */
    static TypePolicy text() {
      return new TypePolicy(false, true, List.of(), FormatEnforcement.INPUT_ONLY);
    }

    /** Compared as text, case matters, constrained by one or more rules. */
    static TypePolicy text(FormatEnforcement enforcement, FormatRuleKind... rules) {
      return new TypePolicy(false, true, List.of(rules), enforcement);
    }

    /**
     * Compared as text but with no case distinction - hexadecimal digests, UUIDs, dotted-decimal
     * addresses, structured identifiers - and no format constraint.
     */
    static TypePolicy caselessText() {
      return new TypePolicy(false, false, List.of(), FormatEnforcement.INPUT_ONLY);
    }

    /** Compared as text with no case distinction, constrained by one or more rules. */
    static TypePolicy caselessText(FormatEnforcement enforcement, FormatRuleKind... rules) {
      return new TypePolicy(false, false, List.of(rules), enforcement);
    }

    public boolean hasRules() {
      return !this.rules.isEmpty();
    }
  }

  private static final Map<PrimitiveType, TypePolicy> POLICIES = new EnumMap<>(PrimitiveType.class);

  static {
    // ---- Numbers ----
    POLICIES.put(
        PrimitiveType.Number,
        TypePolicy.numeric(FormatEnforcement.RUNTIME_AND_INPUT, FormatRuleKind.NUMBER));
    POLICIES.put(
        PrimitiveType.Port,
        TypePolicy.numeric(FormatEnforcement.RUNTIME_AND_INPUT, FormatRuleKind.PORT));

    // ---- Formats enforced at runtime since before this class existed ----
    POLICIES.put(
        PrimitiveType.IPv4,
        TypePolicy.caselessText(FormatEnforcement.RUNTIME_AND_INPUT, FormatRuleKind.IPV4));
    POLICIES.put(
        PrimitiveType.IPv6,
        TypePolicy.caselessText(FormatEnforcement.RUNTIME_AND_INPUT, FormatRuleKind.IPV6));
    POLICIES.put(
        PrimitiveType.IpSubnet,
        TypePolicy.caselessText(
            FormatEnforcement.RUNTIME_AND_INPUT,
            FormatRuleKind.IPV4_CIDR,
            FormatRuleKind.IPV6_CIDR));
    POLICIES.put(
        PrimitiveType.Domain,
        TypePolicy.text(FormatEnforcement.RUNTIME_AND_INPUT, FormatRuleKind.DOMAIN));

    // ---- Formats introduced with this class: user input only until the emitted values have been
    // observed in the field ----
    POLICIES.put(
        PrimitiveType.CVE,
        TypePolicy.caselessText(FormatEnforcement.INPUT_ONLY, FormatRuleKind.CVE));
    POLICIES.put(
        PrimitiveType.SID,
        TypePolicy.caselessText(FormatEnforcement.INPUT_ONLY, FormatRuleKind.WINDOWS_SID));
    POLICIES.put(
        PrimitiveType.Email, TypePolicy.text(FormatEnforcement.INPUT_ONLY, FormatRuleKind.EMAIL));
    // A host is an IP literal or a name; DOMAIN also covers single-label host names.
    POLICIES.put(
        PrimitiveType.Host,
        TypePolicy.text(
            FormatEnforcement.INPUT_ONLY,
            FormatRuleKind.IPV4,
            FormatRuleKind.IPV6,
            FormatRuleKind.DOMAIN));

    // ---- No format constraint, and compared without case distinction ----
    // Credential material, not a file digest: the emitters declare it as free Text and it carries
    // NTLM hashes, LM:NT pairs and Kerberos roasting blobs ($krb5asrep$..., $krb5tgs$...). It is
    // also masked for display, so any rule here would reject the masked echo the client sends back.
    POLICIES.put(PrimitiveType.Hash, TypePolicy.caselessText());
    // Carried by injector output as free Text and optionally as an array: an internal UUID is only
    // one of the shapes a third-party emitter may use for an asset reference.
    POLICIES.put(PrimitiveType.AssetId, TypePolicy.caselessText());
    POLICIES.put(PrimitiveType.AssetGroupId, TypePolicy.caselessText());

    // ---- No format constraint, compared as case-sensitive text ----
    // Every remaining type: opaque values, labels declared as free Text by their output processor,
    // identities with several incompatible spellings (sAMAccountName vs UPN, NetBIOS vs FQDN),
    // secrets, and paths whose acceptance depends on the target operating system.
    for (PrimitiveType type : PrimitiveType.values()) {
      POLICIES.putIfAbsent(type, TypePolicy.text());
    }
  }

  private PrimitiveTypePolicy() {}

  /** Policy of a primitive type. Never null: every type has at least the permissive default. */
  public static TypePolicy of(PrimitiveType type) {
    return POLICIES.get(type);
  }

  /** Immutable view of every policy, keyed by type, for the API descriptor and for tests. */
  public static Map<PrimitiveType, TypePolicy> all() {
    return Map.copyOf(POLICIES);
  }
}
