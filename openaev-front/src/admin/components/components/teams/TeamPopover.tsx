import { Button, Dialog as MuiDialog, DialogActions, DialogContent, DialogContentText } from '@mui/material';
import { type FunctionComponent, useContext, useState } from 'react';

import { type ExercisesHelper } from '../../../../actions/exercises/exercise-helper';
import { type OrganizationHelper, type TagHelper } from '../../../../actions/helper';
import { type TeamInputForm } from '../../../../actions/teams/Team';
import { deleteTeam, updateTeam } from '../../../../actions/teams/team-actions';
import { type TeamsHelper } from '../../../../actions/teams/team-helper';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import Dialog from '../../../../components/common/dialog/Dialog';
import DialogDelete from '../../../../components/common/DialogDelete';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type Team, type TeamOutput, type TeamUpdateInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { type Option, organizationOption, tagOptions } from '../../../../utils/Option';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { TeamContext } from '../../common/Context';
import TeamForm from './TeamForm';

interface TeamPopoverProps {
  team: Team | TeamOutput;
  managePlayers?: () => void;
  disabled?: boolean;
  openEditOnInit?: boolean;
  onRemoveTeamFromInject?: (teamId: string) => void;
  onUpdate?: (result: Team) => void;
  onDelete?: (result: string) => void;
}

const TeamPopover: FunctionComponent<TeamPopoverProps> = ({
  team,
  managePlayers,
  disabled,
  openEditOnInit = false,
  onRemoveTeamFromInject = null,
  onUpdate,
  onDelete,
}) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  const { organizationsMap, tagsMap } = useHelper(
    (
      helper: ExercisesHelper & TeamsHelper & OrganizationHelper & TagHelper,
    ) => {
      return {
        organizationsMap: helper.getOrganizationsMap(),
        tagsMap: helper.getTagsMap(),
      };
    },
  );

  const { onRemoveTeam } = useContext(TeamContext);

  const [openDelete, setOpenDelete] = useState(false);
  const [openEdit, setOpenEdit] = useState(openEditOnInit);
  const [openRemove, setOpenRemove] = useState(false);
  const [openRemoveFromInject, setOpenRemoveFromInject] = useState(false);

  // Edition
  const handleOpenEdit = () => {
    setOpenEdit(true);
  };

  const handleCloseEdit = () => setOpenEdit(false);

  const onSubmitEdit = (data: TeamInputForm) => {
    const inputValues: TeamUpdateInput = {
      ...data,
      team_organization: data.team_organization?.id,
      team_tags: data.team_tags?.map((tag: Option) => tag.id),
    };
    return dispatch(updateTeam(team.team_id, inputValues)).then(
      (result: {
        result: string;
        entities: { teams: Record<string, Team> };
      }) => {
        if (result.entities) {
          if (onUpdate) {
            const updated = result.entities.teams[result.result];
            onUpdate(updated);
          }
        }
        handleCloseEdit();
        return result;
      },
    );
  };

  // Deletion
  const handleOpenDelete = () => {
    setOpenDelete(true);
  };

  const handleCloseDelete = () => setOpenDelete(false);

  const submitDelete = () => {
    dispatch(deleteTeam(team.team_id)).then(
      () => {
        if (onDelete) {
          onDelete(team.team_id);
        }
        handleCloseDelete();
      },
    );
  };

  // Remove
  const handleOpenRemove = () => {
    setOpenRemove(true);
  };

  const handleCloseRemove = () => setOpenRemove(false);

  const submitRemove = () => {
    return onRemoveTeam!(team.team_id).then(() => handleCloseRemove());
  };

  // Remove
  const handleOpenRemoveFromInject = () => {
    setOpenRemoveFromInject(true);
  };

  const handleCloseRemoveFromInject = () => setOpenRemoveFromInject(false);

  const submitRemoveFromInject = () => {
    onRemoveTeamFromInject!(team.team_id);
    handleCloseRemoveFromInject();
  };

  const initialValues: TeamInputForm = {
    ...team,
    team_organization: organizationOption(
      team.team_organization,
      organizationsMap,
    ),
    team_tags: tagOptions(team.team_tags, tagsMap),
  };

  // Button Popover
  const entries = [];
  entries.push({
    label: 'Update',
    action: () => handleOpenEdit(),
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.TEAMS_AND_PLAYERS),
  });
  if (managePlayers) entries.push({
    label: 'Manage players',
    action: () => managePlayers(),
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.TEAMS_AND_PLAYERS),
  });
  if (onRemoveTeam && !onRemoveTeamFromInject && !team.team_contextual) entries.push({
    label: 'Remove from the context',
    action: () => handleOpenRemove(),
    userRight: true,
  });
  if (onRemoveTeamFromInject) entries.push({
    label: 'Remove from the inject',
    action: () => handleOpenRemoveFromInject(),
    userRight: true,
  });
  entries.push({
    label: 'Delete',
    action: () => handleOpenDelete(),
    userRight: ability.can(ACTIONS.DELETE, SUBJECTS.TEAMS_AND_PLAYERS),
  });

  return (
    <>
      <ButtonPopover disabled={disabled} entries={entries} variant="icon" />
      <DialogDelete
        open={openDelete}
        handleClose={handleCloseDelete}
        handleSubmit={submitDelete}
        text={t('Do you want to delete this team?')}
      />
      <Dialog
        open={openEdit}
        handleClose={handleCloseEdit}
        title={t('Update the team')}
      >
        <TeamForm
          initialValues={initialValues}
          handleClose={handleCloseEdit}
          onSubmit={onSubmitEdit}
          editing
        />
      </Dialog>
      <MuiDialog
        open={openRemove}
        slots={{ transition: Transition }}
        onClose={handleCloseRemove}
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to remove the team from this context?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseRemove}>{t('Cancel')}</Button>
          <Button color="secondary" onClick={submitRemove}>
            {t('Remove')}
          </Button>
        </DialogActions>
      </MuiDialog>
      <MuiDialog
        open={openRemoveFromInject}
        slots={{ transition: Transition }}
        onClose={handleCloseRemoveFromInject}
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to remove the team from this inject?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseRemoveFromInject}>{t('Cancel')}</Button>
          <Button color="secondary" onClick={submitRemoveFromInject}>
            {t('Remove')}
          </Button>
        </DialogActions>
      </MuiDialog>
    </>
  );
};

export default TeamPopover;
