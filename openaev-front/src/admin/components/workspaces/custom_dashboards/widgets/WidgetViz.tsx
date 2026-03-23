import { lazy, memo, Suspense } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import {
  type EsSeries,
  type ListConfiguration,
  type Pagination,
  type StructuralHistogramWidget,
  type Widget,
} from '../../../../../utils/api-types';
import type { EsAvgsExtended } from './viz/domains/SecurityDomainsWidgetUtils';
import { getWidgetTitle, type WidgetVizData, WidgetVizDataType } from './WidgetUtils';

const AttackPathContextLayer = lazy(() => import('./viz/attack_paths/AttackPathContextLayer'));
const SecurityDomainsWidget = lazy(() => import('./viz/domains/SecurityDomainsWidget'));
const DonutChart = lazy(() => import('./viz/DonutChart'));
const HorizontalBarChart = lazy(() => import('./viz/HorizontalBarChart'));
const LineChart = lazy(() => import('./viz/LineChart'));
const ListWidget = lazy(() => import('./viz/list/ListWidget'));
const NumberWidget = lazy(() => import('./viz/NumberWidget'));
const SecurityCoverage = lazy(() => import('./viz/SecurityCoverage'));
const VerticalBarChart = lazy(() => import('./viz/VerticalBarChart'));

interface WidgetTemporalVizProps {
  widget: Widget;
  fullscreen: boolean;
  setFullscreen: (fullscreen: boolean) => void;
  errorMessage: string;
  vizData: WidgetVizData;
  onPaginationChange: (paginationInput: Pagination) => void;
  contentLoading?: boolean;
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

const WidgetViz = ({ widget, fullscreen, setFullscreen, vizData, errorMessage, onPaginationChange, contentLoading = false }: WidgetTemporalVizProps) => {
  const { t } = useFormatter();

  const seriesData = vizData.type === WidgetVizDataType.SERIES
    ? computeSeriesData(vizData.data)
    : null;

  const widgetTitle = getWidgetTitle(widget.widget_config.title, widget.widget_type, t);

  const renderWidget = () => {
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
            widgetConfig={widget.widget_config as StructuralHistogramWidget}
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
            widgetConfig={widget.widget_config as StructuralHistogramWidget}
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
        return (
          <ListWidget
            elements={vizData.data.es_datas}
            currentPageNumber={vizData.data.page_number}
            elementsPerPage={vizData.data.page_size}
            totalElements={vizData.data.total}
            widgetConfig={widget.widget_config as ListConfiguration}
            onPaginationChange={onPaginationChange}
            contentLoading={contentLoading}
          />
        );
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
        return <SecurityDomainsWidget widgetId={widget.widget_id} data={vizData.data as EsAvgsExtended} />;
      default:
        return 'Not implemented yet';
    }
  };

  return (
    <Suspense fallback={<Loader variant="inElement" />}>
      {renderWidget()}
    </Suspense>
  );
};

export default memo(WidgetViz);
