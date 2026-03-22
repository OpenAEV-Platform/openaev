import { Button } from '@mui/material';
import { type FunctionComponent, memo, useCallback, useContext } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type EsCountInterval } from '../../../../../../utils/api-types';
import { CustomDashboardContext } from '../../CustomDashboardContext';

const useStyles = makeStyles()(theme => ({
  number: {
    fontSize: 32,
    fontWeight: 600,
    lineHeight: 1,
    padding: 0,
    minWidth: 0,
    justifyContent: 'flex-start',
    color: theme.palette.text.primary,
  },
  container: {
    display: 'flex',
    alignItems: 'center',
    height: '100%',
  },
}));

interface Props {
  widgetId: string;
  data: EsCountInterval;
}

const NumberWidget: FunctionComponent<Props> = ({ widgetId, data }) => {
  const { classes } = useStyles();

  const { openWidgetDataDrawer } = useContext(CustomDashboardContext);

  const onClick = useCallback(() => {
    openWidgetDataDrawer({
      widgetId,
      filter_values_map: {},
      series_index: 0,
    });
  }, [openWidgetDataDrawer, widgetId]);

  return (
    <div className={classes.container}>
      <Button onClick={onClick} className={classes.number} variant="text">
        {data.interval_count ?? '-'}
      </Button>
    </div>
  );
};

export default memo(NumberWidget);
