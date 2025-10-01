import { InfoOutlined, OpenInFullOutlined } from '@mui/icons-material';
import { Box, darken, IconButton, Paper, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, type FunctionComponent, type SyntheticEvent, useContext, useEffect, useState } from 'react';
import RGL, { type Layout, WidthProvider } from 'react-grid-layout';

import { updateCustomDashboardWidgetLayout } from '../../../../actions/custom_dashboards/customdashboardwidget-action';
import { ErrorBoundary } from '../../../../components/Error';
import { useFormatter } from '../../../../components/i18n';
import { type Widget } from '../../../../utils/api-types-custom';
import { CustomDashboardContext } from './CustomDashboardContext';
import WidgetPopover from './widgets/WidgetPopover';
import { getWidgetTitle } from './widgets/WidgetUtils';
import WidgetViz from './widgets/WidgetViz';

const ReactGridLayout = WidthProvider(RGL);

const CustomDashboardReactLayout: FunctionComponent<{
  readOnly: boolean;
  style?: CSSProperties;
}> = ({ readOnly, style = {} }) => {
  // Standard hooks
  const theme = useTheme();
  const { t } = useFormatter();
  const [fullscreenWidgets, setFullscreenWidgets] = useState<Record<Widget['widget_id'], boolean | never>>({});
  const { customDashboard, setCustomDashboard } = useContext(CustomDashboardContext);
  const [tooltipMessage, setTooltipMessage] = useState<React.ReactNode>('');

  const [idToResize, setIdToResize] = useState<string | null>(null);
  const handleResize = (updatedWidget: string | null) => setIdToResize(updatedWidget);

  useEffect(() => {
    window.dispatchEvent(new Event('resize'));
  }, [customDashboard]);

  const handleWidgetUpdate = (widget: Widget) => {
    setCustomDashboard((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        custom_dashboard_widgets: (prev.custom_dashboard_widgets ?? []).map((w) => {
          if (w.widget_id === widget.widget_id) {
            return widget;
          } else {
            return w;
          }
        }),
      };
    });
  };
  const handleWidgetDelete = (widgetId: string) => {
    setCustomDashboard((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        custom_dashboard_widgets: (prev.custom_dashboard_widgets ?? []).filter(w => w.widget_id !== widgetId),
      };
    });
  };

  const onLayoutChange = async (layouts: Layout[]) => {
    if (!customDashboard) return;
    await Promise.all(
      layouts.map(layout =>
        updateCustomDashboardWidgetLayout(customDashboard.custom_dashboard_id, layout.i, {
          widget_layout_h: layout.h,
          widget_layout_w: layout.w,
          widget_layout_x: layout.x,
          widget_layout_y: layout.y,
        }),
      ),
    );
    setCustomDashboard(prev => prev && {
      ...prev,
      custom_dashboard_widgets: prev.custom_dashboard_widgets?.map((widget) => {
        const existingLayout = layouts.find(x => x.i === widget.widget_id)!;
        if (!existingLayout) return widget;
        return {
          ...widget,
          widget_layout: {
            widget_layout_x: existingLayout.x,
            widget_layout_y: existingLayout.y,
            widget_layout_w: existingLayout.w,
            widget_layout_h: existingLayout.h,
          },
        };
      }),
    });
  };

  return (
    <ReactGridLayout
      style={style}
      className="layout"
      margin={[0, 20]}
      rowHeight={50}
      cols={12}
      draggableCancel=".noDrag,.MuiAutocomplete-paper,.MuiModal-backdrop,.MuiPopover-paper,.MuiDialog-paper"
      isDraggable={!readOnly}
      isResizable={!readOnly}
      onResizeStart={(_, { i }) => handleResize(i)}
      onResizeStop={(layouts) => {
        handleResize(null);
        onLayoutChange(layouts);
      }}
      onDragStop={onLayoutChange}
    >
      {customDashboard?.custom_dashboard_widgets?.map((widget) => {
        const layout = {
          i: widget.widget_id,
          x: widget.widget_layout?.widget_layout_x,
          y: widget.widget_layout?.widget_layout_y,
          w: widget.widget_layout?.widget_layout_w,
          h: widget.widget_layout?.widget_layout_h,
        };
        const setFullscreen = (fullscreen: boolean) => setFullscreenWidgets({
          ...fullscreenWidgets,
          [widget.widget_id]: fullscreen,
        });
        // Make the theme.info.main color a bit darker
        const darkerInfoStyle = darken(theme.palette.info.main, 0.7);
        return (
          <Paper
            key={widget.widget_id}
            data-grid={layout}
            style={{
              borderRadius: 4,
              display: 'flex',
              flexDirection: 'column',
            }}
            variant="outlined"
          >
            <Box
              display="flex"
              flexDirection="row"
              alignItems="center"
            >
              <Box
                display="flex"
                flexDirection="row"
                alignItems="center"
                paddingTop={theme.spacing(2.5)}
              >
                <Typography
                  variant="h4"
                  sx={{
                    margin: 0,
                    paddingLeft: theme.spacing(2),
                    paddingRight: theme.spacing(1),
                    textTransform: 'uppercase',
                  }}
                >
                  {getWidgetTitle(widget.widget_config.title, widget.widget_type, t)}
                </Typography>
                {'number' === widget.widget_type && (
                  <Tooltip
                    title={tooltipMessage}
                    placement="right"
                    slotProps={{
                      tooltip: {
                        sx: {
                          bgcolor: darkerInfoStyle,
                          color: theme.palette.getContrastText(darkerInfoStyle),
                          boxShadow: theme.shadows[1],
                        },
                      },
                    }}
                  >
                    <InfoOutlined
                      fontSize="small"
                      color="primary"
                    />
                  </Tooltip>
                )}
              </Box>
              <Box
                display="flex"
                flexDirection="row"
                marginLeft="auto"
              >
                {widget.widget_type === 'security-coverage' && (
                  <IconButton
                    color="primary"
                    className="noDrag"
                    onClick={() => setFullscreen(true)}
                    size="small"
                  >
                    <OpenInFullOutlined fontSize="small" />
                  </IconButton>
                )}
                {!readOnly && (
                  <WidgetPopover
                    className="noDrag"
                    customDashboardId={customDashboard.custom_dashboard_id}
                    widget={widget}
                    onUpdate={widget => handleWidgetUpdate(widget)}
                    onDelete={widgetId => handleWidgetDelete(widgetId)}
                  />
                )}
              </Box>
            </Box>
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
                    fullscreen={fullscreenWidgets[widget.widget_id]}
                    setFullscreen={setFullscreen}
                    setTooltipMessage={setTooltipMessage}
                  />
                </Box>
              )}
            </ErrorBoundary>
          </Paper>
        );
      })}
    </ReactGridLayout>
  );
};

export default CustomDashboardReactLayout;
