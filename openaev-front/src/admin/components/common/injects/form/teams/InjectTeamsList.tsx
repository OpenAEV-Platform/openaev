import { GroupsOutlined } from '@mui/icons-material';
import { FormHelperText, List, ListItem, ListItemIcon, ListItemText, Tooltip } from '@mui/material';
import { type FunctionComponent, useContext, useEffect, useState } from 'react';
import { useFormContext, useWatch } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { findTeams } from '../../../../../../actions/teams/team-actions';
import { useFormatter } from '../../../../../../components/i18n';
import ItemTags from '../../../../../../components/ItemTags';
import { type TeamOutput } from '../../../../../../utils/api-types';
import { Can } from '../../../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../../../utils/permissions/types';
import TeamPopover from '../../../../components/teams/TeamPopover';
import { TeamContext } from '../../../Context';
import InjectAddTeams from './InjectAddTeams';

const useStyles = makeStyles()(theme => ({
  column: {
    display: 'grid',
    gridTemplateColumns: '2fr 1fr 1fr 2fr',
  },
  bodyItem: { fontSize: theme.typography.h3.fontSize },
}));

interface Props {
  readOnly?: boolean;
  hideEnabledUsersNumber?: boolean;
  error?: string | null;
}

const InjectTeamsList: FunctionComponent<Props> = ({ readOnly = false, hideEnabledUsersNumber = false, error }) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const { control, setValue } = useFormContext();
  const { computeTeamUsersEnabled, allUsersNumber, allUsersEnabledNumber } = useContext(TeamContext);

  // -- TEAMS VALUES --
  const allTeams = useWatch({
    control,
    name: 'inject_all_teams',
  });
  const injectTeamIds = useWatch({
    control,
    name: 'inject_teams',
  });
  const [teams, setTeams] = useState<TeamOutput[]>([]);

  useEffect(() => {
    if (injectTeamIds.length > 0) {
      findTeams(injectTeamIds).then(result =>
        setTeams(result.data.sort((a: TeamOutput, b: TeamOutput) => a.team_name.localeCompare(b.team_name))));
    } else {
      setTeams([]);
    }
  }, [injectTeamIds]);

  // -- ACTIONS --
  const onTeamsChange = (teamIds: string[]) => setValue('inject_teams', teamIds, { shouldValidate: true });

  const onRemoveTeam = (teamId: string) => {
    const updatedTeamIds = injectTeamIds.filter((id: string) => id !== teamId);
    setValue('inject_teams', updatedTeamIds, { shouldValidate: true });
  };

  const teamListItem = (team: TeamOutput, userEnabled: number) => (
    <ListItem
      key={team.team_id}
      divider
      secondaryAction={!allTeams && (
        <TeamPopover
          team={team}
          onRemoveTeamFromInject={onRemoveTeam}
          disabled={readOnly}
        />
      )}
    >
      <ListItemIcon>
        <GroupsOutlined />
      </ListItemIcon>
      <ListItemText
        primary={(
          <div className={classes.column}>
            <div className={classes.bodyItem}>
              {team.team_name}
            </div>
            <Tooltip title={t('Number of users')} className={classes.bodyItem}>
              <span>{team.team_users_number}</span>
            </Tooltip>
            {!hideEnabledUsersNumber && (
              <Tooltip title={t('Number of enable user')} className={classes.bodyItem}>
                <span>{userEnabled}</span>
              </Tooltip>
            )}
            <div className={classes.bodyItem}>
              <ItemTags variant="reduced-view" tags={team.team_tags} />
            </div>
          </div>
        )}
      />
    </ListItem>
  );

  return (
    <>
      <List>
        {!allTeams && teams.map((team) => {
          return teamListItem(team, computeTeamUsersEnabled?.(team.team_id) ?? 0);
        })}
        {allTeams && teamListItem({
          team_id: 'all',
          team_name: t('All teams'),
          team_users_number: allUsersNumber,
          team_tags: [],
          team_exercises: [],
          team_scenarios: [],
          team_updated_at: '',
        }, allUsersEnabledNumber ?? 0)}
      </List>
      {!allTeams
        && (
          <Can I={ACTIONS.ACCESS} a={SUBJECTS.TEAMS_AND_PLAYERS}>
            <InjectAddTeams disabled={readOnly} handleModifyTeams={onTeamsChange} injectTeamsIds={injectTeamIds} error={error} />
          </Can>
        )}
      {!allTeams && error && (
        <FormHelperText error>
          {error}
        </FormHelperText>
      )}
    </>
  );
};

export default InjectTeamsList;
