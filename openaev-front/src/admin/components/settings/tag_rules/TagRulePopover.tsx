import { type FunctionComponent, useContext, useState } from 'react';

import { deleteTagRule } from '../../../../actions/tag_rules/tagrule-actions';
import ButtonPopover, { type PopoverEntry } from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type TagRuleOutput } from '../../../../utils/api-types';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import OPEN_CTI_TAG_NAME from './TagRuleConstants';
import TagRuleUpdate from './TagRuleUpdate';

interface Props {
  onDelete?: (result: string) => void;
  onUpdate?: (result: TagRuleOutput) => void;
  tagRule: TagRuleOutput;
}

const TagRulePopover: FunctionComponent<Props> = ({
  onDelete, onUpdate,
  tagRule,
}) => {
  const { t } = useFormatter();
  const ability = useContext(AbilityContext);

  // Edition
  const [openEdit, setOpenEdit] = useState(false);
  const handleOpenEdit = () => setOpenEdit(true);
  const handleCloseEdit = () => setOpenEdit(false);

  // Deletion
  const [openDelete, setOpenDelete] = useState(false);
  const handleOpenDelete = () => setOpenDelete(true);
  const handleCloseDelete = () => setOpenDelete(false);
  const submitDelete = () => {
    deleteTagRule(tagRule.tag_rule_id);
    if (onDelete) {
      onDelete(tagRule.tag_rule_id);
    }
    handleCloseDelete();
  };

  const entries: PopoverEntry[] = [
    {
      label: 'Update',
      action: handleOpenEdit,
      userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS),
    },
  ];

  // we don't allow the deletion of the rule with the tag opencti
  if (tagRule.tag_name != OPEN_CTI_TAG_NAME) {
    entries.push({
      label: 'Delete',
      action: handleOpenDelete,
      userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS),

    });
  }

  return (
    <>
      <ButtonPopover entries={entries} variant="icon" />

      <Drawer
        open={openEdit}
        handleClose={handleCloseEdit}
        title={t('Update the asset rule')}
      >
        <TagRuleUpdate
          tagRule={tagRule}
          onUpdate={onUpdate}
          handleClose={handleCloseEdit}
        />
      </Drawer>
      <DialogDelete
        open={openDelete}
        handleClose={handleCloseDelete}
        handleSubmit={submitDelete}
        text={t('Do you want to delete this asset rule?')}
      />
    </>
  );
};

export default TagRulePopover;
