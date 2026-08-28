import { ControlPointOutlined, GroupsOutlined } from '@mui/icons-material';
import { ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type FunctionComponent, useContext, useEffect, useMemo, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { findTeams } from '../../../../../../actions/teams/team-actions';
import PaginationComponentV2 from '../../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../../../../components/common/queryable/useQueryableWithLocalStorage';
import SelectListPicker, { type SelectListPickerElements } from '../../../../../../components/common/SelectListPicker';
import { useFormatter } from '../../../../../../components/i18n';
import ItemTags from '../../../../../../components/ItemTags';
import { type TeamOutput } from '../../../../../../utils/api-types';
import { Can } from '../../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../../utils/permissions/types';
import CreateTeam from '../../../../components/teams/CreateTeam';
import { TeamContext } from '../../../Context';

const useStyles = makeStyles()(theme => ({
  icon: { minWidth: 30 },
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
  const [isLoading, setIsLoading] = useState<boolean>(false);

  // Drawer
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

  const selectedIds = useMemo(() => selectedTeamValues.map(v => v.team_id), [selectedTeamValues]);

  const toggleTeam = (teamId: string, team: TeamOutput) => {
    if (selectedIds.includes(teamId)) {
      setSelectedTeamValues(selectedTeamValues.filter(v => v.team_id !== teamId));
    } else {
      setSelectedTeamValues([...selectedTeamValues, team]);
    }
  };

  // Headers
  const elements: SelectListPickerElements<TeamOutput> = useMemo(() => ({
    icon: { value: () => <GroupsOutlined /> },
    headers: [
      {
        field: 'team_name',
        label: 'Name',
        isSortable: true,
        value: (team: TeamOutput) => team.team_name,
        width: 50,
      },
      {
        field: 'team_users_number',
        label: 'Players',
        isSortable: false,
        value: (team: TeamOutput) => String(team.team_users_number ?? 0),
        width: 20,
      },
      {
        field: 'team_tags',
        label: 'Tags',
        value: (team: TeamOutput) => <ItemTags variant="list" limit={2} tags={team.team_tags} />,
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
      setLoading={setIsLoading}
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
        <ListItemIcon classes={{ root: classes.icon }}>
          <ControlPointOutlined color={error ? 'error' : 'primary'} fontSize="small" />
        </ListItemIcon>
        <ListItemText
          primary={t('Modify target teams')}
          classes={{ primary: error ? classes.textError : classes.text }}
        />
      </ListItemButton>
      {/* Inline dialog: the inject form is already a drawer (never drawer over drawer). */}
      <SelectListPicker<TeamOutput>
        open={open}
        onClose={handleClose}
        onSubmit={submitAddTeams}
        title={t('Modify target teams in this inject')}
        inline
        headerComponent={paginationComponent}
        values={teamValues}
        elements={elements}
        sortHelpers={queryableHelpers.sortHelpers}
        selectedIds={selectedIds}
        onToggle={toggleTeam}
        getId={element => element.team_id}
        isLoading={isLoading}
        containerTestId="select-team-list"
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
      />
    </div>
  );
};
export default InjectAddTeams;
