import { type FunctionComponent } from 'react';

import { fetchThreatArsenalHistory } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import CatalogHistoryTable from '../../../../components/common/catalog_history/CatalogHistoryTable';
import useCatalogHistory from '../../../../components/common/catalog_history/useCatalogHistory';

interface Props { actionId: string }

const PayloadHistoryTab: FunctionComponent<Props> = ({ actionId }) => {
  const history = useCatalogHistory(fetchThreatArsenalHistory, actionId);
  return <CatalogHistoryTable history={history} />;
};

export default PayloadHistoryTab;
