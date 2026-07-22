import { AssignmentOutlined, GroupsOutlined, HubOutlined, PersonOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { Box, Chip, Drawer, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useCallback, useContext, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { searchAtomicTestings } from '../../../../actions/atomic_testings/atomic-testing-actions';
import { searchDistinctFindings } from '../../../../actions/findings/finding-actions';
import { type OrganizationHelper, type UserHelper } from '../../../../actions/helper';
import { fetchOrganizations } from '../../../../actions/Organization';
import { type TagHelper } from '../../../../actions/tags/tag-helper';
import { fetchTeam, fetchTeamPlayers, searchTeams, updateTeamPlayers } from '../../../../actions/teams/team-actions';
import { type TeamsHelper } from '../../../../actions/teams/team-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { DetailHero, DetailSections, Field, HeroStat, InformationGrid, Section, SectionBlock } from '../../../../components/common/detail/EntityDetailCommon';
import { generateFilterId } from '../../../../components/common/queryable/filter/FilterUtils';
import { initSorting, type Page } from '../../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import Empty from '../../../../components/Empty';
import ExpandableMarkdown from '../../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import Loader from '../../../../components/Loader';
import { ORGANIZATION_BASE_URL, PERSON_BASE_URL, TEAM_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import { type Filter, type InjectResultOutput, type SearchPaginationInput, type Team, type TeamOutput, type User } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import InjectResultList from '../../atomic_testings/InjectResultList';
import { TeamContext, type TeamContextType } from '../../common/Context';
import TeamPlayers from '../../components/teams/TeamPlayers';
import TeamPopover from '../../components/teams/TeamPopover';
import FindingList from '../../findings/FindingList';

const useStyles = makeStyles()(() => ({
  drawerPaper: {
    minHeight: '100vh',
    width: '50%',
    padding: 0,
  },
}));

const withFilter = (input: SearchPaginationInput, key: string, values: string[]): SearchPaginationInput => {
  const filter: Filter = {
    id: generateFilterId(),
    key,
    mode: 'or',
    operator: 'contains',
    values,
  };
  return {
    ...input,
    filterGroup: {
      mode: input.filterGroup?.mode ?? 'and',
      filters: [...(input.filterGroup?.filters ?? []), filter],
    },
  };
};

// Business-side team overview: headline metrics, identity + organization
// pivot, the team members (pivot to persons), the injects the team played and
// the findings linked to the team. Compact and grid-based.
const TeamDetail = () => {
  const { t, fldt } = useFormatter();
  const { classes } = useStyles();
  const theme = useTheme();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);
  const { teamId } = useParams() as { teamId: string };

  const [managing, setManaging] = useState(false);

  const { team, usersMap, organizationsMap } = useHelper((helper: TeamsHelper & UserHelper & OrganizationHelper & TagHelper) => ({
    team: helper.getTeam(teamId),
    usersMap: helper.getUsersMap(),
    organizationsMap: helper.getOrganizationsMap(),
  }));

  useDataLoader(() => {
    dispatch(fetchTeam(teamId));
    dispatch(fetchTeamPlayers(teamId));
    dispatch(fetchOrganizations());
  });

  const players: User[] = useMemo(
    () => (team?.team_users ?? []).map((id: string) => usersMap[id]).filter(Boolean) as User[],
    [team, usersMap],
  );

  const teamContext: TeamContextType = useMemo(() => ({
    onAddUsersTeam(id: Team['team_id'], userIds: string[]): Promise<void> {
      return dispatch(updateTeamPlayers(id, { team_users: [...(team?.team_users ?? []), ...userIds] }));
    },
    onRemoveUsersTeam(id: Team['team_id'], userIds: string[]): Promise<void> {
      return dispatch(updateTeamPlayers(id, { team_users: (team?.team_users ?? []).filter((u: string) => !userIds.includes(u)) }));
    },
    searchTeams(input: SearchPaginationInput): Promise<{ data: Page<TeamOutput> }> {
      return searchTeams(input) as Promise<{ data: Page<TeamOutput> }>;
    },
  }), [dispatch, team]);

  const { queryableHelpers: injectsHelpers, searchPaginationInput: injectsInput } = useQueryableWithLocalStorage(
    'team-injects',
    buildSearchPagination({ sorts: initSorting('inject_updated_at', 'DESC') }),
  );
  const fetchInjectsPlayed = useCallback(
    (input: SearchPaginationInput): Promise<{ data: Page<InjectResultOutput> }> =>
      searchAtomicTestings(withFilter(input, 'inject_teams', [teamId])) as Promise<{ data: Page<InjectResultOutput> }>,
    [teamId],
  );

  if (!team) {
    return <Loader />;
  }

  const organizationName = team.team_organization ? organizationsMap[team.team_organization]?.organization_name : undefined;
  const expectedScore = team.team_injects_expectations_total_expected_score ?? 0;
  const totalScore = team.team_injects_expectations_total_score ?? 0;
  const scoreLabel = expectedScore > 0 ? `${Math.round((totalScore / expectedScore) * 100)}%` : '-';

  return (
    <TeamContext.Provider value={teamContext}>
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
      }}
      >
        <Breadcrumbs
          variant="object"
          elements={[
            {
              label: t('Teams'),
              link: TEAM_BASE_URL,
            },
            {
              label: team.team_name,
              current: true,
            },
          ]}
        />

        <DetailHero
          icon={GroupsOutlined}
          title={team.team_name}
          chips={(
            <>
              <Chip size="small" variant="outlined" label={t('{count} players', { count: team.team_users_number ?? players.length })} sx={{ borderRadius: 1 }} />
              {organizationName && <Chip size="small" variant="outlined" label={organizationName} sx={{ borderRadius: 1 }} />}
              {team.team_contextual && <Chip size="small" color="primary" variant="outlined" label={t('Contextual')} sx={{ borderRadius: 1 }} />}
            </>
          )}
          action={(
            <TeamPopover
              team={team}
              managePlayers={() => setManaging(true)}
              onUpdate={() => dispatch(fetchTeam(teamId))}
              onDelete={() => navigate(TEAM_BASE_URL)}
            />
          )}
          stats={(
            <>
              <HeroStat icon={PersonOutlined} label={t('Players')} value={team.team_users_number ?? players.length} color={theme.palette.success.main} />
              <HeroStat icon={HubOutlined} label={t('Simulations')} value={team.team_exercises?.length ?? 0} color={theme.palette.primary.main} />
              <HeroStat icon={AssignmentOutlined} label={t('Scenarios')} value={team.team_scenarios?.length ?? 0} color={theme.palette.secondary.main} />
              <HeroStat icon={TrackChangesOutlined} label={t('Simulation injects')} value={team.team_exercise_injects_number ?? 0} color={theme.palette.warning.main} />
              <HeroStat icon={TrackChangesOutlined} label={t('Expectations')} value={team.team_injects_expectations_number ?? 0} />
              <HeroStat icon={TrackChangesOutlined} label={t('Score')} value={scoreLabel} />
            </>
          )}
        />

        {/* Identity + members side by side: both sections are short, so
            sharing one grid row keeps the overview compact (they stack
            automatically on narrow viewports). */}
        <DetailSections>
          <InformationGrid title={t('Information')}>
            <Field label={t('Description')}>
              <ExpandableMarkdown source={team.team_description ?? ''} limit={300} />
            </Field>
            <Field label={t('Organization')}>
              {team.team_organization
                ? <Link to={`${ORGANIZATION_BASE_URL}/${team.team_organization}`}>{organizationName || team.team_organization}</Link>
                : '-'}
            </Field>
            <Field label={t('Contextual')}>{team.team_contextual ? t('Yes') : t('No')}</Field>
            <Field label={t('Tags')}>
              <ItemTags variant="list" tags={team.team_tags ?? []} />
            </Field>
            <Field label={t('Creation date')}>{fldt(team.team_created_at)}</Field>
            <Field label={t('Update date')}>{fldt(team.team_updated_at)}</Field>
          </InformationGrid>
          <Section title={t('Players')}>
            {players.length === 0
              ? <Empty message={t('No player in this team.')} />
              : (
                  <List disablePadding>
                    {players.map((player) => {
                      const label = [player.user_firstname, player.user_lastname].filter(Boolean).join(' ').trim() || player.user_email;
                      return (
                        <ListItem key={player.user_id} divider disablePadding>
                          <ListItemButton component={Link} to={`${PERSON_BASE_URL}/${player.user_id}`}>
                            <ListItemIcon sx={{ minWidth: 36 }}><PersonOutlined color="primary" /></ListItemIcon>
                            <ListItemText
                              primary={label}
                              secondary={player.user_organization ? organizationsMap[player.user_organization]?.organization_name : undefined}
                            />
                          </ListItemButton>
                        </ListItem>
                      );
                    })}
                  </List>
                )}
          </Section>
        </DetailSections>

        <SectionBlock title={t('Injects played')}>
          <InjectResultList
            fetchInjects={fetchInjectsPlayed}
            goTo={injectId => `/admin/atomic_testings/${injectId}`}
            queryableHelpers={injectsHelpers}
            searchPaginationInput={injectsInput}
            contextId={teamId}
          />
        </SectionBlock>

        <SectionBlock title={t('Findings')}>
          <FindingList
            filterLocalStorageKey="team-findings"
            searchDistinctFindings={(input: SearchPaginationInput) => searchDistinctFindings(withFilter(input, 'finding_teams', [teamId]))}
            contextId={teamId}
          />
        </SectionBlock>

        <Drawer
          open={managing}
          keepMounted={false}
          anchor="right"
          sx={{ zIndex: 1202 }}
          classes={{ paper: classes.drawerPaper }}
          onClose={() => {
            setManaging(false);
            dispatch(fetchTeam(teamId));
            dispatch(fetchTeamPlayers(teamId));
          }}
          elevation={1}
        >
          {managing && (
            <TeamPlayers
              teamId={teamId}
              handleClose={() => {
                setManaging(false);
                dispatch(fetchTeam(teamId));
                dispatch(fetchTeamPlayers(teamId));
              }}
              canManage={ability.can(ACTIONS.MANAGE, SUBJECTS.TEAMS_AND_PLAYERS)}
            />
          )}
        </Drawer>
      </Box>
    </TeamContext.Provider>
  );
};

export default TeamDetail;
