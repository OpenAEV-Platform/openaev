import {
  changeThreatArsenalVersion,
  fetchThreatArsenalUsages,
} from '../../../actions/threat_arsenals/threatArsenal-version-actions';
import { type useFormatter } from '../../../components/i18n';
import { type PayloadUsageOutput, type PayloadVersionChangeOutput } from '../../../utils/api-types';
import { MESSAGING$ } from '../../../utils/Environment';

/**
 * Moves every scenario inject, atomic test and chained step of a version (its action) to another
 * version. Each one is checked on its own server side: those the user cannot change stay and are
 * reported.
 */
const movePayloadVersionUsages = (
  fromActionId: string,
  targetPayloadId: string,
  targetVersion: string,
  t: ReturnType<typeof useFormatter>['t'],
) => fetchThreatArsenalUsages(fromActionId)
  .then((response: { data: PayloadUsageOutput[] }) => changeThreatArsenalVersion(fromActionId, {
    target_payload_id: targetPayloadId,
    inject_ids: response.data.filter(u => u.usage_type !== 'step').map(u => u.usage_id),
    step_ids: response.data.filter(u => u.usage_type === 'step').map(u => u.usage_id),
  }))
  .then((response: { data: PayloadVersionChangeOutput }) => {
    const refused = response.data.refused?.length ?? 0;
    if (refused > 0) {
      MESSAGING$.notifyError(t('{count} usages could not move and stay on the previous version', { count: String(refused) }));
    } else {
      MESSAGING$.notifySuccess(t('{count} usages moved to v{version}', {
        count: String(response.data.moved_ids?.length ?? 0),
        version: targetVersion,
      }));
    }
  });

export default movePayloadVersionUsages;
