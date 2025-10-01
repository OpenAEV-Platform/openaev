import { Card, CardContent, Chip, GridLegacy, Typography } from '@mui/material';
import { makeStyles } from 'tss-react/mui';

import { fetchExecutors } from '../../../actions/Executor';
import { type ExecutorHelper } from '../../../actions/executors/executor-helper';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { useFormatter } from '../../../components/i18n';
import SearchFilter from '../../../components/SearchFilter';
import { useHelper } from '../../../store';
import { type Executor } from '../../../utils/api-types';
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
}));

const Executors = () => {
  // Standard hooks
  const { t, nsdt } = useFormatter();
  const { classes } = useStyles();
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
    dispatch(fetchExecutors());
  });
  const sortedExecutors = filtering.filterAndSort(executors);
  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{ label: t('Integrations') }, {
          label: t('Executors'),
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
        {sortedExecutors.map((executor: Executor) => {
          return (
            <GridLegacy key={executor.executor_id} item={true} xs={3}>
              <Card classes={{ root: classes.card }} variant="outlined">
                <CardContent className={classes.content}>
                  <div style={{ display: 'flex' }}>
                    <div className={classes.icon}>
                      <img
                        src={`/api/images/executors/icons/${executor.executor_type}`}
                        alt={executor.executor_type}
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
                      {executor.executor_name}
                    </Typography>
                  </div>
                  <Chip
                    variant="outlined"
                    classes={{ root: classes.chipInList }}
                    style={{ width: 120 }}
                    color="secondary"
                    label={t('Built-in')}
                  />
                  <div style={{
                    display: 'flex',
                    marginTop: 30,
                  }}
                  >
                    <div className={classes.dotGreen} />
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
                      {nsdt(executor.executor_updated_at)}
                    </Typography>
                  </div>
                </CardContent>
              </Card>
            </GridLegacy>
          );
        })}
      </GridLegacy>
    </>
  );
};

export default Executors;
