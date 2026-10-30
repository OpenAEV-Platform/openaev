import { type FunctionComponent, useEffect, useState } from 'react';

import { fetchThreatArsenalHistory } from '../../../actions/threat_arsenals/threatArsenal-version-actions';
import { ContentHistoryTable } from '../../../components/common/content_versions/ContentVersionsTable';
import { type ContentHistoryOutput } from '../../../utils/api-types';

interface Props { actionId: string }

// F7: who installed, updated or revoked a version of the payload, and when.
const PayloadHistoryTab: FunctionComponent<Props> = ({ actionId }) => {
  const [history, setHistory] = useState<ContentHistoryOutput[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    fetchThreatArsenalHistory(actionId)
      // Moved injects belong to the audit log, not to the life of the payload.
      .then(response => setHistory((response.data as ContentHistoryOutput[]).filter(entry => entry.content_history_action !== 'VERSION_CHANGE')))
      .finally(() => setLoading(false));
  }, [actionId]);

  return <ContentHistoryTable history={history} loading={loading} />;
};

export default PayloadHistoryTab;
