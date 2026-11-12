import { SECURITY_PLATFORM_TYPE_LABELS, type SecurityPlatformType } from '../../../../../components/securityPlatformType';
import { type InjectExpectationOutput } from '../../../../../utils/api-types';

export interface InjectExpectationsStore extends Omit<InjectExpectationOutput, 'inject_expectation_team' | 'inject_expectation_user' | 'inject_expectation_article' | 'inject_expectation_challenge' | 'inject_expectation_asset'> {
  inject_expectation_id: string;
  inject_expectation_type: InjectExpectationOutput['inject_expectation_type'];
  inject_expectation_expected_score: number;
  inject_expectation_expiration_time: number;
  inject_expectation_team: string | undefined;
  inject_expectation_user: string | undefined;
  inject_expectation_article: string | undefined;
  inject_expectation_challenge: string | undefined;
  inject_expectation_asset: string | undefined;
}

export interface ExpectationInput {
  expectation_type: string;
  expectation_name: string;
  expectation_description?: string;
  expectation_score: number;
  expectation_expectation_group: boolean;
  expectation_expiration_time: number;
  expectation_is_multi_selectable?: boolean;
  // Security platform types expected to fulfil this expectation (empty = any platform).
  expectation_expected_security_platform_types?: string[];
  expectation_is_predefined: boolean;
  // Display order within the inject, declared by the injector contract (e.g. phishing orders its
  // steps email -> link -> submission). Not user-editable: carried through edits so the results
  // timeline keeps its logical order. Null/undefined means unordered.
  expectation_order?: number | null;
}

// Derived from the canonical type map so a platform type added server-side
// (e.g. VULNERABILITY_SCANNER) always shows up in the expectation forms.
export const SECURITY_PLATFORM_TYPES = Object.keys(SECURITY_PLATFORM_TYPE_LABELS) as SecurityPlatformType[];

// expectation_order is deliberately not part of the form: it is contract-declared, not
// user-editable, and ExpectationPopover carries it through on submit.
export interface ExpectationInputForm extends Omit<ExpectationInput, 'expectation_expiration_time' | 'expectation_is_multi_selectable' | 'expectation_order'> {
  expiration_time_days: number;
  expiration_time_hours: number;
  expiration_time_minutes: number;
}

export enum ExpectationType {
  PREVENTION = 'PREVENTION',
  DETECTION = 'DETECTION',
  LOGGING = 'LOGGING',
  VULNERABILITY = 'VULNERABILITY',
  MANUAL = 'MANUAL',
  ARTICLE = 'ARTICLE',
  CHALLENGE = 'CHALLENGE',
}

export type ExpectationResultType = 'PREVENTION' | 'DETECTION' | 'LOGGING' | 'VULNERABILITY' | 'HUMAN_RESPONSE';

export const expectationResultTypes: ExpectationResultType [] = ['PREVENTION', 'DETECTION', 'LOGGING', 'VULNERABILITY', 'HUMAN_RESPONSE'];

export const mitreMatrixExpectationTypes: ExpectationResultType [] = ['PREVENTION', 'DETECTION', 'VULNERABILITY'];

// Answered by security platforms, and scoped per security platform type. Display order.
export const TECHNICAL_EXPECTATION_TYPES: string[] = [ExpectationType.PREVENTION, ExpectationType.DETECTION, ExpectationType.VULNERABILITY];

// Validated by people. Display order.
export const HUMAN_EXPECTATION_TYPES: string[] = [ExpectationType.MANUAL, ExpectationType.ARTICLE, ExpectationType.CHALLENGE];

// The expectation types a posture (assets, security platforms, reports) is broken down by.
export const POSTURE_EXPECTATION_TYPES: string[] = [...TECHNICAL_EXPECTATION_TYPES, ...HUMAN_EXPECTATION_TYPES];

// i18n key of each expectation type label, the HUMAN_RESPONSE result type included.
const EXPECTATION_TYPE_LABELS: Record<string, string> = {
  PREVENTION: 'Prevention',
  DETECTION: 'Detection',
  LOGGING: 'Logging',
  VULNERABILITY: 'Vulnerability',
  HUMAN_RESPONSE: 'Human Response',
  MANUAL: 'Manual',
  ARTICLE: 'Article',
  CHALLENGE: 'Challenge',
};

// Case-insensitive: the dashboards hand over lowercase keys. Undefined for an unknown type, so
// each caller keeps its own fallback.
export const expectationTypeLabelKey = (type: string | undefined): string | undefined => {
  return EXPECTATION_TYPE_LABELS[(type ?? '').toUpperCase()];
};
