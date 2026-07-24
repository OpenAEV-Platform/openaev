import { TablePagination } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type SyntheticEvent, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';

import usePaginationState, { ROWS_PER_PAGE_OPTIONS } from '../../../../../components/common/queryable/pagination/usePaginationState';
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
  setFullscreen: (widgetId: string, fullscreen: boolean) => void;
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
  // A dashboard tile is small and its pagination lives in the title row, so it
  // paginates at a tile-friendly page size (loading 100 rows into a tile never
  // fit and hid the pagination whenever the total was below 100). The hook is
  // called unconditionally (Rules of Hooks); only list widgets use its state.
  const isListWidget = widget.widget_type === 'list';
  const { elementsPerPage, page, handleChangePagination } = usePaginationState(
    ROWS_PER_PAGE_OPTIONS[0],
    undefined,
    isListWidget ? `widget-list-${widget.widget_id}` : undefined,
  );

  // The 'average' transform colors its output from the CURRENT theme, read through a
  // ref: widgetConfig feeds fetchWidgetData, so keying it on the theme object would
  // refetch every widget over the network whenever the theme instance is rebuilt (a
  // pure styling change). The ref is always up to date by the time the transform runs
  // (fetch callbacks resolve after commit).
  const themeRef = useRef(theme);
  useEffect(() => {
    themeRef.current = theme;
  });
  const widgetConfig = useMemo<Record<string, WidgetFetchConfig>>(() => ({
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
      transformData: data => determinePercentage(data as EsAvgs, themeRef.current),
    },
    'list': {
      vizType: WidgetVizDataType.ENTITIES,
      fetchFn: fetchEntities,
    },
  }), [fetchAttackPaths, fetchCount, fetchAverage, fetchEntities]);

  const defaultConfig = useMemo<WidgetFetchConfig>(() => ({
    vizType: WidgetVizDataType.SERIES,
    fetchFn: fetchSeries,
  }), [fetchSeries]);

  // Use ref to track if component is mounted
  const isMountedRef = useRef(true);
  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  // Monotonic request id: with fetches funneled through the dashboard's
  // concurrency limiter, a slow stale response must never overwrite the
  // result of a newer request (rapid pagination clicks, refresh races).
  const requestIdRef = useRef(0);

  /**
   * Fetches the widget data. Resolves to true only when this call (or the
   * clamped retry it delegated to) is still the LATEST request: callers gate
   * their loading-state resets on it, so a superseded request can neither
   * overwrite fresh data nor clear a spinner owned by a newer request.
   */
  const fetchWidgetData = useCallback(
    async (pagination: Pagination): Promise<boolean> => {
      setErrorMessage('');

      const params = buildParams(customDashboardParameters);
      const config = widgetConfig[widget.widget_type] ?? defaultConfig;
      requestIdRef.current += 1;
      const requestId = requestIdRef.current;

      try {
        const response = await config.fetchFn(widget.widget_id, params, pagination);
        if (!isMountedRef.current || requestId !== requestIdRef.current) return false;
        if (response.data) {
          setVizData({
            type: config.vizType,
            data: config.transformData
              ? config.transformData(response.data)
              : response.data,
          } as WidgetVizData);
          // A page persisted in localStorage can point past the end after the
          // data shrinks (e.g. a narrower time range). The pagination control
          // hides itself in that case, so clamp back to the first page.
          const entities = response.data as EsEntities;
          if (config.vizType === WidgetVizDataType.ENTITIES
            && pagination.page > 0
            && (entities.total ?? 0) <= pagination.page * pagination.size) {
            const firstPage = {
              page: 0,
              size: pagination.size,
            };
            handleChangePagination(firstPage);
            return fetchWidgetData(firstPage);
          }
        }
        return true;
      } catch (error) {
        if (!isMountedRef.current || requestId !== requestIdRef.current) return false;
        setErrorMessage((error as Error).message);
        return true;
      }
    },
    [widget.widget_id, widget.widget_type, widget.widget_config, customDashboardParameters, widgetConfig, defaultConfig, handleChangePagination],
  );

  useEffect(() => {
    if (!isMountedRef.current) return;
    setInitialLoading(true);
    fetchWidgetData({
      page,
      size: elementsPerPage,
    }).then((latest) => {
      if (latest && isMountedRef.current) {
        setInitialLoading(false);
        // A refetch (refresh counter, parameter change) supersedes any
        // pagination request still in flight; clear its spinner too.
        setContentLoading(false);
      }
    });
  }, [fetchWidgetData]);

  const handleSetFullscreen = useCallback(
    (fs: boolean) => setFullscreen(widget.widget_id, fs),
    [setFullscreen, widget.widget_id],
  );

  const handleMouseDown = (e: SyntheticEvent) => e.stopPropagation();
  const handleTouchStart = (e: SyntheticEvent) => e.stopPropagation();

  const isResizing = widget.widget_id === idToResize;

  const onPaginationChange = (pagination: Pagination) => {
    setContentLoading(true);
    handleChangePagination(pagination);
    // Only the latest request may clear the spinner: an older, superseded
    // pagination request resolving late must not hide it while a newer
    // request is still in flight.
    fetchWidgetData(pagination).then((latest) => {
      if (latest && isMountedRef.current) {
        setContentLoading(false);
      }
    });
  };

  // List pagination lives in the title row (top right) instead of a dedicated
  // bar below the list, so it costs no vertical space inside the tile.
  const listPagination = widget.widget_type === 'list'
    && vizData.type === WidgetVizDataType.ENTITIES
    && vizData.data.total > vizData.data.page_size
    ? (
        <TablePagination
          component="div"
          className="noDrag"
          rowsPerPageOptions={ROWS_PER_PAGE_OPTIONS}
          count={vizData.data.total}
          page={vizData.data.page_number}
          rowsPerPage={vizData.data.page_size}
          onPageChange={(_, newPage) => onPaginationChange({
            page: newPage,
            size: vizData.data.page_size,
          })}
          onRowsPerPageChange={event => onPaginationChange({
            page: 0,
            size: parseInt(event.target.value, 10),
          })}
          sx={{
            'overflow': 'hidden',
            'flexShrink': 0,
            '& .MuiTablePagination-toolbar': {
              minHeight: 22,
              paddingLeft: 0,
            },
            '& .MuiTablePagination-selectLabel, & .MuiTablePagination-displayedRows': {
              fontSize: 12,
              margin: 0,
            },
            '& .MuiTablePagination-select': { fontSize: 12 },
            '& .MuiTablePagination-actions .MuiIconButton-root': { padding: 0.25 },
          }}
        />
      )
    : undefined;

  return (
    <div style={{
      height: '100%',
      padding: theme.spacing(1.5),
    }}
    >
      <WidgetTitle
        widget={widget}
        setFullscreen={handleSetFullscreen}
        handleWidgetUpdate={handleWidgetUpdate}
        handleWidgetDelete={handleWidgetDelete}
        readOnly={readOnly}
        vizData={vizData}
        rightSlot={listPagination}
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
                setFullscreen={handleSetFullscreen}
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
