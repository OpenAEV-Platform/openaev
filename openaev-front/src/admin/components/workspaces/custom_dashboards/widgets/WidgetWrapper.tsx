import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { memo, type SyntheticEvent, useContext, useEffect, useState } from 'react';

import { ErrorBoundary } from '../../../../../components/Error';
import Loader from '../../../../../components/Loader';
import {
  type EsAttackPath,
  type EsBase,
  type EsCountInterval,
  type EsSeries,
} from '../../../../../utils/api-types';
import { type Widget } from '../../../../../utils/api-types-custom';
import { CustomDashboardContext } from '../CustomDashboardContext';
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

const WidgetWrapper = ({ widget, fullscreen, setFullscreen, idToResize, handleWidgetUpdate, handleWidgetDelete, readOnly }: WidgetWrapperProps) => {
  const theme = useTheme();

  const [vizData, setVizData] = useState<WidgetVizData>({ type: WidgetVizDataType.NONE });
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string>('');

  const { customDashboardParameters, fetchCount, fetchSeries, fetchEntities, fetchAttackPaths } = useContext(CustomDashboardContext);

  const fetchData = <T extends EsSeries[] | EsBase[] | EsAttackPath[] | EsCountInterval>(
    fetchFunction: (id: string, p: Record<string, string | undefined>) => Promise<{ data: T }>,
    vizType: WidgetVizDataType.SERIES | WidgetVizDataType.ENTITIES | WidgetVizDataType.ATTACK_PATHS | WidgetVizDataType.NUMBER,
  ) => {
    const params: Record<string, string> = Object.fromEntries(
      Object.entries(customDashboardParameters).map(([key, val]) => [key, val.value]),
    );
    fetchFunction(widget.widget_id, params).then((response) => {
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
          default: break;
        }
      }
    }).catch((error) => {
      setErrorMessage(error.message);
    }).finally(() => setLoading(false));
  };

  useEffect(() => {
    setLoading(true);
    switch (widget.widget_type) {
      case 'attack-path': {
        fetchData(fetchAttackPaths, WidgetVizDataType.ATTACK_PATHS);
        break;
      }
      case 'number': {
        fetchData(fetchCount, WidgetVizDataType.NUMBER);
        break;
      }
      case 'list':
        fetchData(fetchEntities, WidgetVizDataType.ENTITIES);
        break;
      default:
        fetchData(fetchSeries, WidgetVizDataType.SERIES);
    }
  }, [widget, customDashboardParameters]);

  if (loading) {
    return <Loader variant="inElement" />;
  }

  return (
    <>
      <WidgetTitle
        widget={widget}
        setFullscreen={setFullscreen}
        handleWidgetUpdate={handleWidgetUpdate}
        handleWidgetDelete={handleWidgetDelete}
        readOnly={readOnly}
        vizData={vizData}
      />
      <ErrorBoundary>
        {widget.widget_id === idToResize ? (<div />) : (
          <Box
            flex={1}
            display="flex"
            flexDirection="column"
            minHeight={0}
            padding={theme.spacing(1, 2, 2)}
            overflow={'number' === widget.widget_type ? 'hidden' : 'auto'}
            onMouseDown={(e: SyntheticEvent) => e.stopPropagation()}
            onTouchStart={(e: SyntheticEvent) => e.stopPropagation()}
          >
            <WidgetViz
              widget={widget}
              fullscreen={fullscreen}
              setFullscreen={setFullscreen}
              vizData={vizData}
              errorMessage={errorMessage}
            />
          </Box>
        )}
      </ErrorBoundary>
    </>
  );
};

export default memo(WidgetWrapper);
