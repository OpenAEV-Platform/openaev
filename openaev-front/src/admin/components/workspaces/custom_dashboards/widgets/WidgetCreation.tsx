import { type FunctionComponent, useContext, useState } from 'react';

import { createCustomDashboardWidget } from '../../../../../actions/custom_dashboards/customdashboardwidget-action';
import ButtonCreate from '../../../../../components/common/ButtonCreate';
import { type Widget } from '../../../../../utils/api-types';
import { CustomDashboardContext } from '../CustomDashboardContext';
import WidgetForm from './configuration/WidgetForm';
import { type WidgetInputWithoutLayout } from './WidgetUtils';

const COL_WIDTH = 30;

const WidgetCreation: FunctionComponent = () => {
  const { customDashboard, setCustomDashboard } = useContext(CustomDashboardContext);
  const customDashboardId = customDashboard?.custom_dashboard_id ?? '';
  const widgets = customDashboard?.custom_dashboard_widgets ?? [];

  const onCreate = (newWidget: Widget) => {
    setCustomDashboard((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        custom_dashboard_widgets: [...(prev.custom_dashboard_widgets ?? []), newWidget],
      };
    });
  };

  // Dialog
  const [open, setOpen] = useState(false);
  const toggleDialog = () => setOpen(prev => !prev);

  // Layout
  const getMaxY = () => {
    return widgets.reduce(
      (max, n) => ((n.widget_layout?.widget_layout_y ?? 0) > max ? (n.widget_layout?.widget_layout_y ?? 0) : max),
      0,
    );
  };

  const getMaxX = () => {
    const y = getMaxY();
    const maxX = widgets
      .filter(n => (n.widget_layout?.widget_layout_y ?? 0) === y)
      .reduce((max, n) => ((n.widget_layout?.widget_layout_x ?? 0) > max ? (n.widget_layout?.widget_layout_x ?? 0) : max), 0);
    return maxX + 4;
  };

  const onSubmit = async (input: WidgetInputWithoutLayout) => {
    let maxX = getMaxX();
    let maxY = getMaxY();
    if (maxX >= COL_WIDTH - 4) {
      maxX = 0;
      maxY += 2;
    }
    let width = 10;
    let height = 10;
    if ('number' === input.widget_type) {
      width = 2;
      height = 2;
    }
    if ('average' === input.widget_type) {
      width = 12;
      height = 4;
    }
    const layout = {
      widget_layout_x: maxX,
      widget_layout_y: maxY,
      widget_layout_w: width,
      widget_layout_h: height,
    };
    const finalInput = {
      ...input,
      widget_layout: layout,
    };
    await createCustomDashboardWidget(customDashboardId, finalInput).then((result) => {
      onCreate(result.data);
    });
  };

  return (
    <>
      <ButtonCreate onClick={toggleDialog} />
      {
        open
        && (
          <WidgetForm
            open
            toggleDialog={toggleDialog}
            onSubmit={onSubmit}
          />
        )
      }
    </>
  );
};

export default WidgetCreation;
