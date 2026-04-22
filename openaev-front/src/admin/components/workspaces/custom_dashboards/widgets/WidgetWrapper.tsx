import { useTheme } from '@mui/material/styles';
import { type SyntheticEvent, useCallback, useContext, useEffect, useRef, useState } from 'react';

import usePaginationState from '../../../../../components/common/queryable/pagination/usePaginationState';
import ErrorBoundary from '../../../../../components/ErrorBoundary';
import Loader from '../../../../../components/Loader';
import {
  type EsAttackPath,
  type EsAvgs,
  type EsCountInterval, type EsEntities,
  type EsSeries, type Pagination,
  type Widget,
} from '../../../../../utils/api-types';
import { CustomDashboardContext, type ParameterOption } from '../CustomDashboardContext';
import { determinePercentage } from './viz/domains/SecurityDomainsWidgetUtils';
import WidgetTitle from './WidgetTitle';
import { type WidgetVizData, WidgetVizDataType } from './WidgetUtils';
import WidgetViz from './WidgetViz';

interface WidgetWrapperProps {
  widget: Widget;
  fullscreen: boolean;
  setFullscreen: (fullscreen: boolean) => void;
  idToResize: string | null;
  handleWidgetUpdate: (widget: Widget) => void;
  handleWidgetDelete: (widgetId: string) => void;
  readOnly: boolean;
}

// Helper to convert parameters to request params
const buildParams = (parameters: Record<string, ParameterOption>): Record<string, string> => {
  return Object.fromEntries(
    Object.entries(parameters).map(([key, val]) => [key, val.value]),
  );
};

type WidgetDataResponse = EsAttackPath[] | EsCountInterval | EsAvgs | EsEntities | EsSeries[];
type WidgetFetchConfig = {
  vizType: WidgetVizDataType;
  fetchFn: (id: string, params: Record<string, string | undefined>, pagination?: Pagination) => Promise<{ data: WidgetDataResponse }>;
  transformData?: (data: WidgetDataResponse) => unknown;
};

const WidgetWrapper = ({
  widget,
  fullscreen,
  setFullscreen,
  idToResize,
  handleWidgetUpdate,
  handleWidgetDelete,
  readOnly,
}: WidgetWrapperProps) => {
  const theme = useTheme();
  const [vizData, setVizData] = useState<WidgetVizData>({ type: WidgetVizDataType.NONE });
  const [initialLoading, setInitialLoading] = useState(true); // full widget loader
  const [contentLoading, setContentLoading] = useState(false);

  const [errorMessage, setErrorMessage] = useState<string>('');
  const { customDashboardParameters, fetchCount, fetchSeries, fetchEntities, fetchAttackPaths, fetchAverage } = useContext(CustomDashboardContext);
  const { elementsPerPage, page, handleChangePagination } = widget.widget_type === 'list'
    ? usePaginationState(100, undefined, `widget-list-${widget.widget_id}`)
    : {
        elementsPerPage: 0,
        page: 0,
        handleChangePagination: () => {},
      };

  const WIDGET_CONFIG: Record<string, WidgetFetchConfig> = {
    'attack-path': {
      vizType: WidgetVizDataType.ATTACK_PATHS,
      fetchFn: fetchAttackPaths,
    },
    'number': {
      vizType: WidgetVizDataType.NUMBER,
      fetchFn: fetchCount,
    },
    'average': {
      vizType: WidgetVizDataType.AVERAGE,
      fetchFn: fetchAverage,
      transformData: data => determinePercentage(data as EsAvgs, theme),
    },
    'list': {
      vizType: WidgetVizDataType.ENTITIES,
      fetchFn: fetchEntities,
    },
  };

  const DEFAULT_CONFIG: WidgetFetchConfig = {
    vizType: WidgetVizDataType.SERIES,
    fetchFn: fetchSeries,
  };

  // Use ref to track if component is mounted
  const isMountedRef = useRef(true);
  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  const fetchWidgetData = useCallback(
    async (pagination: Pagination) => {
      setErrorMessage('');

      const params = buildParams(customDashboardParameters);
      const config = WIDGET_CONFIG[widget.widget_type] ?? DEFAULT_CONFIG;

      config.fetchFn(widget.widget_id, params, pagination).then((response) => {
        if (response.data) {
          setVizData({
            type: config.vizType,
            data: config.transformData
              ? config.transformData(response.data)
              : response.data,
          } as WidgetVizData);
        }
      }).catch((error) => {
        if (!isMountedRef.current) return;
        setErrorMessage(error.message);
      });
    },
    [widget.widget_id, widget.widget_type, widget.widget_config, customDashboardParameters],
  );

  useEffect(() => {
    if (!isMountedRef.current) return;
    setInitialLoading(true);
    fetchWidgetData({
      page,
      size: elementsPerPage,
    }).then(() => {
      if (isMountedRef.current) {
        setInitialLoading(false);
      }
    });
  }, [fetchWidgetData]);

  const handleMouseDown = (e: SyntheticEvent) => e.stopPropagation();
  const handleTouchStart = (e: SyntheticEvent) => e.stopPropagation();

  const isResizing = widget.widget_id === idToResize;

  const onPaginationChange = (pagination: Pagination) => {
    setContentLoading(true);
    handleChangePagination(pagination);
    fetchWidgetData(pagination).then(() => setContentLoading(false));
  };

  return (
    <div style={{
      height: '100%',
      padding: theme.spacing(1.5),
    }}
    >
      <WidgetTitle
        widget={widget}
        setFullscreen={setFullscreen}
        handleWidgetUpdate={handleWidgetUpdate}
        handleWidgetDelete={handleWidgetDelete}
        readOnly={readOnly}
        vizData={vizData}
      />
      <ErrorBoundary>
        {isResizing ? (<div />) : (
          <div
            style={{ height: 'calc(100% - 32px)' }}
            onMouseDown={handleMouseDown}
            onTouchStart={handleTouchStart}
          >
            {initialLoading ? (
              <Loader variant="inElement" />
            ) : (
              <WidgetViz
                widget={widget}
                fullscreen={fullscreen}
                setFullscreen={setFullscreen}
                vizData={vizData}
                errorMessage={errorMessage}
                onPaginationChange={onPaginationChange}
                contentLoading={contentLoading}
              />
            )}
          </div>
        )}
      </ErrorBoundary>
    </div>
  );
};

export default WidgetWrapper;
