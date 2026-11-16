import { type LoggedHelper } from '../../../../../actions/helper';
import { useHelper } from '../../../../../store';
import { type InjectExpectationOutput, type PlatformSettings } from '../../../../../utils/api-types';

type ExpirationTimeSetting = 'expectation_detection_expiration_time'
  | 'expectation_prevention_expiration_time'
  | 'expectation_challenge_expiration_time'
  | 'expectation_article_expiration_time'
  | 'expectation_manual_expiration_time';

// The platform setting holding the default expiration time of each expectation type.
const EXPIRATION_TIME_SETTINGS: Partial<Record<InjectExpectationOutput['inject_expectation_type'], ExpirationTimeSetting>> = {
  DETECTION: 'expectation_detection_expiration_time',
  PREVENTION: 'expectation_prevention_expiration_time',
  CHALLENGE: 'expectation_challenge_expiration_time',
  ARTICLE: 'expectation_article_expiration_time',
  MANUAL: 'expectation_manual_expiration_time',
};

const useExpectationExpirationTime = (expectationType: InjectExpectationOutput['inject_expectation_type']): number => {
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));
  const setting = EXPIRATION_TIME_SETTINGS[expectationType];
  return setting ? settings[setting] : 0;
};

export default useExpectationExpirationTime;
