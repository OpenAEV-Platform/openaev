import { type FunctionComponent, useState } from 'react';

import {
  deleteCustomDomain,
  verifyCustomDomain,
} from '../../../../actions/custom_domains/customdomain-actions';
import ButtonPopover, { type PopoverEntry } from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type CustomDomain } from '../../../../utils/api-types';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import CustomDomainInstructionsPanel from './CustomDomainInstructionsPanel';

interface Props {
  customDomain: CustomDomain;
  onDelete?: (result: string) => void;
  onUpdate?: (result: CustomDomain) => void;
}

const CustomDomainPopover: FunctionComponent<Props> = ({ customDomain, onDelete, onUpdate }) => {
  const { t } = useFormatter();
  const ability = useAbility();
  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);

  const [openInstructions, setOpenInstructions] = useState(false);
  const [openDelete, setOpenDelete] = useState(false);

  const submitVerify = () => {
    verifyCustomDomain(customDomain.custom_domain_id).then((result: { data: CustomDomain }) => {
      if (result?.data && onUpdate) {
        onUpdate(result.data);
      }
      return result;
    });
  };

  const submitDelete = () => {
    deleteCustomDomain(customDomain.custom_domain_id);
    if (onDelete) {
      onDelete(customDomain.custom_domain_id);
    }
    setOpenDelete(false);
  };

  const entries: PopoverEntry[] = [
    {
      label: 'DNS setup & verification',
      action: () => setOpenInstructions(true),
      userRight: canManage,
    },
    {
      label: 'Verify now',
      action: submitVerify,
      userRight: canManage,
    },
    {
      label: 'Delete',
      action: () => setOpenDelete(true),
      userRight: canManage,
    },
  ];

  return (
    <>
      <ButtonPopover entries={entries} variant="icon" />
      <Drawer
        open={openInstructions}
        handleClose={() => setOpenInstructions(false)}
        title={t('DNS setup & verification')}
      >
        <CustomDomainInstructionsPanel customDomain={customDomain} onUpdate={onUpdate} />
      </Drawer>
      <DialogDelete
        open={openDelete}
        handleClose={() => setOpenDelete(false)}
        handleSubmit={submitDelete}
        text={t('Do you want to delete this custom domain? Landing pages linked to it will fall back to the platform domain.')}
      />
    </>
  );
};

export default CustomDomainPopover;
