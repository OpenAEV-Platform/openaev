import { Grid } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { type InjectorHelper } from '../../../../actions/injectors/injector-helper';
import SearchFilter from '../../../../components/SearchFilter';
import { useHelper } from '../../../../store';
import { type InjectorOutput } from '../../../../utils/api-types';
import useSearchAnFilter from '../../../../utils/SortingFiltering';
import ConnectorCard from '../common/ConnectorCard';

const Injectors = () => {
  // Standard hooks
  const theme = useTheme();

  // Filter and sort hook
  const searchColumns = ['name', 'description'];
  const filtering = useSearchAnFilter(
    'injector',
    'name',
    searchColumns,
  );

  // Fetching data
  const { injectors } = useHelper((helper: InjectorHelper) => ({ injectors: helper.getInjectors() }));

  const sortedInjectors = filtering.filterAndSort(injectors);

  return (
    <>
      <SearchFilter
        variant="small"
        onChange={filtering.handleSearch}
        keyword={filtering.keyword}
      />
      <Grid container={true} spacing={3} style={{ marginTop: theme.spacing(2) }}>
        {sortedInjectors.map((injector: InjectorOutput) => (
          <Grid key={injector.injector_id} size={{ xs: 4 }}>
            <ConnectorCard
              connector={{
                connectorName: injector.injector_name,
                connectorType: 'INJECTOR',
                connectorLogoName: injector.injector_type,
                connectorLogoUrl: `/api/images/injectors/${injector.injector_type}`,
                connectorDescription: injector.catalog?.catalog_connector_short_description,
                lastUpdatedAt: injector.injector_updated_at,
                isExternal: injector.injector_external,
                isVerified: injector.is_verified,
                connectorUseCases: [],
              }}
              cardActionUrl={`/admin/integrations/injectors/${injector.injector_id}`}
              showLastUpdatedAt
            />
          </Grid>
        ))}
      </Grid>
    </>
  );
};

export default Injectors;
