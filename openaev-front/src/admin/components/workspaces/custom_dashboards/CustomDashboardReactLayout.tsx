import { Paper } from '@mui/material';
import { type CSSProperties, type FunctionComponent, useContext, useEffect, useState } from 'react';
import RGL, { type Layout, WidthProvider } from 'react-grid-layout';

import { updateCustomDashboardWidgetLayout } from '../../../../actions/custom_dashboards/customdashboardwidget-action';
import { type Widget } from '../../../../utils/api-types-custom';
import { CustomDashboardContext } from './CustomDashboardContext';
import WidgetWrapper from './widgets/WidgetWrapper';

const ReactGridLayout = WidthProvider(RGL);

const CustomDashboardReactLayout: FunctionComponent<{
  readOnly: boolean;
  style?: CSSProperties;
}> = ({ readOnly, style = {} }) => {
  const [fullscreenWidgets, setFullscreenWidgets] = useState<Record<Widget['widget_id'], boolean | never>>({});
  const { customDashboard, setCustomDashboard } = useContext(CustomDashboardContext);

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
      margin={[20, 20]}
      containerPadding={[0, 0]}
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
            <WidgetWrapper
              widget={widget}
              fullscreen={fullscreenWidgets[widget.widget_id]}
              setFullscreen={setFullscreen}
              handleWidgetUpdate={handleWidgetUpdate}
              handleWidgetDelete={handleWidgetDelete}
              readOnly={readOnly}
              idToResize={idToResize}
            >
            </WidgetWrapper>
          </Paper>
        );
      })}
    </ReactGridLayout>
  );
};

export default CustomDashboardReactLayout;
