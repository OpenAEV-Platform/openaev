import { DomainOutlined, HelpOutlineOutlined, KeyboardArrowRight, PersonOutlined } from '@mui/icons-material';
import { Box, Chip, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import type { UserHelper } from '../../../../actions/helper';
import { fetchOrganizationById } from '../../../../actions/security/securityDetail-actions';
import { type TagHelper } from '../../../../actions/tags/tag-helper';
import { fetchUsers, searchUsers } from '../../../../actions/users/User';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { DetailHero, Field, InformationGrid, SectionLabel } from '../../../../components/common/detail/EntityDetailCommon';
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
import { SECURITY_ORGANIZATION_BASE_URL, USER_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import { type Filter, type Organization, type SearchPaginationInput, type User, type UserOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import OrganizationPopover from '../organizations/OrganizationPopover';
import SecurityMenu from '../SecurityMenu';

// Exact-match filter for the members search: user_organization holds a single
// organization id, so equality is the correct scoping operator.
const equals = (key: string, values: string[]): Filter => ({
  id: generateFilterId(),
  key,
  mode: 'or',
  operator: 'eq',
  values,
});

const OrganizationDetailContent = () => {
  const { t, fldt } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const bodyItemsStyles = useBodyItemsStyles();
  const { organizationId } = useParams() as { organizationId: string };

  const [organization, setOrganization] = useState<Organization | null>(null);
  useEffect(() => {
    // simpleCall has already notified the user on failure, hence the empty catch.
    fetchOrganizationById(organizationId)
      .then(response => setOrganization(response.data as Organization))
      .catch(() => {});
  }, [organizationId]);

  const { usersMap, tagsMap } = useHelper((helper: UserHelper & TagHelper) => ({
    usersMap: helper.getUsersMap(),
    tagsMap: helper.getTagsMap(),
  }));
  useDataLoader(() => {
    dispatch(fetchUsers());
  });

  const members = useMemo(
    () => (Object.values(usersMap) as User[]).filter(user => user.user_organization === organizationId),
    [usersMap, organizationId],
  );

  // Members: server-paginated users search scoped to this organization (same
  // single-list layout as the business-side organization overview).
  const [memberRows, setMemberRows] = useState<UserOutput[]>([]);
  const [membersLoading, setMembersLoading] = useState(true);
  const { queryableHelpers: membersHelpers, searchPaginationInput: membersInput } = useQueryableWithLocalStorage(
    'security-organization-members',
    buildSearchPagination({ sorts: initSorting('user_email') }),
  );
  const fetchOrganizationMembers = useCallback(
    (input: SearchPaginationInput): Promise<{ data: Page<UserOutput> }> =>
      searchUsers({
        ...input,
        filterGroup: {
          mode: input.filterGroup?.mode ?? 'and',
          filters: [...(input.filterGroup?.filters ?? []), equals('user_organization', [organizationId])],
        },
      }) as Promise<{ data: Page<UserOutput> }>,
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
      value: (user: UserOutput) => user.user_email,
    },
    {
      field: 'user_firstname',
      label: 'Firstname',
      isSortable: true,
      value: (user: UserOutput) => user.user_firstname || '-',
    },
    {
      field: 'user_lastname',
      label: 'Lastname',
      isSortable: true,
      value: (user: UserOutput) => user.user_lastname || '-',
    },
    {
      field: 'user_tags',
      label: 'Tags',
      isSortable: false,
      value: (user: UserOutput) => <ItemTags variant="list" tags={user.user_tags} />,
    },
  ], []);

  if (!organization) {
    return <Loader />;
  }

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="object"
          elements={[
            { label: t(SETTINGS_LABEL) },
            { label: t('Security') },
            {
              label: t('Organizations'),
              link: SECURITY_ORGANIZATION_BASE_URL,
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
            chips={(
              <Chip size="small" variant="outlined" label={t('{count} members', { count: members.length })} sx={{ borderRadius: 1 }} />
            )}
            action={(
              <OrganizationPopover
                organization={organization}
                tagsMap={tagsMap}
                onUpdate={(updated: Organization) => setOrganization(updated)}
                onDelete={() => navigate(SECURITY_ORGANIZATION_BASE_URL)}
              />
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
                        to={`${USER_BASE_URL}/${member.user_id}`}
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
      </div>
      <SecurityMenu />
    </div>
  );
};

// Remount the whole page when the route param changes (e.g. browser
// back/forward between two organizations) so the organization, the members
// count and the paginated members list (whose fetch scope is captured by
// organizationId) all reset and refetch for the new scope.
const OrganizationDetail = () => {
  const { organizationId } = useParams() as { organizationId: string };
  return <OrganizationDetailContent key={organizationId} />;
};

export default OrganizationDetail;
