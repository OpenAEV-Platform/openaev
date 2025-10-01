import { type FunctionComponent, useContext, useState } from 'react';
import { useNavigate } from 'react-router';

import { deleteExercise, duplicateExercise, updateExercise } from '../../../../actions/Exercise';
import { checkExerciseTagRules } from '../../../../actions/exercises/exercise-action';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogApplyTagRule from '../../../../components/common/DialogApplyTagRule';
import DialogDelete from '../../../../components/common/DialogDelete';
import DialogDuplicate from '../../../../components/common/DialogDuplicate';
import Drawer from '../../../../components/common/Drawer';
import ExportOptionsDialog from '../../../../components/common/export/ExportOptionsDialog';
import { useFormatter } from '../../../../components/i18n';
import {
  type CheckScenarioRulesOutput,
  type Exercise,
  type UpdateExerciseInput,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import useSimulationPermissions from '../../../../utils/permissions/useSimulationPermissions';
import ExerciseForm from './ExerciseForm';
import ExerciseReports from './reports/ExerciseReports';

export type ExerciseActionPopover = 'Duplicate' | 'Update' | 'Delete' | 'Export' | 'Access reports';

interface ExercisePopoverProps {
  exercise: Exercise;
  actions: ExerciseActionPopover[];
  onDelete?: (result: string) => void;
  inList?: boolean;
}

const ExercisePopover: FunctionComponent<ExercisePopoverProps> = ({
  exercise,
  actions = [],
  onDelete,
  inList = false,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const permissions = useSimulationPermissions(exercise.exercise_id, exercise);
  const ability = useContext(AbilityContext);

  // Form
  const initialValues: UpdateExerciseInput = {
    exercise_name: exercise.exercise_name,
    exercise_subtitle: exercise.exercise_subtitle ?? '',
    exercise_description: exercise.exercise_description,
    exercise_category: exercise.exercise_category ?? 'attack-scenario',
    exercise_main_focus: exercise.exercise_main_focus ?? 'incident-response',
    exercise_severity: exercise.exercise_severity ?? 'high',
    exercise_tags: exercise.exercise_tags ?? [],
    exercise_mail_from: exercise.exercise_mail_from ?? '',
    exercise_mails_reply_to: exercise.exercise_mails_reply_to ?? [],
    exercise_message_header: exercise.exercise_message_header ?? '',
    exercise_message_footer: exercise.exercise_message_footer ?? '',
    exercise_custom_dashboard: exercise.exercise_custom_dashboard ?? '',
    apply_tag_rule: false,
  };

  // Edit
  const [openEdit, setOpenEdit] = useState(false);
  const handleOpenEdit = () => setOpenEdit(true);
  const handleCloseEdit = () => setOpenEdit(false);
  const [exerciseFormData, setExerciseFormData] = useState<UpdateExerciseInput>(initialValues);

  // Delete
  const [openDelete, setOpenDelete] = useState(false);
  const handleOpenDelete = () => setOpenDelete(true);
  const handleCloseDelete = () => setOpenDelete(false);

  const submitDelete = () => {
    dispatch(deleteExercise(exercise.exercise_id)).then(() => {
      handleCloseDelete();
      if (onDelete) onDelete(exercise.exercise_id);
    });
  };

  // Duplicate
  const [openDuplicate, setOpenDuplicate] = useState(false);
  const handleOpenDuplicate = () => setOpenDuplicate(true);
  const handleCloseDuplicate = () => setOpenDuplicate(false);

  const submitDuplicate = () => {
    dispatch(duplicateExercise(exercise.exercise_id)).then((result: {
      result: string;
      entities: { exercises: Exercise };
    }) => {
      handleCloseDuplicate();
      navigate(`/admin/simulations/${result.result}`);
    });
  };

  // Export
  const [openExport, setOpenExport] = useState(false);
  const handleOpenExport = () => setOpenExport(true);
  const handleCloseExport = () => setOpenExport(false);

  // Reports
  const [openReports, setOpenReports] = useState(false);
  const handleOpenReports = () => setOpenReports(true);
  const handleCloseReports = () => setOpenReports(false);

  // apply rule dialog
  const [openApplyRule, setOpenApplyRule] = useState(false);
  const handleOpenApplyRule = () => setOpenApplyRule(true);
  const handleCloseApplyRule = () => setOpenApplyRule(false);

  const submitExport = (withPlayers: boolean, withTeams: boolean, withVariableValues: boolean) => {
    const link = document.createElement('a');
    link.href = `/api/exercises/${exercise.exercise_id}/export?isWithTeams=${withTeams}&isWithPlayers=${withPlayers}&isWithVariableValues=${withVariableValues}`;
    link.click();
    handleCloseExport();
  };

  // Button Popover
  const entries = [];
  if (actions.includes('Update')) entries.push({
    label: 'Update',
    action: () => handleOpenEdit(),
    disabled: !permissions.canManage,
    userRight: permissions.canManage,
  });
  if (actions.includes('Duplicate')) entries.push({
    label: 'Duplicate',
    action: () => handleOpenDuplicate(),
    userRight: permissions.canManage && ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT),
  });
  if (actions.includes('Export')) entries.push({
    label: 'Export',
    action: () => handleOpenExport(),
    userRight: true,
  });
  if (actions.includes('Access reports')) entries.push({
    label: 'Access reports',
    action: () => handleOpenReports(),
    userRight: true,
  });
  if (actions.includes('Delete')) entries.push({
    label: 'Delete',
    action: () => handleOpenDelete(),
    userRight: permissions.canManage,
  });

  const submitExerciseUpdate = (data: UpdateExerciseInput) => {
    const input = {
      exercise_name: data.exercise_name,
      exercise_subtitle: data.exercise_subtitle,
      exercise_severity: data.exercise_severity,
      exercise_category: data.exercise_category,
      exercise_description: data.exercise_description,
      exercise_main_focus: data.exercise_main_focus,
      exercise_tags: data.exercise_tags,
      exercise_mails_reply_to: data.exercise_mails_reply_to,
      exercise_mail_from: data.exercise_mail_from,
      exercise_message_header: data.exercise_message_header,
      exercise_message_footer: data.exercise_message_footer,
      exercise_custom_dashboard: data.exercise_custom_dashboard,
      apply_tag_rule: data.apply_tag_rule,
    };
    return dispatch(updateExercise(exercise.exercise_id, input)).then(() => handleCloseEdit());
  };

  const handleTagRuleChoice = (shouldApply: boolean) => {
    exerciseFormData.apply_tag_rule = shouldApply;
    submitExerciseUpdate(exerciseFormData);
    handleCloseApplyRule();
  };

  const onSubmit = (data: UpdateExerciseInput) => {
    setExerciseFormData(data);
    // before updating the exercise we are checking if tag rules could apply
    // -> if yes we ask the user to apply or not apply the rules at the update
    checkExerciseTagRules(exercise.exercise_id, data.exercise_tags ?? []).then(
      (result: { data: CheckScenarioRulesOutput }) => {
        if (result.data.rules_found) {
          handleOpenApplyRule();
        } else {
          submitExerciseUpdate(data);
        }
      },
    );
  };
  return (
    <>
      <ButtonPopover entries={entries} variant={inList ? 'icon' : 'toggle'} />
      <Drawer
        open={openEdit}
        handleClose={handleCloseEdit}
        title={t('Update simulation')}
      >
        <ExerciseForm
          onSubmit={onSubmit}
          initialValues={initialValues}
          disabled={permissions.readOnly}
          handleClose={handleCloseEdit}
          edit
        />

      </Drawer>
      <DialogApplyTagRule
        open={openApplyRule}
        handleClose={handleCloseApplyRule}
        handleApplyRule={() => handleTagRuleChoice(true)}
        handleDontApplyRule={() => handleTagRuleChoice(false)}
      />
      <Drawer
        open={openReports}
        containerStyle={{ padding: '0px' }}
        handleClose={handleCloseReports}
        title={t('Reports')}
      >
        <ExerciseReports exerciseId={exercise.exercise_id} exerciseName={exercise.exercise_name} />
      </Drawer>
      <DialogDuplicate
        open={openDuplicate}
        handleClose={handleCloseDuplicate}
        handleSubmit={submitDuplicate}
        text={`${t('Do you want to duplicate this simulation:')} ${exercise.exercise_name} ?`}
      />
      <ExportOptionsDialog
        title={t('Export the simulation')}
        open={openExport}
        onCancel={handleCloseExport}
        onClose={handleCloseExport}
        onSubmit={submitExport}
      />
      <DialogDelete
        open={openDelete}
        handleClose={handleCloseDelete}
        handleSubmit={submitDelete}
        text={`${t('Do you want to delete this simulation:')} ${exercise.exercise_name} ?`}
      />
    </>
  );
};

export default ExercisePopover;
