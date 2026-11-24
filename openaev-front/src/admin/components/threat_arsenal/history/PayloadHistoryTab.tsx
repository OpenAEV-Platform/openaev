import { type FunctionComponent } from 'react';

import { fetchThreatArsenalHistory } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import ContentHistoryTable, { type ContentHistoryChanges } from '../../../../components/common/content_history/ContentHistoryTable';
import useContentHistory from '../../../../components/common/content_history/useContentHistory';
import { type ContentChangeOutput } from '../../../../utils/api-types';
import usePayloadVersions from '../versions/usePayloadVersions';
import VersionChanges, { VersionChangedFields } from '../versions/VersionChanges';

interface Props { actionId: string }

const VERSION_CHANGES = new Set<ContentChangeOutput['content_change_action']>(['UPDATE', 'SAVE']);

const PayloadHistoryTab: FunctionComponent<Props> = ({ actionId }) => {
  const history = useContentHistory(fetchThreatArsenalHistory, actionId);
  const versions = usePayloadVersions(actionId, true);

  // An update or a save reads "v1.13.0 → v1.14.0", the fields it changed, and opens on its before / after: its
  // version against the one before it (newest first).
  const changesOf = (entry: ContentChangeOutput): ContentHistoryChanges | null => {
    if (!VERSION_CHANGES.has(entry.content_change_action) || !versions) return null;
    const index = versions.findIndex(version => version.payload_version === entry.content_change_version);
    const after = versions[index];
    const before = index >= 0 ? versions[index + 1] : undefined;
    if (!after || !before) return null;
    return {
      fromVersion: before.payload_version,
      summary: <VersionChangedFields actionId={after.payload_action_id} />,
      details: <VersionChanges actionId={after.payload_action_id} beforeVersion={before.payload_version} afterVersion={after.payload_version} />,
    };
  };

  return <ContentHistoryTable history={history} changesOf={changesOf} />;
};

export default PayloadHistoryTab;
