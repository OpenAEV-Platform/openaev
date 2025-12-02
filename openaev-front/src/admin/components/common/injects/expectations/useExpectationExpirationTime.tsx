import { getPlatformSettingsSelector } from '../../../../../actions/selectors';
import { useSelectorHelper } from '../../../../../store';
import { type InjectExpectation } from '../../../../../utils/api-types';

const useExpectationExpirationTime = (expectationType: InjectExpectation['inject_expectation_type']): number => {
  const settings = useSelectorHelper(getPlatformSettingsSelector);
  switch (expectationType) {
    case 'DETECTION':
      return settings?.expectation_detection_expiration_time ?? 0;
    case 'PREVENTION':
      return settings?.expectation_prevention_expiration_time ?? 0;
    case 'CHALLENGE':
      return settings?.expectation_challenge_expiration_time ?? 0;
    case 'ARTICLE':
      return settings?.expectation_article_expiration_time ?? 0;
    case 'MANUAL':
      return settings?.expectation_manual_expiration_time ?? 0;
    default:
      return 0;
  }
};

export default useExpectationExpirationTime;
