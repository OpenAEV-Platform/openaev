import { type FunctionComponent, useContext, useState } from 'react';

import { deleteAiTarget, updateAiTarget } from '../../../../actions/assets/aiTarget-actions';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type AiTargetInput, type Asset as AiTarget } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import AiTargetForm from './AiTargetForm';

type AiTargetStoreWithType = AiTarget & { type: string };

interface Props {
  aiTarget: AiTargetStoreWithType;
  openEditOnInit?: boolean;
  onUpdate?: (result: AiTarget) => void;
  onDelete?: (result: string) => void;
  disabled?: boolean;
}

const AiTargetPopover: FunctionComponent<Props> = ({
  aiTarget,
  openEditOnInit = false,
  onUpdate,
  onDelete,
  disabled,
}) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  const initialValues = (({
    asset_name,
    ai_target_provider,
    ai_target_modality,
    ai_target_endpoint,
    ai_target_model,
    ai_target_system_prompt,
    ai_target_token,
    ai_target_configuration,
    asset_description,
    asset_tags,
  }) => ({
    asset_name,
    ai_target_provider,
    ai_target_modality,
    ai_target_endpoint,
    ai_target_model,
    ai_target_system_prompt,
    ai_target_token,
    ai_target_configuration,
    asset_description,
    asset_tags,
  }))(aiTarget) as AiTargetInput;

  // Edition
  const [edition, setEdition] = useState(openEditOnInit);

  const handleEdit = () => {
    setEdition(true);
  };
  const submitEdit = (data: AiTargetInput) => {
    dispatch(updateAiTarget(aiTarget.asset_id, data)).then(
      (result: {
        result: string;
        entities: { aitargets: Record<string, AiTarget> };
      }) => {
        if (result.entities) {
          if (onUpdate) {
            const aiTargetUpdated = result.entities.aitargets[result.result];
            onUpdate(aiTargetUpdated);
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
    dispatch(deleteAiTarget(aiTarget.asset_id)).then(
      () => {
        if (onDelete) {
          onDelete(aiTarget.asset_id);
        }
      },
    );
    setDeletion(false);
  };

  const entries = [];
  if (onUpdate) entries.push({
    label: t('Update'),
    action: () => handleEdit(),
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.ASSETS),
  });
  if (onDelete) entries.push({
    label: t('Delete'),
    action: () => handleDelete(),
    userRight: ability.can(ACTIONS.DELETE, SUBJECTS.ASSETS),
  });

  return (
    <>
      <ButtonPopover entries={entries} disabled={disabled} variant="icon" />

      <Drawer
        open={edition}
        handleClose={() => setEdition(false)}
        title={t('Update the AI target')}
      >
        <AiTargetForm
          initialValues={initialValues}
          editing
          onSubmit={submitEdit}
          handleClose={() => setEdition(false)}
        />
      </Drawer>
      <DialogDelete
        open={deletion}
        handleClose={() => setDeletion(false)}
        handleSubmit={submitDelete}
        text={t('Do you want to delete the AI target?')}
      />
    </>
  );
};

export default AiTargetPopover;
