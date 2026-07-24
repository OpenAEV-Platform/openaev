import { type FunctionComponent, useState } from 'react';

import { addOrganization } from '../../../../actions/Organization';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type Organization } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { type Option } from '../../../../utils/Option';
import OrganizationForm, { type OrganizationInputForm } from './OrganizationForm';

interface Props {
  onCreate?: (organization: Organization) => void;
}

// Settings > Security > Organizations creation. Separated from the
// business-side CreateOrganization (teams/organizations) on purpose.
const CreateOrganization: FunctionComponent<Props> = ({ onCreate }) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const [open, setOpen] = useState(false);

  const onSubmit = (data: OrganizationInputForm) => {
    const inputValues = {
      ...data,
      organization_tags: (data.organization_tags ?? []).map((tag: Option) => tag.id),
    };
    return dispatch(addOrganization(inputValues)).then(
      (result: {
        result: string;
        entities: { organizations: Record<string, Organization> };
      }) => {
        if (result.result) {
          if (onCreate) {
            onCreate(result.entities.organizations[result.result]);
          }
          setOpen(false);
        }
        return result;
      },
    );
  };

  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
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
