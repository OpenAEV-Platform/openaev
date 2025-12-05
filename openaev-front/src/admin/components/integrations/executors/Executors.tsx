import { Grid } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { fetchExecutors } from '../../../../actions/executors/executor-action';
import type { ExecutorHelper } from '../../../../actions/executors/executor-helper';
import SearchFilter from '../../../../components/SearchFilter';
import { useHelper } from '../../../../store';
import { type ExecutorOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import useSearchAnFilter from '../../../../utils/SortingFiltering';
import ConnectorCard from '../common/ConnectorCard';

const Executors = () => {
  // Standard hooks
  const theme = useTheme();
  const dispatch = useAppDispatch();

  // Filter and sort hook
  const searchColumns = ['name', 'description'];
  const filtering = useSearchAnFilter(
    'executor',
    'name',
    searchColumns,
  );

  // Fetching data
  const { executors } = useHelper((helper: ExecutorHelper) => ({ executors: helper.getExecutors() }));
  useDataLoader(() => {
    dispatch(fetchExecutors(true));
  });
  const sortedExecutors = filtering.filterAndSort(executors);
  return (
    <>
      <SearchFilter
        variant="small"
        onChange={filtering.handleSearch}
        keyword={filtering.keyword}
      />
      <div className="clearfix" />
      <Grid container={true} spacing={3} style={{ marginTop: theme.spacing(2) }}>
        {sortedExecutors.map((executor: ExecutorOutput) => (
          <Grid key={executor.executor_id} size={{ xs: 4 }}>
            <ConnectorCard
              connector={{
                connectorId: executor.executor_id,
                connectorName: executor.executor_name,
                connectorType: 'EXECUTOR',
                connectorLogoName: executor.executor_type,
                connectorLogoUrl: `/api/images/executors/icons/${executor.executor_type}`,
                connectorDescription: executor.catalog?.catalog_connector_short_description,
                lastUpdatedAt: executor.executor_updated_at,
                isVerified: executor.is_verified,
                connectorUseCases: [],
              }}
              cardActionUrl={`/admin/integrations/executors/${executor.executor_id}`}
              isNotClickable={executor.catalog == null}
              showLastUpdatedAt
            />
          </Grid>
        ))}
      </Grid>
    </>
  );
};

export default Executors;
