import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext } from 'react';
import Chart from 'react-apexcharts';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../../components/i18n';
import { donutChartOptions } from '../../../../../../utils/Charts';
import { CustomDashboardContext } from '../../CustomDashboardContext';

interface Props {
  widgetId: string;
  datas: {
    x: string | undefined;
    y: number | undefined;
    meta: string | undefined;
  }[];
}

const useStyles = makeStyles()(() => ({ chartContainer: { '& .apexcharts-pie-area': { cursor: 'pointer' } } }));

const DonutChart: FunctionComponent<Props> = ({ widgetId, datas }: Props) => {
  const theme = useTheme();
  const { classes } = useStyles();
  const { t } = useFormatter();

  const { openWidgetDataDrawer } = useContext(CustomDashboardContext);

  const onClick = (_: Event, config: {
    seriesIndex: number;
    dataPointIndex: number;
  }) => {
    const dataPoint = datas[config.dataPointIndex];

    openWidgetDataDrawer({
      widgetId,
      filter_values: [dataPoint?.meta ?? ''],
      series_index: config.seriesIndex,
    });
  };
  return (
    <Chart
      options={donutChartOptions({
        theme,
        labels: datas.map(s => s?.x ?? t('-')),
        onClick,
      })}
      series={datas.map(s => s?.y ?? 0)}
      type="donut"
      width="100%"
      height="100%"
      className={classes.chartContainer}
    />
  );
};

export default DonutChart;
