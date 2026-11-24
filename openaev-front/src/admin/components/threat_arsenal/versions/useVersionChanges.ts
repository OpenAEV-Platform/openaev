import { fetchThreatArsenalVersionChanges } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type ChangesTableRow } from '../../../../components/common/update/ChangesTable';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadVersionChangeOutput } from '../../../../utils/api-types';
import useLoadedOnKey from '../../../../utils/hooks/useLoadedOnKey';
import versionFieldChanges from './versionFieldChanges';

export interface VersionsProps {
  // The action of the version whose changes are shown.
  actionId: string;
}

const loadChanges = (actionId: string) => fetchThreatArsenalVersionChanges(actionId)
  .then(({ data }: { data: PayloadVersionChangeOutput[] }) => data);

/** What one version changed from the one just below it, field by field; null while it loads. */
const useVersionChanges = ({ actionId }: VersionsProps): ChangesTableRow[] | null => {
  const { t } = useFormatter();
  const { value } = useLoadedOnKey<PayloadVersionChangeOutput[] | null>(actionId, loadChanges, null);
  if (!value) return null;
  return versionFieldChanges(value).map(row => ({
    ...row,
    field: t(row.field),
  }));
};

export default useVersionChanges;
