import { Grid } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { fetchCollectors } from '../../../../actions/Collector';
import { type CollectorHelper } from '../../../../actions/collectors/collector-helper';
import SearchFilter from '../../../../components/SearchFilter';
import { useHelper } from '../../../../store';
import { type CollectorSimpleOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import useSearchAnFilter from '../../../../utils/SortingFiltering';
import ConnectorCard from '../common/ConnectorCard';

const Collectors = () => {
  // Standard hooks
  const theme = useTheme();
  const dispatch = useAppDispatch();

  // Filter and sort hook
  const searchColumns = ['name', 'description'];
  const filtering = useSearchAnFilter(
    'collector',
    'name',
    searchColumns,
  );

  // Fetching data
  const { collectors } = useHelper((helper: CollectorHelper) => ({ collectors: helper.getCollectors() }));
  useDataLoader(() => {
    dispatch(fetchCollectors(true));
  });
  const sortedCollectors = filtering.filterAndSort(collectors);
  return (
    <>
      <SearchFilter
        variant="small"
        onChange={filtering.handleSearch}
        keyword={filtering.keyword}
      />
      <div className="clearfix" />
      <Grid container={true} spacing={3} style={{ marginTop: theme.spacing(2) }}>
        {sortedCollectors.map((collector: CollectorSimpleOutput) => (
          <Grid key={collector.collector_id} size={{ xs: 4 }}>
            <ConnectorCard
              connector={{
                connectorName: collector.collector_name,
                connectorType: 'COLLECTOR',
                connectorLogoName: collector.collector_type,
                connectorLogoUrl: `/api/images/collectors/${collector.collector_type}`,
                connectorDescription: collector.catalog?.catalog_connector_short_description,
                lastUpdatedAt: collector.collector_last_execution,
                isExternal: collector.collector_external,
                isVerified: collector.is_verified,
                connectorUseCases: [],
              }}
              cardActionUrl={`/admin/integrations/collectors/${collector.collector_id}`}
              isNotClickable={collector.catalog == null}
              showLastUpdatedAt
            />
          </Grid>
        ))}
      </Grid>
    </>
  );
};

export default Collectors;
