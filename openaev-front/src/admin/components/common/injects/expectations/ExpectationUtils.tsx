import { type InjectExpectationOutput, type InjectExpectationResult } from '../../../../../utils/api-types';
import expectationIconByType from '../../ExpectationIconByType';
import { ExpectationType, type InjectExpectationsStore } from './Expectation';

export const FAILED = 'Failed';

export const HUMAN_EXPECTATION = ['MANUAL', 'CHALLENGE', 'ARTICLE'];

export const isAutomatic = (type: string) => {
  return [ExpectationType.ARTICLE.toString(), ExpectationType.PREVENTION.toString(), ExpectationType.DETECTION.toString(), ExpectationType.VULNERABILITY.toString()].includes(type);
};

// Single source of truth for expectation-type icons (shared coherent set).
export const typeIcon = (type: string) => expectationIconByType(type);

export const isTechnicalExpectation = (type: string) => {
  return [ExpectationType.PREVENTION.toString(), ExpectationType.DETECTION.toString(), ExpectationType.VULNERABILITY.toString()].includes(type);
};
export const isManualExpectation = (type: string) => {
  return [ExpectationType.MANUAL.toString(), ExpectationType.ARTICLE.toString(), ExpectationType.CHALLENGE.toString()].includes(type);
};

/**
 * Returns a formatted label for the source of an expectation result.
 *
 * @param {InjectExpectationResult | null | undefined} expectationResult - The result object containing source information.
 * @returns {string} The formatted source label, e.g. "sourceName (sourcePlatform)" or "-" if not available.
 */
export const getSourceLabel = (
  expectationResult?: InjectExpectationResult | null,
): string => {
  const sourceName = expectationResult?.sourceName?.trim();
  const sourcePlatform = expectationResult?.sourcePlatform?.trim();

  if (!sourceName) {
    return '-';
  }

  return sourcePlatform ? `${sourceName} (${sourcePlatform})` : sourceName;
};

export const groupedByAsset = (es: InjectExpectationsStore[]): Map<string, InjectExpectationsStore[]> => {
  return es.reduce((group, expectation) => {
    const { inject_expectation_asset } = expectation;
    if (inject_expectation_asset) {
      const values = group.get(inject_expectation_asset) ?? [];
      values.push(expectation);
      group.set(inject_expectation_asset, values);
    }
    return group;
  }, new Map());
};

export const isAssetGroupExpectation = (injectExpectation: InjectExpectationOutput) => {
  return injectExpectation.inject_expectation_asset_group != null
    && injectExpectation.inject_expectation_asset == null
    && injectExpectation.inject_expectation_agent == null;
};

export const isAssetExpectation = (injectExpectation: InjectExpectationOutput) => {
  return injectExpectation.inject_expectation_asset != null
    && injectExpectation.inject_expectation_agent == null;
};

export const isAgentExpectation = (injectExpectation: InjectExpectationOutput) => {
  return injectExpectation.inject_expectation_agent != null;
};

export const isPlayerExpectation = (injectExpectation: InjectExpectationOutput) => {
  return injectExpectation.inject_expectation_user != null;
};

export const useIsManuallyUpdatable = (injectExpectation: InjectExpectationOutput) => {
  const expectationType = injectExpectation.inject_expectation_type;

  // Technical: manually updatable at agent level (result on that agent only, then
  // propagated up) and at asset level (result written on each agent of the endpoint).
  // Asset groups are always computed from their assets, never updated directly.
  if (['DETECTION', 'PREVENTION', 'VULNERABILITY'].includes(expectationType)) {
    return !isAssetGroupExpectation(injectExpectation);
  }
  // Human
  if (isManualExpectation(expectationType)) {
    if ((injectExpectation.inject_expectation_results?.length ?? 0) > 0) return false;

    return true;
  }
  return false;
};
