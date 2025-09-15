import { type FunctionComponent, useCallback, useContext, useState } from 'react';

import { deleteCustomDashboard, exportCustomDashboard, updateCustomDashboard } from '../../../../actions/custom_dashboards/customdashboard-action';
import type { LoggedHelper } from '../../../../actions/helper';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type CustomDashboard, type CustomDashboardInput, type PlatformSettings } from '../../../../utils/api-types';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { download } from '../../../../utils/utils';
import CustomDashboardForm from './CustomDashboardForm';

interface Props {
  customDashboard: CustomDashboard;
  onUpdate?: (result: CustomDashboard) => void;
  onDelete?: (result: string) => void;
  inList?: boolean;
}

const CustomDashboardPopover: FunctionComponent<Props> = ({ customDashboard, onUpdate, onDelete, inList = false }) => {
  // Standard hooks
  const { t } = useFormatter();
  const ability = useContext(AbilityContext);

  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));

  const initialValues = {
    custom_dashboard_name: customDashboard.custom_dashboard_name,
    custom_dashboard_description: customDashboard.custom_dashboard_description ?? '',
    custom_dashboard_parameters: customDashboard.custom_dashboard_parameters ?? [],
  };

  const [modal, setModal] = useState<'edit' | 'delete' | null>(null);
  const toggleModal = (type: 'edit' | 'delete' | null) => setModal(type);

  const onSubmitEdit = useCallback(
    async (data: CustomDashboardInput) => {
      try {
        const response = await updateCustomDashboard(customDashboard.custom_dashboard_id, data);
        if (response.data) {
          onUpdate?.(response.data);
        }
      } finally {
        toggleModal(null);
      }
    },
    [customDashboard.custom_dashboard_id, onUpdate],
  );

  const submitExport = async () => {
    const { data } = await exportCustomDashboard(customDashboard.custom_dashboard_id);
    download(data, `dashboard-${customDashboard.custom_dashboard_name}.zip`, 'application/zip');
  };

  const submitDelete = useCallback(async () => {
    try {
      await deleteCustomDashboard(customDashboard.custom_dashboard_id);
      onDelete?.(customDashboard.custom_dashboard_id);
    } finally {
      toggleModal(null);
    }
  }, [customDashboard.custom_dashboard_id, onDelete]);

  const entries = [
    {
      label: t('Update'),
      action: () => toggleModal('edit'),
      userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.DASHBOARDS),
    },
    {
      label: t('Export'),
      action: () => submitExport(),
      userRight: ability.can(ACTIONS.ACCESS, SUBJECTS.DASHBOARDS),
    },
    {
      label: t('Delete'),
      action: () => toggleModal('delete'),
      userRight: ability.can(ACTIONS.DELETE, SUBJECTS.DASHBOARDS) && settings.platform_home_dashboard !== customDashboard.custom_dashboard_id,
    },
  ];

  return (
    <>
      <ButtonPopover entries={entries} variant={inList ? 'icon' : 'toggle'} />
      <Drawer
        open={modal === 'edit'}
        handleClose={() => toggleModal(null)}
        title={t('Update the custom dashboard')}
      >
        <CustomDashboardForm
          onSubmit={onSubmitEdit}
          handleClose={() => toggleModal(null)}
          initialValues={initialValues}
          editing
          customDashboardId={customDashboard.custom_dashboard_id}
        />
      </Drawer>
      <DialogDelete
        open={modal === 'delete'}
        handleClose={() => toggleModal(null)}
        handleSubmit={submitDelete}
        text={t('Do you want to delete this custom dashboard?')}
      />
    </>
  );
};

export default CustomDashboardPopover;
