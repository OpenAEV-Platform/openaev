package io.openaev.api.chaining;

import io.openaev.api.chaining.dto.PrimitiveTypeCapabilitiesOutput;
import io.openaev.api.chaining.dto.PrimitiveTypeDescriptorOutput;
import io.openaev.api.chaining.dto.PrimitiveTypeFormatRuleOutput;
import io.openaev.api.chaining.dto.PrimitiveTypeValidationOutput;
import io.openaev.database.model.PrimitiveType;
import io.openaev.validator.primitive.FormatRuleKind;
import io.openaev.validator.primitive.PrimitiveFormatValidator;
import io.openaev.validator.primitive.PrimitiveTypePolicy;
import io.openaev.validator.primitive.PrimitiveTypePolicy.TypePolicy;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Serializes the backend primitive-type knowledge - {@link PrimitiveTypePolicy} - into the
 * descriptor consumed by the condition editor and the scope variable form.
 *
 * <p>A pure static mapper: the descriptor is the same for every tenant and every user, since it
 * only describes the shape of a value and not any tenant data.
 */
public final class PrimitiveTypeDescriptorMapper {

  private PrimitiveTypeDescriptorMapper() {}

  /** Descriptors of every primitive type, ordered by label so the UI can render them as-is. */
  public static List<PrimitiveTypeDescriptorOutput> toOutputs() {
    return Arrays.stream(PrimitiveType.values())
        .sorted(Comparator.comparing(type -> type.label))
        .map(PrimitiveTypeDescriptorMapper::toOutput)
        .toList();
  }

  public static PrimitiveTypeDescriptorOutput toOutput(PrimitiveType type) {
    TypePolicy policy = PrimitiveTypePolicy.of(type);
    return PrimitiveTypeDescriptorOutput.builder()
        .primitiveType(type)
        .capabilities(
            PrimitiveTypeCapabilitiesOutput.builder()
                .numericValue(policy.numericValue())
                .caseSensitivity(policy.caseSensitivity())
                .build())
        .validation(toValidation(policy))
        .build();
  }

  private static PrimitiveTypeValidationOutput toValidation(TypePolicy policy) {
    return PrimitiveTypeValidationOutput.builder()
        // Always exposed, even when there is no rule: the frontend applies the same
        // operator-awareness whichever type is selected.
        .appliesTo(
            PrimitiveFormatValidator.FORMAT_OPERATORS.stream()
                .sorted(Comparator.comparing(Enum::name))
                .toList())
        .rules(policy.rules().stream().map(PrimitiveTypeDescriptorMapper::toRule).toList())
        .build();
  }

  private static PrimitiveTypeFormatRuleOutput toRule(FormatRuleKind kind) {
    return PrimitiveTypeFormatRuleOutput.builder()
        .kind(kind.name())
        // Null for named kinds: shipping a regex that only approximates the Java parser would make
        // the frontend accept values the backend rejects, which is worse than no pattern at all.
        .pattern(kind.pattern())
        .errorMessageKey(kind.errorMessageKey())
        .build();
  }
}
