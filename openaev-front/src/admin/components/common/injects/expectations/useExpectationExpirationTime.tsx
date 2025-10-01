import { type LoggedHelper } from '../../../../../actions/helper';
import { useHelper } from '../../../../../store';
import { type InjectExpectation, type PlatformSettings } from '../../../../../utils/api-types';

const useExpectationExpirationTime = (expectationType: InjectExpectation['inject_expectation_type']): number => {
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));
  switch (expectationType) {
    case 'DETECTION':
      return settings.expectation_detection_expiration_time;
    case 'PREVENTION':
      return settings.expectation_prevention_expiration_time;
    case 'CHALLENGE':
      return settings.expectation_challenge_expiration_time;
    case 'ARTICLE':
      return settings.expectation_article_expiration_time;
    case 'MANUAL':
      return settings.expectation_manual_expiration_time;
    default:
      return 0;
  }
};

export default useExpectationExpirationTime;
