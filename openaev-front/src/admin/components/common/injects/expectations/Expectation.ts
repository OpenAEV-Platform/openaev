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
}

export interface ExpectationInputForm extends Omit<ExpectationInput, 'expectation_expiration_time' | 'expectation_is_multi_selectable'> {
  expiration_time_days: number;
  expiration_time_hours: number;
  expiration_time_minutes: number;
}

export enum ExpectationType {
  PREVENTION = 'PREVENTION',
  DETECTION = 'DETECTION',
  VULNERABILITY = 'VULNERABILITY',
  MANUAL = 'MANUAL',
  ARTICLE = 'ARTICLE',
  CHALLENGE = 'CHALLENGE',
}

export type ExpectationResultType = 'PREVENTION' | 'DETECTION' | 'VULNERABILITY' | 'HUMAN_RESPONSE';

export const expectationResultTypes: ExpectationResultType [] = ['PREVENTION', 'DETECTION', 'VULNERABILITY', 'HUMAN_RESPONSE'];

export const mitreMatrixExpectationTypes: ExpectationResultType [] = ['PREVENTION', 'DETECTION', 'VULNERABILITY'];
