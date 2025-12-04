import { AutoModeOutlined, SubscriptionsOutlined } from '@mui/icons-material';
import { Card, CardActionArea, CardContent, Chip, Grid, GridLegacy, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchInjectors } from '../../../../actions/Injectors';
import { type InjectorHelper } from '../../../../actions/injectors/injector-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import SearchFilter from '../../../../components/SearchFilter';
import { useHelper } from '../../../../store';
import { type Collector, type Injector } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import useSearchAnFilter from '../../../../utils/SortingFiltering';

const Injectors = () => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();

  // Filter and sort hook
  const searchColumns = ['name', 'description'];
  const filtering = useSearchAnFilter(
    'injector',
    'name',
    searchColumns,
  );

  // Fetching data
  const { injectors } = useHelper((helper: InjectorHelper) => ({ injectors: helper.getInjectors() }));
  useDataLoader(() => {
    dispatch(fetchInjectors());
  });
  const sortedInjectors = filtering.filterAndSort(injectors);
  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{ label: t('Integrations') }, {
          label: t('Injectors'),
          current: true,
        }]}
      />
      <SearchFilter
        variant="small"
        onChange={filtering.handleSearch}
        keyword={filtering.keyword}
      />
      <div className="clearfix" />
      <Grid container={true} spacing={3} style={{ marginTop: theme.spacing(2) }}>
        {sortedInjectors.map((injector: Injector) => (
          <Grid key={injector.injector_id} size={{ xs: 4 }}>
            // TODO
          </Grid>
        ))}
      </Grid>
    </>
  );
};

export default Injectors;
