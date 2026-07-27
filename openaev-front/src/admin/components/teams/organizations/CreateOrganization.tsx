import { type FunctionComponent, useState } from 'react';

import { addOrganization } from '../../../../actions/Organization';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type Organization } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { type Option } from '../../../../utils/Option';
import OrganizationForm from './OrganizationForm';

// Kept local: the business-side form (this folder) is deliberately decoupled
// from the admin settings one, so their input shapes can diverge.
interface OrganizationInputForm {
  organization_name?: string;
  organization_description?: string;
  organization_tags?: Option[];
}

interface Props { onCreate?: (result: Organization) => void }

const CreateOrganization: FunctionComponent<Props> = ({ onCreate }) => {
  const dispatch = useAppDispatch();
  const { t } = useFormatter();
  const [open, setOpen] = useState(false);
  const handleClose = () => setOpen(false);

  const onSubmit = async (data: OrganizationInputForm) => {
    const inputValues = {
      ...data,
      organization_tags: data.organization_tags?.map(tag => tag.id),
    };
    const result = await dispatch(addOrganization(inputValues));
    if (result.result) {
      onCreate?.(result.entities.organizations[result.result]);
      handleClose();
    }
    // Submission errors flow back to the form (react-final-form contract).
    return result;
  };

  return (
    <div>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={handleClose}
        title={t('Create an organization')}
      >
        <OrganizationForm
          onSubmit={onSubmit}
          initialValues={{ organization_tags: [] }}
          handleClose={handleClose}
        />
      </Drawer>
    </div>
  );
};

export default CreateOrganization;
