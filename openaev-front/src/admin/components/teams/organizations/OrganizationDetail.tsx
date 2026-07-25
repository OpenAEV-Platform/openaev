import { DomainOutlined, GroupsOutlined, HelpOutlineOutlined, KeyboardArrowRight, PersonOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { Box, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Binoculars } from 'mdi-material-ui';
import { type CSSProperties, useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { searchAtomicTestings } from '../../../../actions/atomic_testings/atomic-testing-actions';
import { searchDistinctFindings } from '../../../../actions/findings/finding-actions';
import type { UserHelper } from '../../../../actions/helper';
import { fetchOrganization } from '../../../../actions/organizations/organization-actions';
import { searchPlayers } from '../../../../actions/players/player-actions';
import { type TagHelper } from '../../../../actions/tags/tag-helper';
import { fetchTeams } from '../../../../actions/teams/team-actions';
import { type TeamsHelper } from '../../../../actions/teams/team-helper';
import { fetchPlayers } from '../../../../actions/users/User';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { DetailHero, Field, HeroStat, InformationGrid, SectionLabel } from '../../../../components/common/detail/EntityDetailCommon';
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
import { ORGANIZATION_BASE_URL, PERSON_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import { type Filter, type Organization, type PlayerOutput, type SearchPaginationInput, type Team, type User } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import useSearchTotal from '../../../../utils/hooks/useSearchTotal';
import OrganizationPopover from './OrganizationPopover';

// Scoped `contains` filter for the hero count probes.
const contains = (key: string, values: string[]): Filter => ({
  id: generateFilterId(),
  key,
  mode: 'or',
  operator: 'contains',
  values,
});

// Business-side organization detail (left menu > Organizations). Deliberately
// separated from the admin Settings > Security > Organizations detail: no
// security right menu, business breadcrumbs, and members link to the Persons
// page instead of the admin user administration.
const OrganizationDetail = () => {
  const { t, fldt } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const bodyItemsStyles = useBodyItemsStyles();
  const { organizationId } = useParams() as { organizationId: string };

  const [organization, setOrganization] = useState<Organization | null>(null);
  useEffect(() => {
    fetchOrganization(organizationId).then(response => setOrganization(response.data as Organization));
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
          filters: [...(input.filterGroup?.filters ?? []), contains('user_organization', [organizationId])],
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
      isSortable: true,
      value: (player: PlayerOutput) => <ItemTags variant="list" tags={player.user_tags} />,
    },
  ], []);
  const teams = useMemo(
    () => (Object.values(teamsMap) as Team[]).filter(team => team.team_organization === organizationId),
    [teamsMap, organizationId],
  );
  const teamIds = useMemo(() => teams.map(team => team.team_id), [teams]);
  const memberIds = useMemo(() => members.map(member => member.user_id), [members]);

  // Headline hero counts, scoped through the organization's people: injects
  // played by its teams, findings linked to its teams or members. Empty
  // `contains` filters would match everything, so empty scopes short-circuit to 0.
  const injectsTotal = useSearchTotal(useCallback(
    (input: SearchPaginationInput) => (teamIds.length === 0
      ? Promise.resolve({ data: { totalElements: 0 } })
      : searchAtomicTestings({
          ...input,
          filterGroup: {
            mode: 'and',
            filters: [contains('inject_teams', teamIds)],
          },
        })),
    [teamIds],
  ));
  const findingsTotal = useSearchTotal(useCallback(
    (input: SearchPaginationInput) => {
      const scopes: Filter[] = [
        ...(teamIds.length > 0 ? [contains('finding_teams', teamIds)] : []),
        ...(memberIds.length > 0 ? [contains('finding_users', memberIds)] : []),
      ];
      if (scopes.length === 0) {
        return Promise.resolve({ data: { totalElements: 0 } });
      }
      return searchDistinctFindings({
        ...input,
        filterGroup: {
          mode: 'or',
          filters: scopes,
        },
      });
    },
    [teamIds, memberIds],
  ));

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

        {/* Flat list (no surrounding Paper): metadata above, a single
            full-width, server-paginated and searchable members list below -
            the standard single-list layout on detail pages. */}
        <div>
          <SectionLabel>{t('Members')}</SectionLabel>
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
        </div>
      </Box>
    </>
  );
};

export default OrganizationDetail;
