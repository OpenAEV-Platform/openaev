import { Button } from '@mui/material';
import { type FunctionComponent, useContext } from 'react';
import { makeStyles } from 'tss-react/mui';

import { CustomDashboardContext } from '../../CustomDashboardContext';

const useStyles = makeStyles()(theme => ({
  number: {
    fontSize: 40,
    height: 50,
    fontWeight: 500,
    padding: 0,
    color: theme.palette.text.primary,
  },
}));

interface Props {
  widgetId: string;
  data: number;
}

const NumberWidget: FunctionComponent<Props> = ({ widgetId, data }) => {
  // Standard hooks
  const { classes } = useStyles();

  const { openWidgetDataDrawer } = useContext(CustomDashboardContext);

  const onClick = () => {
    openWidgetDataDrawer({
      widgetId,
      filter_values: [],
      series_index: 0,
    });
  };

  return (
    <div>
      <Button onClick={onClick} className={classes.number} variant="text">
        {data ?? '-'}
      </Button>
    </div>
  );
};
export default NumberWidget;
