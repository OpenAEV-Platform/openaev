import { type AxiosResponse } from 'axios';

import { type Translate } from '../../../../components/i18n';
import { MESSAGING$ } from '../../../../utils/Environment';

// Set by the server on a bulk "add to scenario(s)": selected actions whose payload is not approved
// are skipped instead of failing the whole operation.
export const SKIPPED_ACTIONS_HEADER = 'x-openaev-skipped-actions';

export const skippedActionsCount = (response: AxiosResponse): number => {
  const count = Number(response.headers?.[SKIPPED_ACTIONS_HEADER] ?? 0);
  return Number.isFinite(count) ? count : 0;
};

const notifySkippedActions = (response: AxiosResponse, t: Translate) => {
  const count = skippedActionsCount(response);
  if (count > 0) {
    MESSAGING$.notifySuccess(t('{count, plural, one {# selected action was not added: its payload is not approved} other {# selected actions were not added: their payload is not approved}}', { count: String(count) }));
  }
};

export default notifySkippedActions;
