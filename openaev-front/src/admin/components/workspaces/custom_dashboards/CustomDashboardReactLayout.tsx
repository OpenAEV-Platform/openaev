import { Paper } from '@mui/material';
import { type CSSProperties, type FunctionComponent, useContext, useEffect, useMemo, useState } from 'react';
import ReactGridLayout, { type Layout, type LayoutItem } from 'react-grid-layout';

import { updateCustomDashboardWidgetLayout } from '../../../../actions/custom_dashboards/customdashboardwidget-action';
import { type Widget, type WidgetLayout } from '../../../../utils/api-types';
import { CustomDashboardContext } from './CustomDashboardContext';
import WidgetWrapper from './widgets/WidgetWrapper';

const CustomDashboardReactLayout: FunctionComponent<{
  readOnly: boolean;
  style?: CSSProperties;
}> = ({ readOnly, style = {} }) => {
  const { customDashboard, setCustomDashboard, setGridReady } = useContext(CustomDashboardContext);

  // Track container width for responsive grid
  const [containerWidth, setContainerWidth] = useState(1200);

  // Hide grid until container has been measured (prevents initial layout animation)
  const [isReady, setIsReady] = useState(false);
  useEffect(() => {
    const timeout = setTimeout(() => {
      setIsReady(true);
      setGridReady(true); // Notify parent that grid is ready
    }, 150);
    return () => clearTimeout(timeout);
  }, [setGridReady]);

  // Measure container width on mount and resize
  useEffect(() => {
    const container = document.querySelector('.dashboard-container');
    if (!container) return;

    const updateWidth = () => {
      setContainerWidth(container.clientWidth);
    };

    const resizeObserver = new ResizeObserver(updateWidth);
    resizeObserver.observe(container);
    updateWidth(); // Initial measurement

    resizeObserver.disconnect();
  }, []);

  const [deleting, setDeleting] = useState(false);
  const [idToResize, setIdToResize] = useState<string | null>(null);
  const handleResize = (updatedWidget: string | null) => setIdToResize(updatedWidget);

  const [fullscreenWidgets, setFullscreenWidgets] = useState<Record<string, boolean>>({});

  // Map of widget layouts, refreshed when dashboard is updated (like OpenCTI).
  // We use a local map of layouts to avoid a lot of computation when only changing position
  // or dimension of widgets.
  const [widgetsLayouts, setWidgetsLayouts] = useState<Record<string, LayoutItem>>({});

  // Array of all widgets, refreshed when dashboard is updated (same pattern as OpenCTI).
  // Sync our local layouts immediately.
  const widgetsArray = useMemo(() => {
    const widgets = customDashboard?.custom_dashboard_widgets ?? [];
    setWidgetsLayouts(
      widgets.reduce<Record<string, LayoutItem>>((res, widget) => {
        if (widget.widget_layout) {
          res[widget.widget_id] = {
            i: widget.widget_id,
            x: widget.widget_layout.widget_layout_x,
            y: widget.widget_layout.widget_layout_y,
            w: widget.widget_layout.widget_layout_w,
            h: widget.widget_layout.widget_layout_h,
          };
        }
        return res;
      }, {}),
    );
    return widgets;
  }, [customDashboard?.custom_dashboard_widgets]);

  useEffect(() => {
    const timeout = setTimeout(() => {
      window.dispatchEvent(new Event('resize'));
    }, 1200);
    return () => {
      clearTimeout(timeout);
    };
  }, []);

  const handleWidgetUpdate = (widget: Widget) => {
    setCustomDashboard((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        custom_dashboard_widgets: (prev.custom_dashboard_widgets ?? []).map(w =>
          w.widget_id === widget.widget_id ? widget : w,
        ),
      };
    });
  };

  const handleWidgetDelete = (widgetId: string) => {
    setDeleting(true);
    setCustomDashboard((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        custom_dashboard_widgets: (prev.custom_dashboard_widgets ?? []).filter(w => w.widget_id !== widgetId),
      };
    });
  };

  const onSetFullscreen = (widgetId: string, fullscreen: boolean) => {
    setFullscreenWidgets(prev => ({
      ...prev,
      [widgetId]: fullscreen,
    }));
  };

  const onLayoutChange = (layouts: Layout) => {
    if (deleting || !customDashboard) {
      setDeleting(false);
      return;
    }

    // Build maps for quick lookup
    const newLayouts: Record<string, LayoutItem> = {};
    const layoutMap = new Map<string, WidgetLayout>();
    layouts.forEach((layout) => {
      newLayouts[layout.i] = layout;
      layoutMap.set(layout.i, {
        widget_layout_h: layout.h,
        widget_layout_w: layout.w,
        widget_layout_x: layout.x,
        widget_layout_y: layout.y,
      });
    });

    // Update local layouts state immediately (same pattern as OpenCTI)
    setWidgetsLayouts(newLayouts);

    // Filter to only layouts that actually changed
    const changedLayouts = layouts.filter((layout) => {
      const widget = customDashboard.custom_dashboard_widgets?.find(w => w.widget_id === layout.i);
      if (!widget?.widget_layout) return true;
      return (
        widget.widget_layout.widget_layout_x !== layout.x
        || widget.widget_layout.widget_layout_y !== layout.y
        || widget.widget_layout.widget_layout_w !== layout.w
        || widget.widget_layout.widget_layout_h !== layout.h
      );
    });

    // Only make API calls for changed layouts (don't update React state to avoid re-renders)
    if (changedLayouts.length > 0) {
      Promise.all(
        changedLayouts.map(layout =>
          updateCustomDashboardWidgetLayout(customDashboard.custom_dashboard_id, layout.i, layoutMap.get(layout.i)!),
        ),
      );
    }
  };

  const paperStyle = {
    height: '100%',
    margin: 0,
    borderRadius: 4,
    overflow: 'hidden',
  };

  // Compute layouts directly for data-grid prop to avoid timing issues
  const getWidgetLayout = (widget: Widget): LayoutItem => {
    // First check local state (for user-modified positions)
    if (widgetsLayouts[widget.widget_id]) {
      return widgetsLayouts[widget.widget_id];
    }
    // Fall back to widget data
    if (widget.widget_layout) {
      return {
        i: widget.widget_id,
        x: widget.widget_layout.widget_layout_x,
        y: widget.widget_layout.widget_layout_y,
        w: widget.widget_layout.widget_layout_w,
        h: widget.widget_layout.widget_layout_h,
      };
    }
    // Default layout
    return {
      i: widget.widget_id,
      x: 0,
      y: 0,
      w: 4,
      h: 2,
    };
  };

  return (
    <div
      className="dashboard-container"
      style={{
        ...style,
        width: '100%',
        visibility: isReady ? 'visible' : 'hidden',
      }}
    >
      <ReactGridLayout
        className="layout"
        width={containerWidth}
        gridConfig={{
          margin: [20, 20],
          containerPadding: [0, 0],
          rowHeight: 50,
          cols: 12,
        }}
        dragConfig={{
          cancel: '.noDrag,.MuiAutocomplete-paper,.MuiModal-backdrop,.MuiPopover-paper,.MuiDialog-paper',
          enabled: !readOnly,
        }}
        resizeConfig={{ enabled: !readOnly }}
        onLayoutChange={!readOnly ? onLayoutChange : undefined}
        onResizeStart={!readOnly ? (_layout, _oldItem, newItem) => handleResize(newItem?.i ?? null) : undefined}
        onResizeStop={!readOnly ? () => handleResize(null) : undefined}
      >
        {widgetsArray.map((widget) => {
          const layout = getWidgetLayout(widget);
          return (
            <Paper
              key={widget.widget_id}
              data-grid={layout}
              style={paperStyle}
              variant="outlined"
            >
              <WidgetWrapper
                widget={widget}
                fullscreen={fullscreenWidgets[widget.widget_id] ?? false}
                setFullscreen={(fs: boolean) => onSetFullscreen(widget.widget_id, fs)}
                handleWidgetUpdate={handleWidgetUpdate}
                handleWidgetDelete={handleWidgetDelete}
                readOnly={readOnly}
                idToResize={idToResize}
              />
            </Paper>
          );
        })}
      </ReactGridLayout>
    </div>
  );
};

export default CustomDashboardReactLayout;
