import { GroupsOutlined, KeyboardArrowRight, PermIdentityOutlined, SecurityOutlined } from '@mui/icons-material';
import { Box, Chip, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Skeleton } from '@mui/material';
import { type CSSProperties, useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import type { UserHelper } from '../../../../actions/helper';
import {
  fetchPlatformGroupById,
  fetchPlatformGroupRoleIds,
  fetchPlatformGroupUserIds,
} from '../../../../actions/platform/platform-group/platform-group-action';
import { findPlatformRoles } from '../../../../actions/platform/platform-role/platform-role-action';
import { findPlatformUsers } from '../../../../actions/platform/users/platform-user-action';
import { fetchAllRoles, fetchGroupById } from '../../../../actions/security/securityDetail-actions';
import { fetchUsers } from '../../../../actions/users/User';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { DetailHero, DetailSections, Field, InformationGrid, Section, SectionLabel } from '../../../../components/common/detail/EntityDetailCommon';
import { initSorting, type Page } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useQueryable } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../components/common/SortHeadersList';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { GROUP_BASE_URL, ROLE_BASE_URL, USER_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import { type Group, type PlatformGroupOutput, type PlatformRoleOutput, type RoleOutput, type SearchPaginationInput, type UserOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import PlatformGroupPopover from '../groups/platform_groups/PlatformGroupPopover';
import GroupPopover from '../groups/tenant_groups/GroupPopover';
import SecurityMenu from '../SecurityMenu';
import useSecurityScope from '../useSecurityScope';
import { SecurityDetailNotFound } from './SecurityDetailNotFound';

interface RelatedItem {
  id: string;
  name: string;
}

// Unified member row shared by the tenant scope (Redux users map) and the
// platform scope (UserOutput list), so the members list renders one way.
interface MemberRow {
  user_id: string;
  user_email: string;
  user_firstname?: string;
  user_lastname?: string;
  user_admin?: boolean;
}

const memberInlineStyles: Record<string, CSSProperties> = {
  user_email: { width: '35%' },
  user_firstname: { width: '22%' },
  user_lastname: { width: '22%' },
  user_admin: { width: '21%' },
};

