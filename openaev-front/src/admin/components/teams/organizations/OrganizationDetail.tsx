import { DomainOutlined, GroupsOutlined, PermIdentityOutlined, PersonOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { Box, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Binoculars } from 'mdi-material-ui';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { searchAtomicTestings } from '../../../../actions/atomic_testings/atomic-testing-actions';
import { searchDistinctFindings } from '../../../../actions/findings/finding-actions';
import type { UserHelper } from '../../../../actions/helper';
import { fetchOrganization } from '../../../../actions/organizations/organization-actions';
import { type TagHelper } from '../../../../actions/tags/tag-helper';
import { fetchTeams } from '../../../../actions/teams/team-actions';
import { type TeamsHelper } from '../../../../actions/teams/team-helper';
import { fetchUsers } from '../../../../actions/users/User';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { DetailHero, DetailSections, Field, HeroStat, InformationGrid, Section } from '../../../../components/common/detail/EntityDetailCommon';
import { generateFilterId } from '../../../../components/common/queryable/filter/FilterUtils';
import Empty from '../../../../components/Empty';
import ExpandableMarkdown from '../../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import Loader from '../../../../components/Loader';
import { ORGANIZATION_BASE_URL, PERSON_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import { type Filter, type Organization, type SearchPaginationInput, type Team, type User } from '../../../../utils/api-types';
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
  useDataLoader(() => {
    dispatch(fetchUsers());
    dispatch(fetchTeams());
  });

  const members = useMemo(
    () => (Object.values(usersMap) as User[]).filter(user => user.user_organization === organizationId),
    [usersMap, organizationId],
  );
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

        {/* Identity + members side by side: both sections are short, so
            sharing one grid row keeps the overview compact (they stack
            automatically on narrow viewports). */}
        <DetailSections>
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
          <Section title={t('Members')}>
            {members.length === 0
              ? <Empty message={t('No member in this organization.')} />
              : (
                  <List disablePadding>
                    {members.map((member) => {
                      const label = [member.user_firstname, member.user_lastname].filter(Boolean).join(' ').trim()
                        || member.user_email;
                      return (
                        <ListItem key={member.user_id} divider disablePadding>
                          <ListItemButton component={Link} to={`${PERSON_BASE_URL}/${member.user_id}`}>
                            <ListItemIcon sx={{ minWidth: 36 }}><PermIdentityOutlined color="primary" /></ListItemIcon>
                            <ListItemText primary={label} />
                          </ListItemButton>
                        </ListItem>
                      );
                    })}
                  </List>
                )}
          </Section>
        </DetailSections>
      </Box>
    </>
  );
};

export default OrganizationDetail;
