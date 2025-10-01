import { type FunctionComponent, useContext, useState } from 'react';

import { deleteSecurityPlatform, updateSecurityPlatform } from '../../../../actions/assets/securityPlatform-actions';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type SecurityPlatform, type SecurityPlatformInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import SecurityPlatformForm from './SecurityPlatformForm';

type SecurityPlatformStoreWithType = SecurityPlatform & { type: string };

interface Props {
  securityPlatform: SecurityPlatformStoreWithType;
  assetGroupId?: string;
  assetGroupSecurityPlatformIds?: string[];
  onRemoveSecurityPlatformFromInject?: (assetId: string) => void;
  onRemoveSecurityPlatformFromAssetGroup?: (assetId: string) => void;
  openEditOnInit?: boolean;
  onUpdate?: (result: SecurityPlatform) => void;
  onDelete?: (result: string) => void;
  disabled?: boolean;
}

const SecurityPlatformPopover: FunctionComponent<Props> = ({
  securityPlatform,
  openEditOnInit = false,
  onUpdate,
  onDelete,
  disabled,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  const initialValues = (({
    asset_name,
    security_platform_type,
    asset_description,
    security_platform_logo_light,
    security_platform_logo_dark,
    asset_tags,
  }) => ({
    asset_name,
    asset_description,
    security_platform_type,
    security_platform_logo_light,
    security_platform_logo_dark,
    asset_tags,
  }))(securityPlatform);

  // Edition
  const [edition, setEdition] = useState(openEditOnInit);

  const handleEdit = () => {
    setEdition(true);
  };
  const submitEdit = (data: SecurityPlatformInput) => {
    dispatch(updateSecurityPlatform(securityPlatform.asset_id, data)).then(
      (result: {
        result: string;
        entities: { securityplatforms: Record<string, SecurityPlatform> };
      }) => {
        if (result.entities) {
          if (onUpdate) {
            const securityPlatformUpdated = result.entities.securityplatforms[result.result];
            onUpdate(securityPlatformUpdated);
          }
        }
        return result;
      },
    );
    setEdition(false);
  };

  // Deletion
  const [deletion, setDeletion] = useState(false);

  const handleDelete = () => {
    setDeletion(true);
  };
  const submitDelete = () => {
    dispatch(deleteSecurityPlatform(securityPlatform.asset_id)).then(
      () => {
        if (onDelete) {
          onDelete(securityPlatform.asset_id);
        }
      },
    );
    setDeletion(false);
  };

  // Button Popover
  const entries = [];
  if (onUpdate) entries.push({
    label: t('Update'),
    action: () => handleEdit(),
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.SECURITY_PLATFORMS),
  });
  if (onDelete) entries.push({
    label: t('Delete'),
    action: () => handleDelete(),
    userRight: ability.can(ACTIONS.DELETE, SUBJECTS.SECURITY_PLATFORMS),
  });

  return (
    <>
      <ButtonPopover entries={entries} disabled={disabled} variant="icon" />

      <Drawer
        open={edition}
        handleClose={() => setEdition(false)}
        title={t('Update the security platform')}
      >
        <SecurityPlatformForm
          initialValues={initialValues}
          editing
          onSubmit={submitEdit}
          handleClose={() => setEdition(false)}
          securityPlatformId={securityPlatform.asset_id}
        />
      </Drawer>
      <DialogDelete
        open={deletion}
        handleClose={() => setDeletion(false)}
        handleSubmit={submitDelete}
        text={t('Do you want to delete the security platform?')}
      />
    </>
  );
};

export default SecurityPlatformPopover;