const GroupDetail = () => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const bodyItemsStyles = useBodyItemsStyles();
  const { groupId } = useParams() as { groupId: string };
  const { scope } = useSecurityScope();
  const isPlatform = scope === 'platform';

  const [group, setGroup] = useState<Group | null>(null);
  const [platformGroup, setPlatformGroup] = useState<PlatformGroupOutput | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [roles, setRoles] = useState<RoleOutput[]>([]);
  const [platformMembers, setPlatformMembers] = useState<UserOutput[]>([]);
  const [platformRoles, setPlatformRoles] = useState<PlatformRoleOutput[]>([]);
  // In platform scope the members and roles resolve through two chained calls
  // (ids first, details later): gate the empty states on these flags so the
  // lists show skeletons instead of flashing "No member/role" while loading.
  const [platformMembersReady, setPlatformMembersReady] = useState(false);
  const [platformRolesReady, setPlatformRolesReady] = useState(false);

  const { usersMap } = useHelper((helper: UserHelper) => ({ usersMap: helper.getUsersMap() }));
  useDataLoader(() => {
    if (!isPlatform) {
      dispatch(fetchUsers());
    }
  });

  useEffect(() => {
    setGroup(null);
    setPlatformGroup(null);
    setNotFound(false);
    setPlatformMembersReady(!isPlatform);
    setPlatformRolesReady(!isPlatform);
    if (isPlatform) {
      fetchPlatformGroupById(groupId)
        .then(response => setPlatformGroup(response.data as PlatformGroupOutput))
        .catch(() => setNotFound(true));
      fetchPlatformGroupUserIds(groupId)
        .then((response) => {
          const ids = (response.data ?? []) as string[];
          if (ids.length === 0) {
            setPlatformMembers([]);
            return undefined;
          }
          return findPlatformUsers(ids).then(usersResponse => setPlatformMembers((usersResponse.data ?? []) as UserOutput[]));
        })
        .catch(() => {})
        .finally(() => setPlatformMembersReady(true));
      fetchPlatformGroupRoleIds(groupId)
        .then((response) => {
          const ids = (response.data ?? []) as string[];
          if (ids.length === 0) {
            setPlatformRoles([]);
            return undefined;
          }
          return findPlatformRoles(ids).then(rolesResponse => setPlatformRoles((rolesResponse.data ?? []) as PlatformRoleOutput[]));
        })
        .catch(() => {})
        .finally(() => setPlatformRolesReady(true));
    } else {
      fetchGroupById(groupId)
        .then(response => setGroup(response.data as Group))
        .catch(() => setNotFound(true));
      fetchAllRoles().then(response => setRoles(response.data as RoleOutput[]));
    }
  }, [groupId, isPlatform]);

  const rolesMap = useMemo(
    () => Object.fromEntries(roles.map(role => [role.role_id, role.role_name])),
    [roles],
  );

  const members: MemberRow[] = useMemo(() => {
    if (isPlatform) {
      return platformMembers.map(member => ({
        user_id: member.user_id,
        user_email: member.user_email,
        user_firstname: member.user_firstname,
        user_lastname: member.user_lastname,
        user_admin: member.user_admin,
      }));
    }
    return (group?.group_users ?? []).map((memberId) => {
      const user = usersMap[memberId];
      return {
        user_id: memberId,
        user_email: user?.user_email ?? memberId,
        user_firstname: user?.user_firstname,
        user_lastname: user?.user_lastname,
        user_admin: user?.user_admin,
      };
    });
  }, [isPlatform, platformMembers, group, usersMap]);

  // Members list: standard toolbar (search + sortable columns + pagination)
  // paginated client-side - the membership is already fully loaded for both
  // scopes and users are not server-searchable by group.
  const [memberRows, setMemberRows] = useState<MemberRow[]>([]);
  const [membersPageLoading, setMembersPageLoading] = useState(true);
  const { queryableHelpers, searchPaginationInput } = useQueryable(buildSearchPagination({ sorts: initSorting('user_email') }));
  const membersLoading = membersPageLoading || !platformMembersReady;

  // The members source resolves asynchronously (group ids first, user details
  // later), so re-run the client-side fetch whenever it actually changes -
  // the length alone would miss the user-details arrival.
  const [membersVersion, setMembersVersion] = useState(0);
  useEffect(() => {
    setMembersVersion(version => version + 1);
  }, [members]);

  const fetchMembers = useCallback((input: SearchPaginationInput): Promise<{ data: Page<MemberRow> }> => {
    const search = (input.textSearch ?? '').trim().toLowerCase();
    const filtered = search
      ? members.filter(member => [member.user_email, member.user_firstname, member.user_lastname]
          .some(value => value?.toLowerCase().includes(search)))
      : members;
    const sort = input.sorts?.[0];
    const property = (sort?.property ?? 'user_email') as keyof MemberRow;
    const direction = sort?.direction === 'DESC' ? -1 : 1;
    const sorted = [...filtered].sort((a, b) => direction * String(a[property] ?? '').localeCompare(String(b[property] ?? '')));
    const page = input.page ?? 0;
    const size = input.size ?? 20;
    return Promise.resolve({
      data: {
        content: sorted.slice(page * size, (page + 1) * size),
        totalElements: sorted.length,
        totalPages: size > 0 ? Math.ceil(sorted.length / size) : 0,
        pageable: { pageNumber: page },
      } as Page<MemberRow>,
    });
  }, [members]);

  const memberHeaders: Header[] = useMemo(() => [
    {
      field: 'user_email',
      label: 'Email address',
      isSortable: true,
      value: (member: MemberRow) => member.user_email,
    },
    {
      field: 'user_firstname',
      label: 'Firstname',
      isSortable: true,
      value: (member: MemberRow) => member.user_firstname || '-',
    },
    {
      field: 'user_lastname',
      label: 'Lastname',
      isSortable: true,
      value: (member: MemberRow) => member.user_lastname || '-',
    },
    {
      field: 'user_admin',
      label: 'Administrator',
      isSortable: true,
      value: (member: MemberRow) => (member.user_admin
        ? <Chip size="small" color="primary" variant="outlined" label={t('Administrator')} sx={{ borderRadius: 1 }} />
        : <>-</>),
    },
  ], [t]);

  const groupsLink = isPlatform ? `${GROUP_BASE_URL}?scope=platform` : GROUP_BASE_URL;
  const scopeSuffix = isPlatform ? '?scope=platform' : '';

  if (notFound) {
    return <SecurityDetailNotFound>{t('This group could not be found.')}</SecurityDetailNotFound>;
  }

  if (isPlatform ? !platformGroup : !group) {
    return <Loader />;
  }

  const title = isPlatform ? platformGroup!.platform_group_name : group!.group_name;
  const description = isPlatform ? platformGroup!.platform_group_description : group!.group_description;
  const defaultUserAssign = isPlatform ? platformGroup!.group_default_user_assign : group!.group_default_user_assign;

  const roleItems: RelatedItem[] = isPlatform
    ? platformRoles.map(role => ({
        id: role.platform_role_id,
        name: role.platform_role_name,
      }))
    : (group!.group_roles ?? []).map(roleId => ({
        id: roleId,
        name: rolesMap[roleId] ?? roleId,
      }));

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="object"
          elements={[
            { label: t(SETTINGS_LABEL) },
            { label: t('Security') },
            {
              label: t('Groups'),
              link: groupsLink,
            },
            {
              label: title,
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
            icon={GroupsOutlined}
            title={title}
            chips={(
              <>
                <Chip size="small" variant="outlined" label={t('{count} members', { count: members.length })} sx={{ borderRadius: 1 }} />
                <Chip size="small" variant="outlined" label={t('{count} roles', { count: roleItems.length })} sx={{ borderRadius: 1 }} />
              </>
            )}
            action={isPlatform
              ? (
                  <PlatformGroupPopover
                    platformGroup={platformGroup!}
                    actions={['Update', 'Manage users', 'Manage roles', 'Delete']}
                    onUpdate={(updated: PlatformGroupOutput) => setPlatformGroup(updated)}
                    onDelete={() => navigate(groupsLink)}
                  />
                )
              : (
                  <GroupPopover
                    group={group!}
                    groupUsersIds={group!.group_users ?? []}
                    groupRolesIds={group!.group_roles ?? []}
                    onUpdate={(updated: Group) => setGroup(updated)}
                    onDelete={() => navigate(groupsLink)}
                  />
                )}
          />

          {/* Identity + roles share one 50/50 row; the members list gets the
              full width below as a standard paginated list. */}
          <DetailSections>
            <InformationGrid title={t('Information')}>
              <Field label={t('Description')}>{description || '-'}</Field>
              <Field label={t('Default user assignment')}>{defaultUserAssign ? t('Yes') : t('No')}</Field>
            </InformationGrid>
            <Section title={t('Roles')}>
              {!platformRolesReady && (
                <List disablePadding>
                  {[...Array(2)].map((_, index) => (
                    <ListItem key={index} divider disablePadding>
                      <ListItemButton disabled sx={{ opacity: 1 }}>
                        <ListItemIcon sx={{ minWidth: 36 }}><SecurityOutlined color="disabled" /></ListItemIcon>
                        <ListItemText primary={<Skeleton variant="text" width="40%" />} />
                      </ListItemButton>
                    </ListItem>
                  ))}
                </List>
              )}
              {platformRolesReady && roleItems.length === 0 && (
                <Empty message={t('No role assigned to this group.')} />
              )}
              {platformRolesReady && roleItems.length > 0 && (
                <List disablePadding>
                  {roleItems.map(role => (
                    <ListItem key={role.id} divider disablePadding>
                      <ListItemButton component={Link} to={`${ROLE_BASE_URL}/${role.id}${scopeSuffix}`}>
                        <ListItemIcon sx={{ minWidth: 36 }}><SecurityOutlined color="primary" /></ListItemIcon>
                        <ListItemText primary={role.name} />
                      </ListItemButton>
                    </ListItem>
                  ))}
                </List>
              )}
            </Section>
          </DetailSections>

          <div>
            <SectionLabel>{t('Members')}</SectionLabel>
            <PaginationComponentV2
              fetch={fetchMembers}
              searchPaginationInput={searchPaginationInput}
              setContent={setMemberRows}
              setLoading={setMembersPageLoading}
              disableFilters
              queryableHelpers={queryableHelpers}
              reloadContentCount={membersVersion}
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
                      headers={memberHeaders}
                      inlineStylesHeaders={memberInlineStyles}
                      sortHelpers={queryableHelpers.sortHelpers}
                    />
                  )}
                />
              </ListItem>
              {membersLoading && (
                <PaginatedListLoader Icon={PermIdentityOutlined} headers={memberHeaders} headerStyles={memberInlineStyles} number={5} />
              )}
              {!membersLoading && memberRows.map(member => (
                <ListItem
                  key={member.user_id}
                  divider
                  disablePadding
                  secondaryAction={<KeyboardArrowRight color="action" />}
                >
                  <ListItemButton
                    style={{ height: 50 }}
                    component={Link}
                    to={`${USER_BASE_URL}/${member.user_id}${scopeSuffix}`}
                  >
                    <ListItemIcon>
                      <PermIdentityOutlined color="primary" />
                    </ListItemIcon>
                    <ListItemText
                      primary={(
                        <div style={bodyItemsStyles.bodyItems}>
                          {memberHeaders.map(header => (
                            <div
                              key={header.field}
                              style={{
                                ...bodyItemsStyles.bodyItem,
                                ...memberInlineStyles[header.field],
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
              {!membersLoading && memberRows.length === 0 && <Empty message={t('No member in this group.')} />}
            </List>
          </div>
        </Box>
      </div>
      <SecurityMenu />
    </div>
  );
};

export default GroupDetail;
