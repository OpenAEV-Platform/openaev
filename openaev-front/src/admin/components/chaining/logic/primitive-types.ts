import ipaddr from 'ipaddr.js';

import { type PrimitiveTypeDescriptorOutput, type PrimitiveTypeFormatRuleOutput } from '../../../../utils/api-types';

/**
 * Frontend counterpart of the backend primitive-type descriptor.
 *
 * Rules that the backend can serialize arrive as a `pattern` and are applied as-is: the backend
 * writes them in the Java / ECMAScript intersection precisely so no second source of truth exists
 * here. Rules backed by a parser the backend cannot express as a portable regex arrive with no
 * pattern and are implemented below, one function per `kind`. Both runtimes are pinned to the same
 * behaviour by the shared vectors in `openaev-api/src/test/resources/primitive-format-vectors.json`
 * — never change one side without running both test suites.
 */

export type PrimitiveTypeLabel = string;

/**
 * The backend resolves an IPv6 literal with `InetAddress`, which returns an *IPv4* address for
 * IPv4-mapped forms such as `::ffff:10.0.0.1`. Mirror that so the two runtimes agree.
 */
function isIpv6Address(value: string): boolean {
  if (!value.includes(':')) return false;
  if (!ipaddr.IPv6.isValid(value)) return false;
  return !ipaddr.IPv6.parse(value).isIPv4MappedAddress();
}

function isCidr(value: string, family: 'ipv4' | 'ipv6'): boolean {
  const slashIndex = value.lastIndexOf('/');
  if (slashIndex < 0) return false;
  const address = value.slice(0, slashIndex);
  const prefix = value.slice(slashIndex + 1);
  // The backend parses the prefix with Integer.parseInt on the raw substring, so anything that is
  // not a plain decimal is rejected before the range check.
  if (!/^\d+$/.test(prefix)) return false;
  const prefixLength = Number(prefix);
  if (family === 'ipv4') {
    return prefixLength <= 32 && ipaddr.IPv4.isValidFourPartDecimal(address);
  }
  return prefixLength <= 128 && isIpv6Address(address);
}

// Mirrors Apache Commons EmailValidator for the shapes the shared vectors pin: a single @, a
// non-empty local part without spaces, and a domain that is itself a valid host name.
// Same shape as the backend DOMAIN rule; only used to validate the domain part of an email, since
// the backend delegates that check to its own validator rather than exposing a pattern for EMAIL.
const DOMAIN_RULE = /^(?=.{1,253}$)(?:[a-zA-Z0-9\u00C0-\u024F\u0400-\u04FF\u4E00-\u9FFF](?:[a-zA-Z0-9\u00C0-\u024F\u0400-\u04FF\u4E00-\u9FFF-]{0,61}[a-zA-Z0-9\u00C0-\u024F\u0400-\u04FF\u4E00-\u9FFF])?\.)*(?![0-9]+$)[a-zA-Z0-9\u00C0-\u024F\u0400-\u04FF\u4E00-\u9FFF](?:[a-zA-Z0-9\u00C0-\u024F\u0400-\u04FF\u4E00-\u9FFF-]{0,61}[a-zA-Z0-9\u00C0-\u024F\u0400-\u04FF\u4E00-\u9FFF])?$/;

const EMAIL_LOCAL_PART = /^[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+)*$/;

function isEmail(value: string): boolean {
  const atIndex = value.lastIndexOf('@');
  if (atIndex <= 0 || atIndex === value.length - 1) return false;
  const local = value.slice(0, atIndex);
  const domain = value.slice(atIndex + 1);
  if (local.includes('@')) return false;
  return EMAIL_LOCAL_PART.test(local) && DOMAIN_RULE.test(domain);
}

/** Named kinds: no portable regex exists, so the frontend implements them itself. */
const NAMED_RULE_IMPLEMENTATIONS: Record<string, (value: string) => boolean> = {
  IPV4: value => ipaddr.IPv4.isValidFourPartDecimal(value),
  IPV6: value => isIpv6Address(value),
  IPV4_CIDR: value => isCidr(value, 'ipv4'),
  IPV6_CIDR: value => isCidr(value, 'ipv6'),
  EMAIL: value => isEmail(value),
};

/** Evaluates a single rule of a descriptor. A blank value never satisfies a format rule. */
export const matchesRule = (rule: PrimitiveTypeFormatRuleOutput, value: string): boolean => {
  const trimmed = value.trim();
  if (!trimmed) return false;
  if (rule.pattern) return new RegExp(rule.pattern).test(trimmed);
  const implementation = rule.kind ? NAMED_RULE_IMPLEMENTATIONS[rule.kind] : undefined;
  // An unknown named kind means the backend shipped a rule this build cannot evaluate. Accepting
  // is the only safe fallback: rejecting would block a value the backend considers perfectly valid.
  return implementation ? implementation(trimmed) : true;
};

/** Backend descriptors, indexed by the primitive type label used in conditions. */
export type DescriptorsByPrimitiveType = ReadonlyMap<PrimitiveTypeLabel, PrimitiveTypeDescriptorOutput>;

export const EMPTY_DESCRIPTORS: DescriptorsByPrimitiveType = new Map();

export const buildDescriptorsByType = (
  descriptors: PrimitiveTypeDescriptorOutput[],
): DescriptorsByPrimitiveType => new Map(
  descriptors
    .filter(descriptor => !!descriptor.primitive_type)
    .map(descriptor => [descriptor.primitive_type as string, descriptor]),
);

/**
 * Capabilities assumed while the descriptors are loading or when the call failed: the permissive
 * text behaviour, which is what every non-numeric type resolves to anyway. Never assume
 * `numeric_value`, or the UI would offer comparison operators the backend cannot evaluate.
 */
export const FALLBACK_CAPABILITIES = {
  numeric_value: false,
  case_sensitivity: true,
} as const;

export const capabilitiesOf = (
  field: PrimitiveTypeLabel,
  descriptorsByType: DescriptorsByPrimitiveType,
) => descriptorsByType.get(field)?.primitive_type_capabilities ?? FALLBACK_CAPABILITIES;

export const validationOf = (
  field: PrimitiveTypeLabel,
  descriptorsByType: DescriptorsByPrimitiveType,
) => descriptorsByType.get(field)?.primitive_type_validation;

/** Fallback key, used only when the backend ships a rule with no message key of its own. */
export const UNEXPECTED_FORMAT_ERROR = 'The value has an unexpected format';

/**
 * Validates a single exact value against a primitive type's format rules.
 *
 * This is the pure format facet, mirroring the backend `PrimitiveFormatValidator`: it knows nothing
 * about operators. Callers that validate a condition must gate it on the operator first - a
 * condition value is not always an exact value (`IN` is a substring match, `IS_NULL` carries none).
 * Callers that validate an intrinsically exact value, such as a scope variable, can use it as is.
 *
 * A type with no rule, or one this build cannot resolve, accepts everything: the backend stays the
 * authority, the frontend only surfaces the errors it can prove.
 *
 * @returns the untranslated error message key, or `undefined` when the value is acceptable
 */
export const getPrimitiveFormatError = (
  field: PrimitiveTypeLabel,
  value: string,
  descriptorsByType: DescriptorsByPrimitiveType,
): string | undefined => {
  const rules = validationOf(field, descriptorsByType)?.rules ?? [];
  if (rules.length === 0) return undefined;
  // ANY_OF semantics: one satisfied rule is enough.
  if (rules.some(rule => matchesRule(rule, value))) return undefined;
  return rules[0].error_message_key ?? UNEXPECTED_FORMAT_ERROR;
};
