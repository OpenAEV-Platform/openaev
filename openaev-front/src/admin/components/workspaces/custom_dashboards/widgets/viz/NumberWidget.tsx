import { Button } from '@mui/material';
import { type FunctionComponent, useContext } from 'react';
import { makeStyles } from 'tss-react/mui';

import ItemNumberDifference from '../../../../../../components/ItemNumberDifference';
import { type EsCountInterval } from '../../../../../../utils/api-types';
import { CustomDashboardContext } from '../../CustomDashboardContext';

const useStyles = makeStyles()(theme => ({
  number: {
    fontSize: 40,
    height: 50,
    fontWeight: 500,
    padding: 0,
    color: theme.palette.text.primary,
  },
  numberContainer: {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1),
  },
}));

interface Props {
  widgetId: string;
  data: EsCountInterval;
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
    <div className={classes.numberContainer}>
      <Button onClick={onClick} className={classes.number} variant="text">
        {data.interval_count ?? '-'}

      </Button>
      <ItemNumberDifference
        difference={data.difference_count}
      />
    </div>
  );
};
export default NumberWidget;
