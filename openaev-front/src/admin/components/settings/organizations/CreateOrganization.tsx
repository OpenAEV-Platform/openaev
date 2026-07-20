import { Add } from '@mui/icons-material';
import { Fab } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { addOrganization } from '../../../../actions/Organization';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { useAppDispatch } from '../../../../utils/hooks';
import { type Option } from '../../../../utils/Option';
import OrganizationForm, { type OrganizationInputForm } from './OrganizationForm';

// Settings > Security > Organizations creation. Separated from the
// business-side CreateOrganization (teams/organizations) on purpose.
const CreateOrganization: FunctionComponent = () => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const [open, setOpen] = useState(false);

  const onSubmit = (data: OrganizationInputForm) => {
    const inputValues = {
      ...data,
      organization_tags: (data.organization_tags ?? []).map((tag: Option) => tag.id),
    };
    return dispatch(addOrganization(inputValues)).then((result: { result: string }) => {
      if (result.result) {
        setOpen(false);
      }
      return result;
    });
  };

  return (
    <>
      <Fab
        onClick={() => setOpen(true)}
        color="primary"
        aria-label="Add"
        sx={{
          position: 'fixed',
          bottom: 30,
          right: 230,
        }}
      >
        <Add />
      </Fab>
      <Drawer
        open={open}
        handleClose={() => setOpen(false)}
        title={t('Create an organization')}
      >
        <OrganizationForm
          onSubmit={onSubmit}
          initialValues={{ organization_tags: [] }}
          handleClose={() => setOpen(false)}
        />
      </Drawer>
    </>
  );
};

export default CreateOrganization;
