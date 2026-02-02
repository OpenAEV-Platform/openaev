import { memo } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { type EsSeries, type ListConfiguration, type StructuralHistogramWidget, type Widget } from '../../../../../utils/api-types';
import AttackPathContextLayer from './viz/attack_paths/AttackPathContextLayer';
import SecurityDomainsWidget from './viz/domains/SecurityDomainsWidget';
import DonutChart from './viz/DonutChart';
import HorizontalBarChart from './viz/HorizontalBarChart';
import LineChart from './viz/LineChart';
import ListWidget from './viz/list/ListWidget';
import NumberWidget from './viz/NumberWidget';
import SecurityCoverage from './viz/SecurityCoverage';
import VerticalBarChart from './viz/VerticalBarChart';
import { getWidgetTitle, type WidgetVizData, WidgetVizDataType } from './WidgetUtils';

interface WidgetTemporalVizProps {
  widget: Widget;
  fullscreen: boolean;
  setFullscreen: (fullscreen: boolean) => void;
  errorMessage: string;
  vizData: WidgetVizData;
}

export type SerieData = {
  x?: string;
  y?: string;
  meta?: string;
};

const computeSeriesData = (esSeries: EsSeries[]) => {
  return esSeries.map(({ label, data }) => {
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
};

const WidgetViz = ({ widget, fullscreen, setFullscreen, vizData, errorMessage }: WidgetTemporalVizProps) => {
  const { t } = useFormatter();

  const seriesData = vizData.type === WidgetVizDataType.SERIES
    ? computeSeriesData(vizData.data)
    : null;

  const widgetTitle = getWidgetTitle(widget.widget_config.title, widget.widget_type, t);

  switch (widget.widget_type) {
    case 'attack-path':
      if (vizData.type !== WidgetVizDataType.ATTACK_PATHS) {
        return 'Not implemented yet';
      }
      return (
        <AttackPathContextLayer
          attackPathsData={vizData.data}
          widgetId={widget.widget_id}
          widgetConfig={widget.widget_config as StructuralHistogramWidget}
        />
      );
    case 'security-coverage':
      if (vizData.type !== WidgetVizDataType.SERIES) {
        return 'Not implemented yet';
      }
      return (
        <SecurityCoverage
          widgetId={widget.widget_id}
          widgetTitle={widgetTitle}
          fullscreen={fullscreen}
          setFullscreen={setFullscreen}
          data={vizData.data}
        />
      );
    case 'vertical-barchart':
      if (vizData.type !== WidgetVizDataType.SERIES || !seriesData) {
        return 'Not implemented yet';
      }
      return (
        <VerticalBarChart
          widgetId={widget.widget_id}
          widgetConfig={widget.widget_config}
          series={seriesData}
          errorMessage={errorMessage}
        />
      );
    case 'horizontal-barchart':
      if (vizData.type !== WidgetVizDataType.SERIES || !seriesData) {
        return 'Not implemented yet';
      }
      return (
        <HorizontalBarChart
          widgetId={widget.widget_id}
          widgetConfig={widget.widget_config}
          series={seriesData}
        />
      );
    case 'line':
      if (vizData.type !== WidgetVizDataType.SERIES || !seriesData) {
        return 'Not implemented yet';
      }
      return <LineChart widgetId={widget.widget_id} series={seriesData} />;
    case 'donut': {
      if (vizData.type !== WidgetVizDataType.SERIES || !seriesData) {
        return 'Not implemented yet';
      }
      // The seriesLimit is set to 1 for the donut.
      const data = seriesData[0].data;
      return (
        <DonutChart
          widgetId={widget.widget_id}
          widgetConfig={widget.widget_config as StructuralHistogramWidget}
          datas={data}
        />
      );
    }
    case 'list':
      if (vizData.type !== WidgetVizDataType.ENTITIES) {
        return 'Not implemented yet';
      }
      return (<ListWidget elements={vizData.data} widgetConfig={widget.widget_config as ListConfiguration} />);
    case 'number':
      if (vizData.type !== WidgetVizDataType.NUMBER) {
        return 'Not implemented yet';
      }
      return (
        <NumberWidget
          widgetId={widget.widget_id}
          data={vizData.data}
        />
      );
    case 'average':
      if (vizData.type !== WidgetVizDataType.AVERAGE) {
        return 'Not implemented yet';
      }
      return (<SecurityDomainsWidget data={vizData.data} />);
    default:
      return 'Not implemented yet';
  }
};

export default memo(WidgetViz);
