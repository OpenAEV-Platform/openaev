import { type FunctionComponent, useCallback, useState } from 'react';

import { createCustomDashboard } from '../../../../actions/custom_dashboards/customdashboard-action';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import type { CustomDashboard, CustomDashboardInput } from '../../../../utils/api-types';
import CustomDashboardForm from './CustomDashboardForm';

interface Props { onCreate: (result: CustomDashboard) => void }

const CustomDashboardCreation: FunctionComponent<Props> = ({ onCreate }) => {
  // Standard hooks
  const { t } = useFormatter();

  // Drawer
  const [open, setOpen] = useState(false);
  const toggleDrawer = () => setOpen(prev => !prev);

  // Form
  const onSubmit = useCallback(
    async (data: CustomDashboardInput) => {
      try {
        const response = await createCustomDashboard(data);
        if (response.data) {
          onCreate(response.data);
        }
      } finally {
        setOpen(false);
      }
    },
    [onCreate],
  );

  return (
    <>
      <ButtonCreate onClick={toggleDrawer} />
      <Drawer
        open={open}
        handleClose={toggleDrawer}
        title={t('Create a custom dashboard')}
      >
        <CustomDashboardForm onSubmit={onSubmit} handleClose={toggleDrawer} />
      </Drawer>
    </>
  );
};

export default CustomDashboardCreation;
