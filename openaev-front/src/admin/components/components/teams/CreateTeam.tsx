import { type FunctionComponent, useContext, useState } from 'react';

import { type TeamInputForm } from '../../../../actions/teams/Team';
import { addTeam } from '../../../../actions/teams/team-actions';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Dialog from '../../../../components/common/dialog/Dialog';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type Team, type TeamCreateInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { type Option } from '../../../../utils/Option';
import { TeamContext } from '../../common/Context';
import TeamForm from './TeamForm';

interface CreateTeamProps {
  inline?: boolean;
  onCreate: (result: Team) => void;
}

const CreateTeam: FunctionComponent<CreateTeamProps> = ({
  inline,
  onCreate,
}) => {
  const dispatch = useAppDispatch();
  const { t } = useFormatter();
  const [openDialog, setOpenDialog] = useState(false);
  const { onCreateTeam } = useContext(TeamContext);
  const handleOpen = () => setOpenDialog(true);
  const handleClose = () => setOpenDialog(false);
  const onSubmit = async (data: TeamInputForm) => {
    const inputValues: TeamCreateInput = {
      ...data,
      team_organization: data.team_organization?.id,
      team_tags: data.team_tags?.map((tag: Option) => tag.id),
    };
    let value;
    if (inputValues.team_contextual) {
      value = await onCreateTeam!(inputValues);
    } else {
      value = await dispatch(addTeam(inputValues));
    }
    if (value.entities) {
      if (onCreate) {
        const created = value.entities.teams[value.result];
        onCreate(created);
      }
      handleClose();
    }
    return value;
  };

  return (
    <div>
      {inline ? (
        // Header placement (picker top-right): compact creation button.
        <ButtonCreate onClick={handleOpen} label={t('Create a new team')} />
      ) : (
        <ButtonCreate onClick={handleOpen} />
      )}
      {inline ? (
        <Dialog
          open={openDialog}
          handleClose={handleClose}
          title={t('Create a new team')}
        >
          <TeamForm
            initialValues={{ team_tags: [] }}
            handleClose={handleClose}
            onSubmit={onSubmit}
          />
        </Dialog>
      ) : (
        <Drawer
          open={openDialog}
          handleClose={handleClose}
          title={t('Create a new team')}
        >
          <TeamForm
            initialValues={{ team_tags: [] }}
            handleClose={handleClose}
            onSubmit={onSubmit}
          />
        </Drawer>
      )}

    </div>
  );
};

export default CreateTeam;
