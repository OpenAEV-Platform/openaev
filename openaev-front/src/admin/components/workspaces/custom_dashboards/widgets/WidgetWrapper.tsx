import { useTheme } from '@mui/material/styles';
import { type SyntheticEvent, useContext, useEffect, useRef, useState } from 'react';

import { ErrorBoundary } from '../../../../../components/Error';
import Loader from '../../../../../components/Loader';
import {
  type EsAttackPath,
  type EsAvgs,
  type EsBase,
  type EsCountInterval,
  type EsSeries,
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
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string>('');
  const { customDashboardParameters, fetchCount, fetchSeries, fetchEntities, fetchAttackPaths, fetchAverage } = useContext(CustomDashboardContext);

  // Use ref to track if component is mounted
  const isMountedRef = useRef(true);
  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    setLoading(true);
    setErrorMessage('');

    const params = buildParams(customDashboardParameters);
    type WidgetDataResponse = EsAttackPath[] | EsCountInterval | EsAvgs | EsBase[] | EsSeries[];
    let fetchFunction: (id: string, p: Record<string, string | undefined>) => Promise<{ data: WidgetDataResponse }>;
    let vizType: WidgetVizDataType;

    switch (widget.widget_type) {
      case 'attack-path':
        fetchFunction = fetchAttackPaths;
        vizType = WidgetVizDataType.ATTACK_PATHS;
        break;
      case 'number':
        fetchFunction = fetchCount;
        vizType = WidgetVizDataType.NUMBER;
        break;
      case 'average': {
        fetchFunction = fetchAverage;
        vizType = WidgetVizDataType.AVERAGE;
        break;
      }
      case 'list':
        fetchFunction = fetchEntities;
        vizType = WidgetVizDataType.ENTITIES;
        break;
      default:
        fetchFunction = fetchSeries;
        vizType = WidgetVizDataType.SERIES;
    }

    fetchFunction(widget.widget_id, params)
      .then((response) => {
        if (!isMountedRef.current) return;
        if (response.data) {
          switch (vizType) {
            case WidgetVizDataType.SERIES:
              setVizData({
                type: WidgetVizDataType.SERIES,
                data: response.data as EsSeries[],
              });
              break;
            case WidgetVizDataType.ENTITIES:
              setVizData({
                type: WidgetVizDataType.ENTITIES,
                data: response.data as EsBase[],
              });
              break;
            case WidgetVizDataType.ATTACK_PATHS:
              setVizData({
                type: WidgetVizDataType.ATTACK_PATHS,
                data: response.data as EsAttackPath[],
              });
              break;
            case WidgetVizDataType.NUMBER:
              setVizData({
                type: WidgetVizDataType.NUMBER,
                data: response.data as EsCountInterval,
              });
              break;
            case WidgetVizDataType.AVERAGE:
              setVizData({
                type: WidgetVizDataType.AVERAGE,
                data: determinePercentage(response.data as EsAvgs, theme),
              });
              break;
            default: break;
          }
        }
      })
      .catch((error) => {
        if (!isMountedRef.current) return;
        setErrorMessage(error.message);
      })
      .finally(() => {
        if (isMountedRef.current) {
          setLoading(false);
        }
      });
  }, [widget.widget_id, widget.widget_type, widget.widget_config, customDashboardParameters, fetchAttackPaths, fetchCount, fetchEntities, fetchSeries]);

  const handleMouseDown = (e: SyntheticEvent) => e.stopPropagation();
  const handleTouchStart = (e: SyntheticEvent) => e.stopPropagation();

  const isResizing = widget.widget_id === idToResize;

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
            {loading ? (
              <Loader variant="inElement" />
            ) : (
              <WidgetViz
                widget={widget}
                fullscreen={fullscreen}
                setFullscreen={setFullscreen}
                vizData={vizData}
                errorMessage={errorMessage}
              />
            )}
          </div>
        )}
      </ErrorBoundary>
    </div>
  );
};

export default WidgetWrapper;
