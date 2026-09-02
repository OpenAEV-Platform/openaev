import { AssignmentOutlined, GroupsOutlined, HelpOutlineOutlined, HubOutlined, KeyboardArrowRight, PersonOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { Box, Chip, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, useCallback, useContext, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { searchDistinctFindings } from '../../../../actions/findings/finding-actions';
import { type OrganizationHelper, type UserHelper } from '../../../../actions/helper';
import { fetchOrganizations } from '../../../../actions/Organization';
import { searchPlayers } from '../../../../actions/players/player-actions';
import { type TagHelper } from '../../../../actions/tags/tag-helper';
import { fetchTeam, fetchTeamPlayers, searchInjectsForTeam, searchTeams, updateTeamPlayers } from '../../../../actions/teams/team-actions';
import { type TeamsHelper } from '../../../../actions/teams/team-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { DetailHero, Field, HeroStat, InformationGrid, SectionBlock } from '../../../../components/common/detail/EntityDetailCommon';
import { generateFilterId } from '../../../../components/common/queryable/filter/FilterUtils';
import { initSorting, type Page } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../components/common/SortHeadersList';
import Empty from '../../../../components/Empty';
import ExpandableMarkdown from '../../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import Loader from '../../../../components/Loader';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { ORGANIZATION_BASE_URL, PERSON_BASE_URL, TEAM_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import { type Filter, type InjectResultOutput, type PlayerOutput, type SearchPaginationInput, type Team, type TeamOutput, type User } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import InjectResultList from '../../atomic_testings/InjectResultList';
import injectResultDetailPath from '../../atomic_testings/injectResultUtils';
import { TeamContext, type TeamContextType } from '../../common/Context';
import TeamPlayers from '../../components/teams/TeamPlayers';
import TeamPopover from '../../components/teams/TeamPopover';
import FindingList from '../../findings/FindingList';
import EntityReportsPanel from '../../reporting/EntityReportsPanel';

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
  const countLabel = (count: number, singular: string, plural: string) => t(count === 1 ? singular : plural);
  const bodyItemsStyles = useBodyItemsStyles();
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

  // Persons: server-paginated players search scoped to this team (same logic
  // as the injects played and findings sections below).
  const [persons, setPersons] = useState<PlayerOutput[]>([]);
  const [personsLoading, setPersonsLoading] = useState(true);
  // Bumped whenever the team membership or a player actually changes (add,
  // remove, update, delete) so the list refreshes reactively - never on a mere
  // open/close of the manage-players drawer.
  const [personsReload, setPersonsReload] = useState(0);

  const teamContext: TeamContextType = useMemo(() => ({
    async onAddUsersTeam(id: Team['team_id'], userIds: string[]): Promise<void> {
      await dispatch(updateTeamPlayers(id, { team_users: [...(team?.team_users ?? []), ...userIds] }));
      setPersonsReload(count => count + 1);
    },
    async onRemoveUsersTeam(id: Team['team_id'], userIds: string[]): Promise<void> {
      await dispatch(updateTeamPlayers(id, { team_users: (team?.team_users ?? []).filter((u: string) => !userIds.includes(u)) }));
      setPersonsReload(count => count + 1);
    },
    searchTeams(input: SearchPaginationInput): Promise<{ data: Page<TeamOutput> }> {
      return searchTeams(input) as Promise<{ data: Page<TeamOutput> }>;
    },
  }), [dispatch, team]);

  const { queryableHelpers: injectsHelpers, searchPaginationInput: injectsInput } = useQueryableWithLocalStorage(
    'team-injects',
    buildSearchPagination({ sorts: initSorting('inject_updated_at', 'DESC') }),
  );
  // Injects played: every inject (atomic testing or simulation inject) that
  // concerns this team - targeted directly or evidenced by the table-top
  // expectations persisted at execution time. Same scope as the expectation
  // counters in the hero, so the list and the counters stay consistent.
  const fetchInjectsPlayed = useCallback(
    (input: SearchPaginationInput): Promise<{ data: Page<InjectResultOutput> }> =>
      searchInjectsForTeam(teamId, input) as Promise<{ data: Page<InjectResultOutput> }>,
    [teamId],
  );

  const { queryableHelpers: personsHelpers, searchPaginationInput: personsInput } = useQueryableWithLocalStorage(
    'team-persons',
    buildSearchPagination({ sorts: initSorting('user_email') }),
  );
  const fetchTeamPersons = useCallback(
    (input: SearchPaginationInput): Promise<{ data: Page<PlayerOutput> }> =>
      searchPlayers(withFilter(input, 'user_teams', [teamId])) as Promise<{ data: Page<PlayerOutput> }>,
    [teamId],
  );

  const personsInlineStyles: Record<string, CSSProperties> = {
    user_email: { width: '25%' },
    user_firstname: { width: '15%' },
    user_lastname: { width: '15%' },
    user_organization: { width: '20%' },
    user_tags: { width: '25%' },
  };

  const personsHeaders: Header[] = useMemo(() => [
    {
      field: 'user_email',
      label: 'Email address',
      isSortable: true,
      value: (player: PlayerOutput) => player.user_email,
    },
    {
      field: 'user_firstname',
      label: 'Firstname',
      isSortable: true,
      value: (player: PlayerOutput) => player.user_firstname || '-',
    },
    {
      field: 'user_lastname',
      label: 'Lastname',
      isSortable: true,
      value: (player: PlayerOutput) => player.user_lastname || '-',
    },
    {
      field: 'user_organization',
      label: 'Organization',
      isSortable: false,
      value: (player: PlayerOutput) => (player.user_organization ? organizationsMap[player.user_organization]?.organization_name : '-'),
    },
    {
      field: 'user_tags',
      label: 'Tags',
      isSortable: true,
      value: (player: PlayerOutput) => <ItemTags variant="list" tags={player.user_tags} />,
    },
  ], [organizationsMap]);

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
              {organizationName && <Chip size="small" variant="outlined" label={organizationName} sx={{ borderRadius: 1 }} />}
              {team.team_contextual && <Chip size="small" color="primary" variant="outlined" label={t('Contextual')} sx={{ borderRadius: 1 }} />}
            </>
          )}
          action={(
            <>
              {/* Entity-scoped reports - self-hides without the reporting
                  access capability. */}
              <EntityReportsPanel
                contextType="TEAM"
                contextId={teamId}
                entityName={team.team_name}
              />
              <TeamPopover
                team={team}
                managePlayers={() => setManaging(true)}
                onUpdate={() => dispatch(fetchTeam(teamId))}
                onDelete={() => navigate(TEAM_BASE_URL)}
              />
            </>
          )}
          stats={(
            <>
              <HeroStat icon={PersonOutlined} label={countLabel(team.team_users_number ?? players.length, 'Player', 'Players')} value={team.team_users_number ?? players.length} color={theme.palette.success.main} />
              <HeroStat icon={HubOutlined} label={countLabel(team.team_exercises?.length ?? 0, 'Simulation', 'Simulations')} value={team.team_exercises?.length ?? 0} color={theme.palette.primary.main} />
              <HeroStat icon={AssignmentOutlined} label={countLabel(team.team_scenarios?.length ?? 0, 'Scenario', 'Scenarios')} value={team.team_scenarios?.length ?? 0} color={theme.palette.secondary.main} />
              <HeroStat icon={TrackChangesOutlined} label={countLabel(team.team_exercise_injects_number ?? 0, 'Simulation inject', 'Simulation injects')} value={team.team_exercise_injects_number ?? 0} color={theme.palette.warning.main} />
              <HeroStat icon={TrackChangesOutlined} label={countLabel(team.team_injects_expectations_number ?? 0, 'Expectation', 'Expectations')} value={team.team_injects_expectations_number ?? 0} />
              <HeroStat icon={TrackChangesOutlined} label={t('Score')} value={scoreLabel} />
            </>
          )}
        />

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

        <SectionBlock title={t('Persons')}>
          <PaginationComponentV2
            fetch={fetchTeamPersons}
            searchPaginationInput={personsInput}
            setContent={setPersons}
            setLoading={setPersonsLoading}
            entityPrefix="user"
            availableFilterNames={['user_email', 'user_firstname', 'user_lastname', 'user_organization', 'user_tags']}
            queryableHelpers={personsHelpers}
            reloadContentCount={personsReload}
          />
          <List>
            <ListItem
              divider={false}
              style={{
                paddingTop: 0,
                textTransform: 'uppercase',
              }}
              secondaryAction={<>&nbsp;</>}
            >
              <ListItemIcon />
              <ListItemText
                primary={(
                  <SortHeadersComponentV2
                    headers={personsHeaders}
                    inlineStylesHeaders={personsInlineStyles}
                    sortHelpers={personsHelpers.sortHelpers}
                  />
                )}
              />
            </ListItem>
            {personsLoading
              ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={personsHeaders} headerStyles={personsInlineStyles} />
              : persons.map(player => (
                  <ListItem
                    key={player.user_id}
                    divider
                    disablePadding
                    secondaryAction={<KeyboardArrowRight color="action" />}
                  >
                    <ListItemButton
                      style={{ height: 50 }}
                      component={Link}
                      to={`${PERSON_BASE_URL}/${player.user_id}`}
                    >
                      <ListItemIcon>
                        <PersonOutlined color="primary" />
                      </ListItemIcon>
                      <ListItemText
                        primary={(
                          <div style={bodyItemsStyles.bodyItems}>
                            {personsHeaders.map(header => (
                              <div
                                key={header.field}
                                style={{
                                  ...bodyItemsStyles.bodyItem,
                                  ...personsInlineStyles[header.field],
                                }}
                              >
                                {header.value?.(player)}
                              </div>
                            ))}
                          </div>
                        )}
                      />
                    </ListItemButton>
                  </ListItem>
                ))}
            {!personsLoading && persons.length === 0 && <Empty message={t('No player in this team.')} />}
          </List>
        </SectionBlock>

        <SectionBlock title={t('Findings')}>
          <FindingList
            filterLocalStorageKey="team-findings"
            searchDistinctFindings={(input: SearchPaginationInput) => searchDistinctFindings(withFilter(input, 'finding_teams', [teamId]))}
            contextId={teamId}
          />
        </SectionBlock>

        <SectionBlock title={t('Injects played')}>
          <InjectResultList
            fetchInjects={fetchInjectsPlayed}
            goTo={injectResultDetailPath}
            queryableHelpers={injectsHelpers}
            searchPaginationInput={injectsInput}
            contextId={teamId}
          />
        </SectionBlock>

        {managing && (
          <TeamPlayers
            teamId={teamId}
            // Closing without touching anything must not reload the page lists:
            // every mutation already refreshes reactively (context handlers +
            // onPlayersChange below).
            handleClose={() => setManaging(false)}
            onPlayersChange={() => setPersonsReload(count => count + 1)}
            canManage={ability.can(ACTIONS.MANAGE, SUBJECTS.TEAMS_AND_PLAYERS)}
          />
        )}
      </Box>
    </TeamContext.Provider>
  );
};

export default TeamDetail;
