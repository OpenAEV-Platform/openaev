import { type FunctionComponent } from 'react';

import { searchThreatArsenalHistory } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import ContentHistoryTable, { type ContentHistoryChanges } from '../../../../components/common/content_history/ContentHistoryTable';
import useContentHistory from '../../../../components/common/content_history/useContentHistory';
import { type PayloadContentChangeOutput } from '../../../../utils/api-types';
import VersionChanges, { VersionChangedFields } from '../versions/VersionChanges';

interface Props { actionId: string }

const PayloadHistoryTab: FunctionComponent<Props> = ({ actionId }) => {
  const history = useContentHistory<PayloadContentChangeOutput>(searchThreatArsenalHistory, actionId);

  // An update or a save reads "v1.13.0 → v1.14.0", the fields it changed, and opens on its before / after.
  const changesOf = (entry: PayloadContentChangeOutput): ContentHistoryChanges | null => {
    const fromVersion = entry.content_change_from_version;
    const changes = entry.content_change_fields;
    if (!fromVersion || !changes) return null;
    return {
      fromVersion,
      summary: <VersionChangedFields changes={changes} />,
      details: <VersionChanges changes={changes} beforeVersion={fromVersion} afterVersion={entry.content_change_version} />,
    };
  };

  return <ContentHistoryTable history={history} changesOf={changesOf} />;
};

export default PayloadHistoryTab;
