import { type FunctionComponent } from 'react';

import { fetchThreatArsenalHistory } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import CatalogHistoryTable, { type CatalogHistoryChanges } from '../../../../components/common/catalog_history/CatalogHistoryTable';
import useCatalogHistory from '../../../../components/common/catalog_history/useCatalogHistory';
import { type CatalogHistoryOutput } from '../../../../utils/api-types';
import usePayloadVersions from '../versions/usePayloadVersions';
import VersionChanges, { VersionChangedFields } from '../versions/VersionChanges';

interface Props { actionId: string }

const PayloadHistoryTab: FunctionComponent<Props> = ({ actionId }) => {
  const history = useCatalogHistory(fetchThreatArsenalHistory, actionId);
  const versions = usePayloadVersions(actionId, true);

  // An update reads "v1.13.0 → v1.14.0", the fields it changed, and opens on its before / after: its version
  // against the installed one before it (newest first).
  const changesOf = (entry: CatalogHistoryOutput): CatalogHistoryChanges | null => {
    if (entry.catalog_history_action !== 'UPDATE' || !versions) return null;
    const index = versions.findIndex(version => version.payload_version === entry.catalog_history_version);
    const after = versions[index];
    const before = index >= 0 ? versions[index + 1] : undefined;
    if (!after || !before) return null;
    const pair = {
      beforeActionId: before.payload_action_id,
      afterActionId: after.payload_action_id,
    };
    return {
      fromVersion: before.payload_version,
      summary: <VersionChangedFields {...pair} />,
      details: <VersionChanges {...pair} beforeVersion={before.payload_version} afterVersion={after.payload_version} />,
    };
  };

  return <CatalogHistoryTable history={history} changesOf={changesOf} />;
};

export default PayloadHistoryTab;
