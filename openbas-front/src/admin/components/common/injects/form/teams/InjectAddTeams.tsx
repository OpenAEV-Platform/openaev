import { ControlPointOutlined, GroupsOutlined } from '@mui/icons-material';
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type FunctionComponent, useContext, useEffect, useMemo, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { findTeams } from '../../../../../../actions/teams/team-actions';
import PaginationComponentV2 from '../../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../../../../components/common/queryable/useQueryableWithLocalStorage';
import SelectList, { type SelectListElements } from '../../../../../../components/common/SelectList';
import Transition from '../../../../../../components/common/Transition';
import { useFormatter } from '../../../../../../components/i18n';
import ItemTags from '../../../../../../components/ItemTags';
import { type TeamOutput } from '../../../../../../utils/api-types';
import { Can } from '../../../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../../../utils/permissions/types';
import CreateTeam from '../../../../components/teams/CreateTeam';
import { TeamContext } from '../../../Context';

const useStyles = makeStyles()(theme => ({
  text: {
    fontSize: 15,
    color: theme.palette.primary.main,
    fontWeight: 500,
  },
  textError: {
    fontSize: 15,
    color: theme.palette.error.main,
    fontWeight: 500,
  },
}));

interface Props {
  handleModifyTeams: (teamIds: string[]) => void;
  injectTeamsIds: string[];
  disabled?: boolean;
  error?: string | null;
}

const InjectAddTeams: FunctionComponent<Props> = ({
  handleModifyTeams,
  injectTeamsIds,
  disabled = false,
  error,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const { classes } = useStyles();
  const { searchTeams, onReplaceTeam } = useContext(TeamContext);

  const [teamValues, setTeamValues] = useState<TeamOutput[]>([]);
  const [selectedTeamValues, setSelectedTeamValues] = useState<TeamOutput[]>([]);

  // Dialog
  const [open, setOpen] = useState(false);

  const handleClose = () => {
    setOpen(false);
  };

  const submitAddTeams = () => {
    handleModifyTeams(selectedTeamValues.map(v => v.team_id));
    handleClose();
  };

  useEffect(() => {
    if (open) {
      findTeams(injectTeamsIds).then(result => setSelectedTeamValues(result.data));
    }
  }, [open, injectTeamsIds]);

  // Pagination
  const addTeam = (_teamId: string, team: TeamOutput) => setSelectedTeamValues([...selectedTeamValues, team]);
  const removeTeam = (teamId: string) => setSelectedTeamValues(selectedTeamValues.filter(v => v.team_id !== teamId));

  // Headers
  const elements: SelectListElements<TeamOutput> = useMemo(() => ({
    icon: { value: () => <GroupsOutlined /> },
    headers: [
      {
        field: 'team_name',
        value: (team: TeamOutput) => team.team_name,
        width: 70,
      },
      {
        field: 'team_tags',
        value: (team: TeamOutput) => <ItemTags variant="reduced-view" tags={team.team_tags} />,
        width: 30,
      },
    ],
  }), []);

  const { queryableHelpers, searchPaginationInput } = useQueryable(buildSearchPagination({}));
  const paginationComponent = (
    <PaginationComponentV2
      fetch={input => searchTeams(input, true)}
      searchPaginationInput={searchPaginationInput}
      setContent={setTeamValues}
      entityPrefix="team"
      availableFilterNames={['team_tags']}
      queryableHelpers={queryableHelpers}
    />
  );

  return (
    <div>
      <ListItemButton
        divider
        onClick={() => setOpen(true)}
        color="primary"
        disabled={disabled}
      >
        <ListItemIcon>
          <ControlPointOutlined color={error ? 'error' : 'primary'} />
        </ListItemIcon>
        <ListItemText
          primary={t('Modify target teams')}
          classes={{ primary: error ? classes.textError : classes.text }}
        />
      </ListItemButton>
      <Dialog
        open={open}
        TransitionComponent={Transition}
        onClose={handleClose}
        fullWidth
        maxWidth="lg"
        PaperProps={{
          elevation: 1,
          sx: {
            minHeight: 580,
            maxHeight: 580,
          },
        }}
      >
        <DialogTitle>{t('Modify target teams in this inject')}</DialogTitle>
        <DialogContent>
          <Box sx={{ marginTop: 2 }}>
            <SelectList
              values={teamValues}
              selectedValues={selectedTeamValues}
              elements={elements}
              onSelect={addTeam}
              onDelete={removeTeam}
              paginationComponent={paginationComponent}
              buttonComponent={(
                <Can I={ACTIONS.MANAGE} a={SUBJECTS.TEAMS_AND_PLAYERS}>
                  <CreateTeam
                    inline
                    onCreate={(team) => {
                      setTeamValues([...teamValues, team as TeamOutput]);
                      setSelectedTeamValues([...selectedTeamValues, team as TeamOutput]);
                      // If a team is created, it has to be linked to the simulation/scenario
                      onReplaceTeam?.([...selectedTeamValues, team as TeamOutput].map(v => v.team_id));
                    }}
                  />
                </Can>
              )}
              getId={element => element.team_id}
              getName={element => element.team_name}
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose}>{t('Cancel')}</Button>
          <Button color="secondary" onClick={submitAddTeams}>
            {t('Update')}
          </Button>
        </DialogActions>
      </Dialog>
    </div>
  );
};
export default InjectAddTeams;
