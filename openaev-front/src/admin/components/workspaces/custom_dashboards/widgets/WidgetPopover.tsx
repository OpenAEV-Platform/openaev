import { type FunctionComponent, useContext, useState } from 'react';

import { deleteCustomDashboardWidget, updateCustomDashboardWidget } from '../../../../../actions/custom_dashboards/customdashboardwidget-action';
import ButtonPopover, { type PopoverEntry } from '../../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../../components/common/DialogDelete';
import { useFormatter } from '../../../../../components/i18n';
import { type Widget } from '../../../../../utils/api-types';
import { AbilityContext } from '../../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import WidgetForm from './configuration/WidgetForm';
import { type WidgetInputWithoutLayout } from './WidgetUtils';

interface Props {
  customDashboardId: string;
  widget: Widget;
  className: string;
  onUpdate: (widget: Widget) => void;
  onDelete: (widgetId: string) => void;
}

const WidgetPopover: FunctionComponent<Props> = ({
  customDashboardId,
  widget,
  className,
  onUpdate,
  onDelete,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const ability = useContext(AbilityContext);

  // Edition
  const [openEdit, setOpenEdit] = useState(false);
  const toggleDialog = () => setOpenEdit(prev => !prev);
  const initialValues = {
    widget_type: widget.widget_type,
    widget_config: widget.widget_config,
  };
  const onSubmit = async (input: WidgetInputWithoutLayout) => {
    const finalInput = {
      ...input,
      widget_layout: widget.widget_layout,
    };
    await updateCustomDashboardWidget(customDashboardId, widget.widget_id, finalInput).then((result) => {
      onUpdate(result.data);
    });
  };

  // Deletion
  const [openDelete, setOpenDelete] = useState(false);
  const handleOpenDelete = () => setOpenDelete(true);
  const handleCloseDelete = () => setOpenDelete(false);
  const submitDelete = () => {
    deleteCustomDashboardWidget(customDashboardId, widget.widget_id);
    if (onDelete) {
      onDelete(widget.widget_id);
    }
  };

  const entries: PopoverEntry[] = [
    {
      label: 'Update',
      action: toggleDialog,
      userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.DASHBOARDS),
    },
    {
      label: 'Delete',
      action: handleOpenDelete,
      userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.DASHBOARDS),
    },
  ];

  return (
    <div className={className}>
      <ButtonPopover entries={entries} variant="icon" size="small" />
      <WidgetForm
        open={openEdit}
        toggleDialog={toggleDialog}
        initialValues={initialValues}
        onSubmit={onSubmit}
        editing
      />
      <DialogDelete
        open={openDelete}
        handleClose={handleCloseDelete}
        handleSubmit={submitDelete}
        text={t('Do you want to delete this widget?')}
      />
    </div>
  );
};

export default WidgetPopover;
