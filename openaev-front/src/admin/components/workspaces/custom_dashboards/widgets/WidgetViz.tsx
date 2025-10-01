import { ArrowDownwardOutlined, ArrowForwardOutlined, ArrowUpwardOutlined } from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { memo, useContext, useEffect, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import {
  type EsAttackPath,
  type EsBase,
  type EsCountInterval,
  type EsSeries,
  type ListConfiguration,
} from '../../../../../utils/api-types';
import { type StructuralHistogramWidget, type Widget } from '../../../../../utils/api-types-custom';
import { CustomDashboardContext } from '../CustomDashboardContext';
import { ALL_TIME_TIME_RANGE, getTimeRangeFromDashboard, getTimeRangeItem } from './configuration/common/TimeRangeUtils';
import AttackPathContextLayer from './viz/attack_paths/AttackPathContextLayer';
import DonutChart from './viz/DonutChart';
import HorizontalBarChart from './viz/HorizontalBarChart';
import LineChart from './viz/LineChart';
import ListWidget from './viz/list/ListWidget';
import NumberWidget from './viz/NumberWidget';
import SecurityCoverage from './viz/SecurityCoverage';
import VerticalBarChart from './viz/VerticalBarChart';
import { getWidgetTitle } from './WidgetUtils';

interface WidgetTemporalVizProps {
  widget: Widget;
  fullscreen: boolean;
  setFullscreen: (fullscreen: boolean) => void;
  setTooltipMessage: (tooltipMessage: React.ReactNode) => void;
}

export type SerieData = {
  x?: string;
  y?: string;
  meta?: string;
};

const WidgetViz = ({ widget, fullscreen, setFullscreen, setTooltipMessage }: WidgetTemporalVizProps) => {
  const { t } = useFormatter();
  const [seriesVizData, setSeriesVizData] = useState<EsSeries[]>([]);
  const [entitiesVizData, setEntitiesVizData] = useState<EsBase[]>([]);
  const [attackPathsVizData, setAttackPathsVizData] = useState<EsAttackPath[]>([]);
  const [numberVizData, setNumberVizData] = useState<EsCountInterval>({
    interval_count: 0,
    previous_interval_count: 0,
    difference_count: 0,
  });
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string>('');

  const { customDashboardParameters, customDashboard, fetchCount, fetchSeries, fetchEntities, fetchAttackPaths } = useContext(CustomDashboardContext);

  const createNumberTooltipContent = () => {
    const { difference_count, previous_interval_count } = numberVizData;
    // extract serie's name (only 1 serie for number widget)
    let resourceName = '';
    if ('series' in widget.widget_config) {
      const seriesItem = widget.widget_config.series[0];
      if (seriesItem) {
        const entityName = seriesItem.filter?.filters
          ?.find(f => f.key === 'base_entity')
          ?.values?.[0];

        resourceName = difference_count <= 1 && difference_count >= -1
          ? t(`${entityName}-singular`)
          : t(`${entityName}-plural`);
      }
    }
    // Compute the widget time range to get the correct sentence
    let widgetTimeRange;
    if (widget.widget_config.time_range == 'DEFAULT') {
      widgetTimeRange = getTimeRangeFromDashboard(customDashboard, customDashboardParameters);
    } else {
      widgetTimeRange = widget.widget_config.time_range;
    }
    const formattedDiff
        = !difference_count || difference_count > 0 ? `+${difference_count}` : `${difference_count}`;
    // Pick icon & color based on difference_count
    let Icon = ArrowForwardOutlined;
    let color = 'grey.400';
    if (difference_count && difference_count > 0) {
      Icon = ArrowUpwardOutlined;
      color = 'success.main';
    } else if (difference_count && difference_count < 0) {
      Icon = ArrowDownwardOutlined;
      color = 'error.main';
    }
    const labelKey = `${getTimeRangeItem(widgetTimeRange)?.label_key}_progression`;
    return (
      <Box display="flex" alignItems="center">
        <Icon sx={{ color }} fontSize="small" />
        <Typography
          variant="body2"
          component="span"
        >
          <Box
            component="strong"
            sx={{ color }}
          >
            {formattedDiff}
          </Box>
          {' '}
          <strong>
            {resourceName}
          </strong>
          {' '}
          <span>
            {t(labelKey)}
            {' '}
          </span>
          {widgetTimeRange !== ALL_TIME_TIME_RANGE && (
            <strong>
              {t('was previously', { previous_number: previous_interval_count })}
            </strong>
          )}
        </Typography>
      </Box>
    );
  };

  const fetchData = <T extends EsSeries[] | EsBase[] | EsAttackPath[] | EsCountInterval>(
    fetchFunction: (id: string, p: Record<string, string | undefined>) => Promise<{ data: T }>,
    setData: React.Dispatch<React.SetStateAction<T>>,
  ) => {
    const params: Record<string, string> = Object.fromEntries(
      Object.entries(customDashboardParameters).map(([key, val]) => [key, val.value]),
    );
    fetchFunction(widget.widget_id, params).then((response) => {
      if (response.data) {
        setData(response.data);
      }
    }).catch((error) => {
      setErrorMessage(error.message);
    }).finally(() => setLoading(false));
  };
  useEffect(() => {
    setLoading(true);
    switch (widget.widget_type) {
      case 'attack-path': {
        fetchData(fetchAttackPaths, setAttackPathsVizData);
        break;
      }
      case 'number': {
        fetchData(fetchCount, setNumberVizData);
        break;
      }
      case 'list':
        fetchData(fetchEntities, setEntitiesVizData);
        break;
      default:
        fetchData(fetchSeries, setSeriesVizData);
    }
  }, [widget, customDashboardParameters]);

  useEffect(() => {
    setTooltipMessage(createNumberTooltipContent());
  }, [numberVizData]);

  if (loading) {
    return <Loader variant="inElement" />;
  }

  const seriesData = seriesVizData.map(({ label, data }) => {
    if (data && data.length > 0) {
      return ({
        name: label,
        data: data.map(n => ({
          x: n.label,
          y: n.value,
          meta: n.key,
        })),
      });
    }
    return {
      name: label,
      data: [],
    };
  });

  switch (widget.widget_type) {
    case 'attack-path':
      return (
        <AttackPathContextLayer
          attackPathsData={attackPathsVizData}
          widgetId={widget.widget_id}
          widgetConfig={widget.widget_config as StructuralHistogramWidget}
        />
      );
    case 'security-coverage':
      return (
        <SecurityCoverage
          widgetId={widget.widget_id}
          widgetTitle={getWidgetTitle(widget.widget_config.title, widget.widget_type, t)}
          fullscreen={fullscreen}
          setFullscreen={setFullscreen}
          data={seriesVizData}
        />
      );
    case 'vertical-barchart':
      return (
        <VerticalBarChart
          widgetId={widget.widget_id}
          widgetConfig={widget.widget_config}
          series={seriesData}
          errorMessage={errorMessage}
        />
      );
    case 'horizontal-barchart':
      return (
        <HorizontalBarChart
          widgetId={widget.widget_id}
          widgetConfig={widget.widget_config}
          series={seriesData}
        />
      );
    case 'line':
      return <LineChart widgetId={widget.widget_id} series={seriesData} />;
    case 'donut': {
      // The seriesLimit is set to 1 for the donut.
      const data = seriesData[0].data;
      return (
        <DonutChart
          widgetId={widget.widget_id}
          widgetConfig={widget.widget_config}
          datas={data}
        />
      );
    }
    case 'list':
      return (<ListWidget elements={entitiesVizData} widgetConfig={widget.widget_config as ListConfiguration} />);
    case 'number':
      return (
        <NumberWidget
          widgetId={widget.widget_id}
          data={numberVizData}
        />
      );
    default:
      return 'Not implemented yet';
  }
};

export default memo(WidgetViz);
