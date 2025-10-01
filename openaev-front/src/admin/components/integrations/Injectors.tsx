import { AutoModeOutlined, SubscriptionsOutlined } from '@mui/icons-material';
import { Card, CardActionArea, CardContent, Chip, GridLegacy, Tooltip, Typography } from '@mui/material';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchInjectors } from '../../../actions/Injectors';
import { type InjectorHelper } from '../../../actions/injectors/injector-helper';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { useFormatter } from '../../../components/i18n';
import SearchFilter from '../../../components/SearchFilter';
import { useHelper } from '../../../store';
import { type Injector } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import useSearchAnFilter from '../../../utils/SortingFiltering';

const useStyles = makeStyles()(theme => ({
  parameters: { marginTop: -3 },
  card: {
    position: 'relative',
    overflow: 'hidden',
    height: 180,
  },
  area: {
    width: '100%',
    height: '100%',
  },
  content: { padding: 20 },
  icon: { padding: 0 },
  chipInList: {
    marginTop: 10,
    fontSize: 12,
    height: 20,
    textTransform: 'uppercase',
    borderRadius: 4,
  },
  dotGreen: {
    height: 15,
    width: 15,
    backgroundColor: theme.palette.success.main,
    borderRadius: '50%',
  },
  dotRed: {
    height: 15,
    width: 15,
    backgroundColor: theme.palette.error.main,
    borderRadius: '50%',
  },
  customizable: {
    position: 'absolute',
    top: 10,
    right: 10,
  },
  payload: {
    position: 'absolute',
    top: 10,
  },
}));

const Injectors = () => {
  // Standard hooks
  const { t, nsdt } = useFormatter();
  const { classes } = useStyles();
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
      <div className={classes.parameters}>
        <div style={{
          float: 'left',
          marginRight: 10,
        }}
        >
          <SearchFilter
            variant="small"
            onChange={filtering.handleSearch}
            keyword={filtering.keyword}
          />
        </div>
      </div>
      <div className="clearfix" />
      <GridLegacy container={true} spacing={3}>
        {sortedInjectors.map((injector: Injector) => {
          return (
            <GridLegacy key={injector.injector_id} item={true} xs={3}>
              <Card classes={{ root: classes.card }} variant="outlined">
                <CardActionArea
                  classes={{ root: classes.area }}
                  component={Link}
                  to={`/admin/integrations/injectors/${injector.injector_id}`}
                >
                  <CardContent className={classes.content}>
                    <div style={{ display: 'flex' }}>
                      <div className={classes.icon}>
                        <img
                          src={`/api/images/injectors/${injector.injector_type}`}
                          alt={injector.injector_type}
                          style={{
                            width: 50,
                            height: 50,
                            borderRadius: 4,
                          }}
                        />
                      </div>
                      <Typography
                        variant="h1"
                        style={{
                          margin: '14px 0 0 10px',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                        }}
                      >
                        {injector.injector_name}
                      </Typography>
                    </div>
                    <Chip
                      variant="outlined"
                      classes={{ root: classes.chipInList }}
                      style={{ width: 120 }}
                      color={injector.injector_external ? 'primary' : 'secondary'}
                      label={t(injector.injector_external ? 'External' : 'Built-in')}
                    />
                    <div style={{
                      display: 'flex',
                      marginTop: 30,
                    }}
                    >
                      {
                        (injector.injector_external && injector.injector_updated_at) || !injector.injector_external
                          ? <div className={classes.dotGreen} /> : <div className={classes.dotRed} />
                      }
                      <Typography
                        variant="h4"
                        style={{
                          margin: '1px 0 0 10px',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                        }}
                      >
                        {t('Updated at')}
                        {' '}
                        {nsdt(injector.injector_updated_at)}
                      </Typography>
                    </div>
                    {injector.injector_custom_contracts && (
                      <div className={classes.customizable}>
                        <Tooltip title={t('Supporting adding new contracts')}>
                          <AutoModeOutlined color="success" />
                        </Tooltip>
                      </div>
                    )}
                    {injector.injector_payloads && (
                      <div className={classes.payload} style={{ right: injector.injector_custom_contracts ? 40 : 10 }}>
                        <Tooltip title={t('Supporting payloads')}>
                          <SubscriptionsOutlined color="success" />
                        </Tooltip>
                      </div>
                    )}
                  </CardContent>
                </CardActionArea>
              </Card>
            </GridLegacy>
          );
        })}
      </GridLegacy>
    </>
  );
};

export default Injectors;
