import { DomainOutlined, GroupsOutlined, HelpOutlineOutlined, KeyboardArrowRight, PersonOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { Box, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Binoculars } from 'mdi-material-ui';
import { type CSSProperties, useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { searchDistinctFindings } from '../../../../actions/findings/finding-actions';
import type { UserHelper } from '../../../../actions/helper';
import { fetchOrganization, searchInjectsForOrganization } from '../../../../actions/organizations/organization-actions';
import { searchPlayers } from '../../../../actions/players/player-actions';
import { type TagHelper } from '../../../../actions/tags/tag-helper';
import { fetchTeams } from '../../../../actions/teams/team-actions';
import { type TeamsHelper } from '../../../../actions/teams/team-helper';
import { fetchPlayers } from '../../../../actions/users/User';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { DetailHero, Field, HeroStat, InformationGrid, SectionBlock } from '../../../../components/common/detail/EntityDetailCommon';
import { generateFilterId } from '../../../../components/common/queryable/filter/FilterUtils';
import { initSorting, type Page } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildEmptyPage, buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
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
import { ORGANIZATION_BASE_URL, PERSON_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import { type AggregatedFindingOutput, type Filter, type InjectResultOutput, type Organization, type PlayerOutput, type SearchPaginationInput, type Team, type User } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import useSearchTotal from '../../../../utils/hooks/useSearchTotal';
import InjectResultList from '../../atomic_testings/InjectResultList';
import injectResultDetailPath from '../../atomic_testings/injectResultUtils';
import FindingList from '../../findings/FindingList';
import OrganizationPopover from './OrganizationPopover';

// Scoped `contains` filter for the hero count probes (array-valued keys such
// as inject_teams / finding_teams / finding_users).
const contains = (key: string, values: string[]): Filter => ({
  id: generateFilterId(),
  key,
  mode: 'or',
  operator: 'contains',
  values,
});

// Exact-match filter for scalar keys (e.g. user_organization holds a single
// organization id, so equality is the correct scoping operator).
const equals = (key: string, values: string[]): Filter => ({
  id: generateFilterId(),
  key,
  mode: 'or',
  operator: 'eq',
  values,
});

// Business-side organization detail (left menu > Organizations). Deliberately
// separated from the admin Settings > Security > Organizations detail: no
// security right menu, business breadcrumbs, and members link to the Persons
// page instead of the admin user administration.
const OrganizationDetailContent = () => {
  const { t, fldt } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const bodyItemsStyles = useBodyItemsStyles();
  const { organizationId } = useParams() as { organizationId: string };

  const [organization, setOrganization] = useState<Organization | null>(null);
  useEffect(() => {
    // simpleCall has already notified the user on failure, hence the empty catch.
    fetchOrganization(organizationId)
      .then(response => setOrganization(response.data as Organization))
      .catch(() => {});
  }, [organizationId]);

  const { usersMap, tagsMap, teamsMap } = useHelper((helper: UserHelper & TagHelper & TeamsHelper) => ({
    usersMap: helper.getUsersMap(),
    tagsMap: helper.getTagsMap(),
    teamsMap: helper.getTeamsMap(),
  }));
  // Players (not admin users): the same player-level API as the paginated
  // members list below, so the hero count and the findings scope can never
  // diverge from what the list shows (and no Users-management permission is
  // needed on this business-side page).
  useDataLoader(() => {
    dispatch(fetchPlayers());
    dispatch(fetchTeams());
  });

  const members = useMemo(
    () => (Object.values(usersMap) as User[]).filter(user => user.user_organization === organizationId),
    [usersMap, organizationId],
  );

  // Members: server-paginated players search scoped to this organization (same
  // pattern as the persons list on the team overview).
  const [memberRows, setMemberRows] = useState<PlayerOutput[]>([]);
  const [membersLoading, setMembersLoading] = useState(true);
  const { queryableHelpers: membersHelpers, searchPaginationInput: membersInput } = useQueryableWithLocalStorage(
    'organization-members',
    buildSearchPagination({ sorts: initSorting('user_email') }),
  );
  const fetchOrganizationMembers = useCallback(
    (input: SearchPaginationInput): Promise<{ data: Page<PlayerOutput> }> =>
      searchPlayers({
        ...input,
        filterGroup: {
          mode: input.filterGroup?.mode ?? 'and',
          filters: [...(input.filterGroup?.filters ?? []), equals('user_organization', [organizationId])],
        },
      }) as Promise<{ data: Page<PlayerOutput> }>,
    [organizationId],
  );

  const membersInlineStyles: Record<string, CSSProperties> = {
    user_email: { width: '30%' },
    user_firstname: { width: '20%' },
    user_lastname: { width: '20%' },
    user_tags: { width: '30%' },
  };

  const membersHeaders: Header[] = useMemo(() => [
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
      field: 'user_tags',
      label: 'Tags',
      isSortable: false,
      value: (player: PlayerOutput) => <ItemTags variant="list" tags={player.user_tags} />,
    },
  ], []);
  const teams = useMemo(
    () => (Object.values(teamsMap) as Team[]).filter(team => team.team_organization === organizationId),
    [teamsMap, organizationId],
  );
  const teamIds = useMemo(() => teams.map(team => team.team_id), [teams]);

  // Appends the organization scope to a search input without discarding the
  // user's own list filters (filter groups are flat, so the scope must be a
  // single ANDable filter - hence teams-only, consistent with the team page).
  const withTeamsScope = useCallback(
    (input: SearchPaginationInput, key: string): SearchPaginationInput => ({
      ...input,
      filterGroup: {
        mode: input.filterGroup?.mode ?? 'and',
        filters: [...(input.filterGroup?.filters ?? []), contains(key, teamIds)],
      },
    }),
    [teamIds],
  );

  // Headline hero counts. Injects are resolved server-side (every inject that
  // concerns the organization through its teams, whether targeted directly or
  // evidenced by the expectations persisted at execution time). Findings stay
  // scoped through the organization's teams; an empty `contains` filter would
  // match everything, so an empty team scope short-circuits to 0.
  const injectsTotal = useSearchTotal(useCallback(
    (input: SearchPaginationInput) => searchInjectsForOrganization(organizationId, input),
    [organizationId],
  ));
  const findingsTotal = useSearchTotal(useCallback(
    (input: SearchPaginationInput) => (teamIds.length === 0
      ? Promise.resolve({ data: { totalElements: 0 } })
      : searchDistinctFindings(withTeamsScope(input, 'finding_teams'))),
    [teamIds, withTeamsScope],
  ));

  // Injects played: server-paginated search scoped to the organization.
  const { queryableHelpers: injectsHelpers, searchPaginationInput: injectsInput } = useQueryableWithLocalStorage(
    'organization-injects',
    buildSearchPagination({ sorts: initSorting('inject_updated_at', 'DESC') }),
  );
  const fetchInjectsPlayed = useCallback(
    (input: SearchPaginationInput): Promise<{ data: Page<InjectResultOutput> }> =>
      searchInjectsForOrganization(organizationId, input) as Promise<{ data: Page<InjectResultOutput> }>,
    [organizationId],
  );
  // An organization with no team cannot have findings, and an empty `contains`
  // scope would match everything - so short-circuit to an empty page while
  // keeping the list (search, filters, pagination) rendered.
  const fetchOrganizationFindings = useCallback(
    (input: SearchPaginationInput): Promise<{ data: Page<AggregatedFindingOutput> }> => (teamIds.length === 0
      ? Promise.resolve(buildEmptyPage<AggregatedFindingOutput>(input))
      : searchDistinctFindings(withTeamsScope(input, 'finding_teams'))),
    [teamIds, withTeamsScope],
  );

  if (!organization) {
    return <Loader />;
  }

  return (
    <>
      <Breadcrumbs
        variant="object"
        elements={[
          {
            label: t('Organizations'),
            link: ORGANIZATION_BASE_URL,
          },
          {
            label: organization.organization_name,
            current: true,
          },
        ]}
      />
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
      }}
      >
        <DetailHero
          icon={DomainOutlined}
          title={organization.organization_name}
          action={(
            <OrganizationPopover
              organization={organization}
              tagsMap={tagsMap}
              onUpdate={(updated: Organization) => setOrganization(updated)}
              onDelete={() => navigate(ORGANIZATION_BASE_URL)}
            />
          )}
          stats={(
            <>
              <HeroStat icon={PersonOutlined} label={t('Members')} value={members.length} color={theme.palette.success.main} />
              <HeroStat icon={GroupsOutlined} label={t('Teams')} value={teams.length} color={theme.palette.secondary.main} />
              <HeroStat icon={Binoculars} label={t('Findings')} value={findingsTotal ?? '-'} color={theme.palette.primary.main} />
              <HeroStat icon={TrackChangesOutlined} label={t('Injects played')} value={injectsTotal ?? '-'} color={theme.palette.warning.main} />
            </>
          )}
        />

        <InformationGrid title={t('Information')}>
          <Field label={t('Description')}>
            <ExpandableMarkdown source={organization.organization_description ?? ''} limit={300} />
          </Field>
          <Field label={t('Tags')}>
            <ItemTags variant="list" tags={organization.organization_tags ?? []} />
          </Field>
          <Field label={t('Creation date')}>{fldt(organization.organization_created_at)}</Field>
          <Field label={t('Update date')}>{fldt(organization.organization_updated_at)}</Field>
        </InformationGrid>

        <SectionBlock title={t('Members')}>
          <PaginationComponentV2
            fetch={fetchOrganizationMembers}
            searchPaginationInput={membersInput}
            setContent={setMemberRows}
            setLoading={setMembersLoading}
            entityPrefix="user"
            availableFilterNames={['user_email', 'user_firstname', 'user_lastname', 'user_tags']}
            queryableHelpers={membersHelpers}
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
                    headers={membersHeaders}
                    inlineStylesHeaders={membersInlineStyles}
                    sortHelpers={membersHelpers.sortHelpers}
                  />
                )}
              />
            </ListItem>
            {membersLoading
              ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={membersHeaders} headerStyles={membersInlineStyles} />
              : memberRows.map(member => (
                  <ListItem
                    key={member.user_id}
                    divider
                    disablePadding
                    secondaryAction={<KeyboardArrowRight color="action" />}
                  >
                    <ListItemButton
                      style={{ height: 50 }}
                      component={Link}
                      to={`${PERSON_BASE_URL}/${member.user_id}`}
                    >
                      <ListItemIcon>
                        <PersonOutlined color="primary" />
                      </ListItemIcon>
                      <ListItemText
                        primary={(
                          <div style={bodyItemsStyles.bodyItems}>
                            {membersHeaders.map(header => (
                              <div
                                key={header.field}
                                style={{
                                  ...bodyItemsStyles.bodyItem,
                                  ...membersInlineStyles[header.field],
                                }}
                              >
                                {header.value?.(member)}
                              </div>
                            ))}
                          </div>
                        )}
                      />
                    </ListItemButton>
                  </ListItem>
                ))}
            {!membersLoading && memberRows.length === 0 && <Empty message={t('No member in this organization.')} />}
          </List>
        </SectionBlock>

        <SectionBlock title={t('Findings')}>
          <FindingList
            filterLocalStorageKey="organization-findings"
            searchDistinctFindings={fetchOrganizationFindings}
            contextId={organizationId}
          />
        </SectionBlock>

        <SectionBlock title={t('Injects played')}>
          <InjectResultList
            fetchInjects={fetchInjectsPlayed}
            goTo={injectResultDetailPath}
            queryableHelpers={injectsHelpers}
            searchPaginationInput={injectsInput}
            contextId={organizationId}
          />
        </SectionBlock>
      </Box>
    </>
  );
};

// Remount the whole page when the route param changes (e.g. browser
// back/forward between two organizations): every piece of state - the
// organization itself, the hero counts and the paginated members list (whose
// fetch scope is captured by organizationId) - resets and refetches for the
// new scope instead of showing stale data from the previous organization.
const OrganizationDetail = () => {
  const { organizationId } = useParams() as { organizationId: string };
  return <OrganizationDetailContent key={organizationId} />;
};

export default OrganizationDetail;
