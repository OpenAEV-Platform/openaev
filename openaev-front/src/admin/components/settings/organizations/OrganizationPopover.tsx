import { type FunctionComponent, useContext, useState } from 'react';

import { deleteOrganization, updateOrganization } from '../../../../actions/Organization';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type Organization, type Tag } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { type Option, tagOptions } from '../../../../utils/Option';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import OrganizationForm, { type OrganizationInputForm } from './OrganizationForm';

interface Props {
  organization: Organization;
  tagsMap: Record<string, Tag>;
  openEditOnInit?: boolean;
  onUpdate?: (organization: Organization) => void;
  onDelete?: (organizationId: string) => void;
}

// Settings > Security > Organizations actions. Separated from the
// business-side OrganizationPopover (teams/organizations) on purpose.
const OrganizationPopover: FunctionComponent<Props> = ({
  organization,
  tagsMap,
  openEditOnInit = false,
  onUpdate,
  onDelete,
}) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  const [openEdit, setOpenEdit] = useState(openEditOnInit);
  const [openDelete, setOpenDelete] = useState(false);

  const onSubmitEdit = (data: OrganizationInputForm) => {
    const inputValues = {
      ...data,
      organization_tags: (data.organization_tags ?? []).map((tag: Option) => tag.id),
    };
    return dispatch(updateOrganization(organization.organization_id, inputValues)).then(
      (result: {
        result: string;
        entities: { organizations: Record<string, Organization> };
      }) => {
        if (onUpdate && result?.result) {
          onUpdate(result.entities.organizations[result.result]);
        }
        setOpenEdit(false);
        return result;
      },
    );
  };

  const submitDelete = () => {
    dispatch(deleteOrganization(organization.organization_id)).then(() => {
      onDelete?.(organization.organization_id);
    });
    setOpenDelete(false);
  };

  const entries = [
    {
      label: 'Update',
      action: () => setOpenEdit(true),
      userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS),
    },
    {
      label: 'Delete',
      action: () => setOpenDelete(true),
      userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS),
    },
  ];

  const initialValues: OrganizationInputForm = {
    organization_name: organization.organization_name,
    organization_description: organization.organization_description,
    organization_tags: tagOptions(organization.organization_tags, tagsMap),
  };

  return (
    <>
      <ButtonPopover entries={entries} variant="icon" />
      <DialogDelete
        open={openDelete}
        handleClose={() => setOpenDelete(false)}
        handleSubmit={submitDelete}
        text={t('Do you want to delete this organization?')}
      />
      <Drawer
        open={openEdit}
        handleClose={() => setOpenEdit(false)}
        title={t('Update the organization')}
      >
        <OrganizationForm
          initialValues={initialValues}
          editing
          onSubmit={onSubmitEdit}
          handleClose={() => setOpenEdit(false)}
        />
      </Drawer>
    </>
  );
};

export default OrganizationPopover;
