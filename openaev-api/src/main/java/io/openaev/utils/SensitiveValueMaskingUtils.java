package io.openaev.utils;

import io.openaev.database.model.ChainingTypeRegistry;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.PrimitiveType;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Single source of truth for masking the secret material the platform discovers during simulations.
 *
 * <p>Sensitivity is <b>derived</b>, never stored: a value is secret because of the type it was
 * extracted as. {@link ChainingTypeRegistry} already decomposes every {@link ContractOutputType}
 * into the {@link PrimitiveType}s it is made of, so a type holding a {@code password}, a {@code
 * hash} or a {@code key} is sensitive by construction. Adding a new contract output type carrying a
 * hash makes it masked automatically, with no column, no migration and no flag to thread through
 * the output processors.
 *
 * <p>Masking is applied <b>per segment</b> rather than to the whole value whenever the composition
 * of that value is known (see {@link #VALUE_COMPOSITIONS}): a credential is handed out as {@code
 * jdoe:Su******}, keeping the identity of the compromised account - the actionable half - and
 * withholding only the secret. Where the composition is unknown, the whole value is masked, so an
 * omission can only ever over-mask.
 *
 * <p>Note on location: this class currently lives in {@code openaev-api}, which covers every place
 * the platform hands a value out today (finding serialization, chaining engine, attack path). If
 * masking at indexing time ever becomes necessary - so that reports and dashboards are covered too
 * - this class will have to move back down to {@code openaev-model}, because {@code FindingHandler}
 * lives there and cannot depend on {@code openaev-api}.
 *
 * <p>The database always keeps the cleartext value: deduplication, correlation and attack path
 * computation rely on it. Only the representation handed out is masked.
 */
public final class SensitiveValueMaskingUtils {

  /** Mask substituted to the secret part of a sensitive value. */
  public static final String MASK = "******";

  private static final char PART_SEPARATOR = ':';
  private static final int MASKING_VISIBLE_FRAGMENT_LENGTH = 2;
  private static final int MASKING_MIN_LENGTH_FOR_FRAGMENT = 5;

  /**
   * Primitive types whose values are secret material: they must never leave the platform in
   * cleartext, so any value carrying one of them is masked.
   */
  private static final Set<PrimitiveType> TYPE_TO_MASK =
      Set.of(PrimitiveType.Password, PrimitiveType.Hash, PrimitiveType.Key);

  /**
   * Contract output types excluded from the derivation whatever their recipe says, because the
   * recipe names a secret primitive that the value does not actually carry.
   *
   * <p>{@link ContractOutputType#PasswordPolicy} decomposes into {@link PrimitiveType#Key} - but
   * that {@code key} is the <em>name of a password policy setting</em> (for instance {@code
   * MinimumPasswordLength}), not a cryptographic key. It is a collision of vocabulary, which no
   * amount of splitting can resolve: the whole value is policy text, so the only way out is to
   * exclude the type. Product decided so on issue 7500.
   *
   * <p>Types whose value merely holds the secret in <em>one of its segments</em> do not belong
   * here: {@link #VALUE_COMPOSITIONS} handles them by masking that segment alone.
   */
  private static final Set<ContractOutputType> NEVER_SENSITIVE =
      Set.of(ContractOutputType.PasswordPolicy);

  /**
   * How a finding value is composed, for the types whose value is more than a single primitive: the
   * separator its segments are joined with ({@code null} when there is only one segment) and the
   * primitive type of each segment, in order.
   */
  record ValueComposition(String separator, List<PrimitiveType> segments) {}

  /**
   * The composition of the finding value, per contract output type - the inverse of the processors'
   * {@code toFindingValue}.
   *
   * <p>It exists because {@link ChainingTypeRegistry} describes the fields of the
   * <em>contract</em>, not what the processor actually concatenates into the value. Deriving from
   * the recipe alone is all-or-nothing: it masks {@code jdoe:Sup3rS3cret} entirely, hiding the
   * username - which is not a secret and is the actionable part for an analyst - and it flags
   * account types whose value turns out to be a username alone. Knowing the segments lets the very
   * same {@link #TYPE_TO_MASK} be applied one level down, per segment.
   *
   * <p><b>Why here and not on the processors.</b> Declaring the composition next to {@code
   * toFindingValue} would be the natural home, but reaching it needs {@code
   * OutputProcessorFactory}, and {@code FindingMapper} - the main caller - would then depend on
   * every {@code OutputProcessor} bean, whose {@code CredentialsOutputProcessor} depends on {@code
   * FindingService}, which depends back on {@code FindingMapper}: a Spring dependency cycle, and an
   * application that no longer starts. Neither {@code @Lazy} nor {@code ObjectProvider} is an
   * answer, as both hide the cycle instead of removing it. So the table is static, and a test walks
   * the processors to prove it never drifts from them (see {@code
   * SensitiveValueCompositionConsistencyTest}).
   *
   * <p>{@code Credentials} is emitted as {@code username:password} or {@code username:hash}
   * depending on what the payload carried. Declaring {@link PrimitiveType#Password} covers both:
   * only the membership of {@link #TYPE_TO_MASK} matters here, and {@link PrimitiveType#Hash} is in
   * it too, so the second segment is masked in either branch.
   */
  private static final Map<ContractOutputType, ValueComposition> VALUE_COMPOSITIONS =
      Map.of(
          ContractOutputType.Credentials,
          new ValueComposition(":", List.of(PrimitiveType.Username, PrimitiveType.Password)),
          ContractOutputType.AsreproastableAccount,
          new ValueComposition(null, List.of(PrimitiveType.Username)),
          ContractOutputType.KerberoastableAccount,
          new ValueComposition(null, List.of(PrimitiveType.Username)));

  /**
   * The declared compositions, for the test that walks the output processors and proves the table
   * still matches what their {@code toFindingValue} produces. Not part of the masking API.
   */
  static Map<ContractOutputType, ValueComposition> valueCompositions() {
    return VALUE_COMPOSITIONS;
  }

  /** Every contract output type indexed by its label, for the label-based overload. */
  private static final Map<String, ContractOutputType> LABEL_TO_TYPE =
      Arrays.stream(ContractOutputType.values())
          .collect(
              Collectors.toUnmodifiableMap(
                  type -> type.getLabel().toLowerCase(Locale.ROOT), Function.identity()));

  private SensitiveValueMaskingUtils() {}

  /**
   * Whether findings of this contract output type hold secret material, hence must be masked when
   * the platform hands their value out.
   *
   * <p>When the value composition is known, a type is sensitive if and only if <b>one of its
   * segments</b> is secret material; the recipe as a whole is not consulted, since it describes the
   * contract rather than the value. This matters beyond display: {@code AttackPathIds} hashes the
   * ids of sensitive findings, and hashing the id of a value that holds nothing to hide would cost
   * readability for no gain.
   *
   * @param type the contract output type the value was extracted as
   * @return true when at least one segment - or, with no known composition, at least one primitive
   *     type of its recipe - is secret material
   */
  public static boolean isSensitive(final ContractOutputType type) {
    if (type == null || NEVER_SENSITIVE.contains(type)) {
      return false;
    }
    ValueComposition composition = VALUE_COMPOSITIONS.get(type);
    if (composition != null) {
      return composition.segments().stream().anyMatch(TYPE_TO_MASK::contains);
    }
    return primitiveTypesOf(type).stream().anyMatch(TYPE_TO_MASK::contains);
  }

  /**
   * Every contract output type is registered in the chaining registry, but an unregistered one
   * would make it throw. Such a type carries no known primitive, hence no known secret: treat it as
   * non sensitive rather than failing the whole serialization of a finding page.
   */
  private static List<PrimitiveType> primitiveTypesOf(final ContractOutputType type) {
    try {
      return ChainingTypeRegistry.getPrimitiveTypesForContractOutputType(type);
    } catch (IllegalArgumentException e) {
      return List.of();
    }
  }

  /** Whether values of this primitive type are secret material. */
  public static boolean isSensitive(final PrimitiveType type) {
    return type != null && TYPE_TO_MASK.contains(type);
  }

  /**
   * Masks the value when the primitive type holds secret material, returns it untouched otherwise.
   *
   * <p>This is the <b>bare secret</b> path - a chaining scope variable, a condition target value -
   * where the whole value is the secret and nothing else. It therefore never splits: a password
   * happening to contain {@code :} would otherwise be handed out as two masked parts, each keeping
   * its own readable fragment, disclosing more of it than masking it as one unit ever does.
   *
   * @param type the primitive type of the value
   * @param value the cleartext value
   * @return the masked value, or the value as-is when the type is not sensitive
   */
  public static String maskIfNeeded(final PrimitiveType type, final String value) {
    if (!isSensitive(type) || value == null || value.isBlank()) {
      return value;
    }
    return maskPart(value);
  }

  /**
   * Whether the candidate value is nothing but the masked representation of the value currently
   * held.
   *
   * <p>A masked value is handed out by the API, so a client editing anything else of the same
   * object sends it back unchanged. Persisting it would overwrite the secret with its own mask:
   * this is the guard that detects the echo so the raw value can be kept instead.
   *
   * @param type the primitive type of the value
   * @param rawValue the cleartext value currently held
   * @param candidateValue the value received from the client
   * @return true when the candidate is exactly what masking the raw value produces
   */
  public static boolean isMaskedRepresentationOfCurrentValue(
      final PrimitiveType type, final String rawValue, final String candidateValue) {
    if (type == null || rawValue == null || candidateValue == null) {
      return false;
    }
    return maskIfNeeded(type, rawValue).equals(candidateValue);
  }

  /**
   * Masks the value when the contract output type holds secret material, returns it untouched
   * otherwise.
   *
   * <p>When the composition of the value is known, only the segments whose primitive type is secret
   * material are masked, so {@code jdoe:Sup3rS3cret} becomes {@code jdoe:Su******}: the identity of
   * the compromised account stays readable, which is what the analyst acts on, while the secret
   * keeps only the two character fragment every mask leaves.
   *
   * @param type the contract output type of the value
   * @param value the cleartext value
   * @return the masked value, or the value as-is when the type is not sensitive
   */
  public static String maskIfNeeded(final ContractOutputType type, final String value) {
    if (!isSensitive(type) || value == null || value.isBlank()) {
      return value;
    }
    ValueComposition composition = VALUE_COMPOSITIONS.get(type);
    return composition == null ? mask(value) : maskSegments(composition, value);
  }

  /**
   * Masks the secret segments of a value whose composition is known, keeping the others as they
   * are.
   *
   * <p>This is exactly {@link #mask(String)}, made selective: the value is cut on the same
   * separator and each part goes through the same {@link #maskPart(String)}, but only the parts
   * whose declared {@link PrimitiveType} belongs to {@link #TYPE_TO_MASK} are masked. Were {@link
   * PrimitiveType#Username} ever added to that set, this would degenerate into {@link
   * #mask(String)} itself - which is the measure of the rule being one rule rather than two.
   *
   * <p>A masked segment therefore keeps its two character fragment, as everywhere else: an operator
   * who already knows a secret can still tell it is the one that was discovered, without the
   * platform disclosing it.
   *
   * <p>Every departure from the declared shape falls back to masking the whole value: an omission
   * can then only over-mask, never leak. That is why the split is <b>limited</b> to the number of
   * declared segments - a password containing the separator, as in {@code jdoe:pa:ss}, must yield
   * {@code ["jdoe", "pa:ss"]}. Splitting without the limit would shift the segments and hand the
   * tail of the password out in the clear, under the guise of a username.
   */
  private static String maskSegments(final ValueComposition composition, final String value) {
    List<PrimitiveType> segments = composition.segments();
    if (composition.separator() == null || segments.size() == 1) {
      // maskPart, not mask: a lone secret segment is one unit, and splitting it on ':' would give
      // back a readable fragment of each of its parts.
      return isSensitive(segments.getFirst()) ? maskPart(value) : value;
    }

    String[] parts =
        value.split(Pattern.quote(composition.separator()), segments.size()); // limited on purpose
    if (parts.length != segments.size()) {
      return mask(value);
    }

    StringBuilder masked = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      if (i > 0) {
        masked.append(composition.separator());
      }
      masked.append(isSensitive(segments.get(i)) ? maskPart(parts[i]) : parts[i]);
    }
    return masked.toString();
  }

  /**
   * Masks the value when the type <em>label</em> denotes a sensitive contract output type, returns
   * it untouched otherwise.
   *
   * <p>The attack path graph manipulates lowercase type labels ({@code "credentials"}, {@code
   * "sid"}, {@code "file"}...) rather than {@link ContractOutputType} constants, and some of its
   * categories are curated groupings with no enum counterpart. An unknown label is therefore
   * <b>not</b> an error: it resolves to no type, hence to "not sensitive", so a single unmapped
   * label can never make a whole graph page fail.
   *
   * <p>Resolving the label to its type rather than masking outright is what makes the graph and the
   * findings page agree down to the character: the composition of the resolved type applies here
   * too, so the drawer shows the same {@code jdoe:Su******}.
   *
   * @param typeLabel the {@link ContractOutputType#getLabel() label} of the type, case insensitive
   * @param value the cleartext value
   * @return the masked value, or the value as-is when the label is unknown or not sensitive
   */
  public static String maskIfNeeded(final String typeLabel, final String value) {
    return maskIfNeeded(fromLabel(typeLabel), value);
  }

  /**
   * Whether findings carrying this type <em>label</em> hold secret material. Same label resolution
   * as {@link #maskIfNeeded(String, String)}: an unknown label is not sensitive.
   *
   * @param typeLabel the {@link ContractOutputType#getLabel() label} of the type, case insensitive
   * @return true when the label resolves to a sensitive contract output type
   */
  public static boolean isSensitive(final String typeLabel) {
    return isSensitive(fromLabel(typeLabel));
  }

  /** The contract output type carrying this label, or {@code null} when none does. */
  private static ContractOutputType fromLabel(final String typeLabel) {
    if (typeLabel == null || typeLabel.isBlank()) {
      return null;
    }
    return LABEL_TO_TYPE.get(typeLabel.toLowerCase(Locale.ROOT));
  }

  /**
   * Masks a value whole, whatever its type - the closed fallback, used when the composition of the
   * value is unknown or does not match, and by callers that have already decided to mask.
   *
   * <p>Every part of the value - the parts being separated by {@code :} - is masked the same way: a
   * two character fragment is kept so an operator can still tell WHICH secret was discovered when
   * the value is already known to them, without ever disclosing it. A part too short to keep a
   * fragment without disclosing most of it is masked entirely. The mask has a fixed width so the
   * length of the secret is not leaked either.
   *
   * <ul>
   *   <li>{@code admin:motdepasse} becomes {@code ad******:mo******}
   *   <li>{@code Sup3rS3cret} becomes {@code Su******}
   *   <li>{@code abcd} becomes {@code ******}
   * </ul>
   *
   * @param value the cleartext value
   * @return its masked form, or the value as-is when it is null or blank
   */
  public static String mask(final String value) {
    if (value == null || value.isBlank()) {
      return value;
    }

    return Arrays.stream(value.split(String.valueOf(PART_SEPARATOR), -1))
        .map(SensitiveValueMaskingUtils::maskPart)
        .collect(Collectors.joining(String.valueOf(PART_SEPARATOR)));
  }

  private static String maskPart(final String part) {
    if (part.length() < MASKING_MIN_LENGTH_FOR_FRAGMENT) {
      return MASK;
    }
    return part.substring(0, MASKING_VISIBLE_FRAGMENT_LENGTH) + MASK;
  }
}
