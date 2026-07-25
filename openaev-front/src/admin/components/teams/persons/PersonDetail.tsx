import { GroupsOutlined, PersonOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { Box, Chip, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Binoculars } from 'mdi-material-ui';
import { useCallback, useMemo } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { searchAtomicTestings } from '../../../../actions/atomic_testings/atomic-testing-actions';
import { searchDistinctFindings } from '../../../../actions/findings/finding-actions';
import { type OrganizationHelper, type UserHelper } from '../../../../actions/helper';
import { fetchOrganizations } from '../../../../actions/Organization';
import { type TagHelper } from '../../../../actions/tags/tag-helper';
import { fetchTeams } from '../../../../actions/teams/team-actions';
import { type TeamsHelper } from '../../../../actions/teams/team-helper';
import { fetchPlayers } from '../../../../actions/users/User';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { DetailHero, DetailSections, Field, HeroStat, InformationGrid, Section, SectionBlock } from '../../../../components/common/detail/EntityDetailCommon';
import { generateFilterId } from '../../../../components/common/queryable/filter/FilterUtils';
import { initSorting, type Page } from '../../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import Loader from '../../../../components/Loader';
import { ORGANIZATION_BASE_URL, PERSON_BASE_URL, TEAM_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import {
  type Filter,
  type InjectResultOutput,
  type SearchPaginationInput,
  type Team,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import useSearchTotal from '../../../../utils/hooks/useSearchTotal';
import { emptyFilled } from '../../../../utils/String';
import InjectResultList from '../../atomic_testings/InjectResultList';
import FindingList from '../../findings/FindingList';
import PlayerPopover from '../players/PlayerPopover';

// Adds a scoped filter to a search input without mutating the caller's group.
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

// Business-side person (player) overview: identity, organization + teams
// pivots, the injects the person played (through their teams) and the findings
// linked to them. Compact and grid-based, mirroring the asset overview.
const PersonDetail = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { userId } = useParams() as { userId: string };

  const { usersMap, teamsMap, organizationsMap } = useHelper((helper: UserHelper & TeamsHelper & OrganizationHelper & TagHelper) => ({
    usersMap: helper.getUsersMap(),
    teamsMap: helper.getTeamsMap(),
    organizationsMap: helper.getOrganizationsMap(),
  }));

  useDataLoader(() => {
    dispatch(fetchPlayers());
    dispatch(fetchTeams());
    dispatch(fetchOrganizations());
  });

  const user = usersMap[userId];

  // Derive team membership from the teams list (team_users) rather than
  // user.user_teams: this page loads the person from GET /api/players, whose
  // RawPlayer projection does not carry user_teams, so that field is undefined
  // after a reload and every team-based stat would collapse to 0.
  const teams = useMemo(
    () => (Object.values(teamsMap) as Team[])
      .filter((team: Team) => (team.team_users ?? []).includes(userId))
      .sort((a: Team, b: Team) => (a.team_name ?? '').localeCompare(b.team_name ?? '')),
    [teamsMap, userId],
  );
  const teamIds = useMemo(() => teams.map((team: Team) => team.team_id), [teams]);

  const { queryableHelpers: injectsHelpers, searchPaginationInput: injectsInput } = useQueryableWithLocalStorage(
    'person-injects',
    buildSearchPagination({ sorts: initSorting('inject_updated_at', 'DESC') }),
  );
  const fetchInjectsPlayed = useCallback(
    (input: SearchPaginationInput): Promise<{ data: Page<InjectResultOutput> }> =>
      searchAtomicTestings(withFilter(input, 'inject_teams', teamIds)) as Promise<{ data: Page<InjectResultOutput> }>,
    [teamIds],
  );

  // Headline hero counts: size-1 probes of the same searches feeding the lists
  // below. A person with no team cannot have played injects (an empty
  // inject_teams filter would match everything, so short-circuit to 0).
  const injectsTotal = useSearchTotal(useCallback(
    (input: SearchPaginationInput) => (teamIds.length === 0
      ? Promise.resolve({ data: { totalElements: 0 } })
      : searchAtomicTestings(withFilter(input, 'inject_teams', teamIds))),
    [teamIds],
  ));
  const findingsTotal = useSearchTotal(useCallback(
    (input: SearchPaginationInput) => searchDistinctFindings(withFilter(input, 'finding_users', [userId])),
    [userId],
  ));

  if (!user) {
    return <Loader />;
  }

  const fullName = [user.user_firstname, user.user_lastname].filter(Boolean).join(' ').trim();
  const displayName = fullName || user.user_email;
  const organizationName = user.user_organization ? organizationsMap[user.user_organization]?.organization_name : undefined;

  return (
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
            label: t('Persons'),
            link: PERSON_BASE_URL,
          },
          {
            label: displayName,
            current: true,
          },
        ]}
      />

      <DetailHero
        icon={PersonOutlined}
        title={displayName}
        chips={(
          <>
            {organizationName && <Chip size="small" variant="outlined" label={organizationName} sx={{ borderRadius: 1 }} />}
            {user.user_admin && <Chip size="small" color="primary" variant="outlined" label={t('Administrator')} sx={{ borderRadius: 1 }} />}
          </>
        )}
        action={(
          <PlayerPopover
            user={user}
            onDelete={() => navigate(PERSON_BASE_URL)}
          />
        )}
        stats={(
          <>
            <HeroStat icon={GroupsOutlined} label={t('Teams')} value={teams.length} color={theme.palette.success.main} />
            <HeroStat icon={Binoculars} label={t('Findings')} value={findingsTotal ?? '-'} color={theme.palette.primary.main} />
            <HeroStat icon={TrackChangesOutlined} label={t('Injects played')} value={injectsTotal ?? '-'} color={theme.palette.warning.main} />
          </>
        )}
      />

      {/* Identity + teams side by side: both sections are short, so sharing
          one grid row keeps the overview compact (they stack automatically on
          narrow viewports). */}
      <DetailSections>
        <InformationGrid title={t('Information')}>
          <Field label={t('Email address')}>{user.user_email}</Field>
          <Field label={t('First name')}>{emptyFilled(user.user_firstname)}</Field>
          <Field label={t('Last name')}>{emptyFilled(user.user_lastname)}</Field>
          <Field label={t('Organization')}>
            {user.user_organization
              ? <Link to={`${ORGANIZATION_BASE_URL}/${user.user_organization}`}>{organizationName || user.user_organization}</Link>
              : '-'}
          </Field>
          <Field label={t('Phone number')}>{emptyFilled(user.user_phone)}</Field>
          <Field label={t('Country')}>{emptyFilled(user.user_country)}</Field>
          <Field label={t('Tags')}>
            <ItemTags variant="list" tags={user.user_tags ?? []} />
          </Field>
        </InformationGrid>
        <Section title={t('Teams')}>
          {teams.length === 0
            ? <Empty message={t('This person is not part of any team.')} />
            : (
                <List disablePadding>
                  {teams.map((team: Team) => (
                    <ListItem key={team.team_id} divider disablePadding>
                      <ListItemButton component={Link} to={`${TEAM_BASE_URL}/${team.team_id}`}>
                        <ListItemIcon sx={{ minWidth: 36 }}><GroupsOutlined color="primary" /></ListItemIcon>
                        <ListItemText primary={team.team_name} secondary={team.team_organization ? organizationsMap[team.team_organization]?.organization_name : undefined} />
                      </ListItemButton>
                    </ListItem>
                  ))}
                </List>
              )}
        </Section>
      </DetailSections>

      <SectionBlock title={t('Injects played')}>
        {teamIds.length === 0
          ? <Empty message={t('This person is not part of any team, so has no injects played.')} />
          : (
              <InjectResultList
                fetchInjects={fetchInjectsPlayed}
                goTo={injectId => `/admin/atomic_testings/${injectId}`}
                queryableHelpers={injectsHelpers}
                searchPaginationInput={injectsInput}
                contextId={userId}
              />
            )}
      </SectionBlock>

      <SectionBlock title={t('Findings')}>
        <FindingList
          filterLocalStorageKey="person-findings"
          searchDistinctFindings={(input: SearchPaginationInput) => searchDistinctFindings(withFilter(input, 'finding_users', [userId]))}
          contextId={userId}
        />
      </SectionBlock>
    </Box>
  );
};

export default PersonDetail;
