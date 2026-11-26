import { useFormatter } from '../../../../../components/i18n';
import { type PayloadUsageMoveOutput } from '../../../../../utils/api-types';
import { MESSAGING$ } from '../../../../../utils/Environment';

/** Tells how a move to a version went: what moved, or what stayed where it was. */
const useNotifyMoves = () => {
  const { t } = useFormatter();
  return (results: PayloadUsageMoveOutput[], version: string) => {
    const moved = results.reduce((sum, result) => sum + result.moved_ids.length, 0);
    const refused = results.reduce((sum, result) => sum + result.refused.length, 0);
    if (refused > 0) {
      MESSAGING$.notifyError(t('{count} usages could not move and stay on the previous version', { count: String(refused) }));
    } else {
      MESSAGING$.notifySuccess(t('{count} usages moved to v{version}', {
        count: String(moved),
        version,
      }));
    }
  };
};

export default useNotifyMoves;
