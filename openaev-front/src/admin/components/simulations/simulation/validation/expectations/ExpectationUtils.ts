import { type InjectExpectation } from '../../../../../../utils/api-types';
import { type InjectExpectationsStore } from '../../../../common/injects/expectations/Expectation';
import { isManualExpectation } from '../../../../common/injects/expectations/ExpectationUtils';

export const groupedByAsset = (es: InjectExpectationsStore[]) => {
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

export const isAssetGroupExpectation = (injectExpectation: InjectExpectation) => {
  return injectExpectation.inject_expectation_asset_group != null
    && injectExpectation.inject_expectation_asset == null
    && injectExpectation.inject_expectation_agent == null;
};

export const isAssetExpectation = (injectExpectation: InjectExpectation) => {
  return injectExpectation.inject_expectation_asset != null
    && injectExpectation.inject_expectation_agent == null;
};

export const isAgentExpectation = (injectExpectation: InjectExpectation) => {
  return injectExpectation.inject_expectation_agent != null;
};

export const isPlayerExpectation = (injectExpectation: InjectExpectation) => {
  return injectExpectation.inject_expectation_user != null;
};

export const useIsManuallyUpdatable = (injectExpectation: InjectExpectation) => {
  // Technical
  if (['DETECTION', 'PREVENTION'].includes(injectExpectation.inject_expectation_type)) {
    if (isAssetGroupExpectation(injectExpectation)) return false;

    return true;
  }
  // Human
  if (isManualExpectation(injectExpectation.inject_expectation_type)) {
    if ((injectExpectation.inject_expectation_results?.length ?? 0) > 0) return false;

    return true;
  }
  return false;
};
