import { type FunctionComponent } from 'react';
import { useParams } from 'react-router';

import { fetchScenarioHistory } from '../../../../../actions/scenarios/scenario-actions';
import CatalogHistoryTable from '../../../../../components/common/catalog_history/CatalogHistoryTable';
import useCatalogHistory from '../../../../../components/common/catalog_history/useCatalogHistory';

const ScenarioHistory: FunctionComponent = () => {
  const { scenarioId } = useParams() as { scenarioId: string };
  const history = useCatalogHistory(fetchScenarioHistory, scenarioId);
  return <CatalogHistoryTable history={history} />;
};

export default ScenarioHistory;
