import { type ChangesTableRow } from '../../../../components/common/update/ChangesTable';
import { useFormatter } from '../../../../components/i18n';
import useVersionDetails from './useVersionDetails';
import versionFieldChanges from './versionFieldChanges';

export interface VersionsProps {
  // The action of the version before, and of the version after.
  beforeActionId: string;
  afterActionId: string;
}

/** What one version changed from the one before it, field by field; null while both load. */
const useVersionChanges = ({ beforeActionId, afterActionId }: VersionsProps): ChangesTableRow[] | null => {
  const { t, tPick } = useFormatter();
  const before = useVersionDetails(beforeActionId);
  const after = useVersionDetails(afterActionId);
  if (!before || !after) return null;
  return versionFieldChanges(before, after, details => tPick(details.action_labels))
    .map(row => ({
      ...row,
      field: t(row.field),
    }));
};

export default useVersionChanges;
