import { DomainOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';

import { searchOrganizations } from '../../../../actions/organizations/organization-actions';
import { fetchTags } from '../../../../actions/tags/tag-action';
import { type TagHelper } from '../../../../actions/tags/tag-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import PaginatedList from '../../../../components/common/list/PaginatedList';
import { initSorting } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../components/common/SortHeadersList';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { SECURITY_ORGANIZATION_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import { type Organization, type SearchPaginationInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import SecurityMenu from '../SecurityMenu';
import CreateOrganization from './CreateOrganization';
import OrganizationPopover from './OrganizationPopover';

const inlineStyles: Record<string, CSSProperties> = {
  organization_name: { width: '30%' },
  organization_description: { width: '40%' },
  organization_tags: { width: '30%' },
};

// Admin-side organizations administration (Settings > Security >
// Organizations), rendered with the security right menu. Fully separated from
// the business-side list (teams/OrganizationsList) so the two experiences can
// diverge.
const Organizations = () => {
  // Standard hooks
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { t } = useFormatter();

  // Tags map, needed by the edit form inside the popover
  const { tagsMap } = useHelper((helper: TagHelper) => ({ tagsMap: helper.getTagsMap() }));

  useDataLoader(() => {
    dispatch(fetchTags());
  });

  // Query param
  const [searchParams] = useSearchParams();
  const [search] = searchParams.getAll('search');
  const [searchId] = searchParams.getAll('id');

  // Headers
  const headers: Header[] = useMemo(() => [
    {
      field: 'organization_name',
      label: 'Name',
      isSortable: true,
      value: (organization: Organization) => organization.organization_name,
    },
    {
      field: 'organization_description',
      label: 'Description',
      isSortable: true,
      value: (organization: Organization) => organization.organization_description || '-',
    },
    {
      field: 'organization_tags',
      label: 'Tags',
      isSortable: false,
      value: (organization: Organization) => <ItemTags variant="list" tags={organization.organization_tags} />,
    },
  ], []);

  const availableFilterNames = [
    'organization_name',
    'organization_tags',
  ];

  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('security-organizations', buildSearchPagination({
    sorts: initSorting('organization_name'),
    textSearch: search,
  }));

  const [loading, setLoading] = useState<boolean>(true);
  const searchOrganizationsToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchOrganizations(input).finally(() => setLoading(false));
  };

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="list"
          elements={[{ label: t(SETTINGS_LABEL) }, { label: t('Security') }, {
            label: t('Organizations'),
            current: true,
          }]}
        />
        <PaginationComponentV2
          fetch={searchOrganizationsToLoad}
          searchPaginationInput={searchPaginationInput}
          setContent={setOrganizations}
          entityPrefix="organization"
          availableFilterNames={availableFilterNames}
          queryableHelpers={queryableHelpers}
          topBarButtons={(
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANT_SETTINGS}>
              <CreateOrganization
                onCreate={(result: Organization) => setOrganizations(prev => [result, ...prev])}
              />
            </Can>
          )}
        />
        <List>
          <ListItem
            divider={false}
            secondaryAction={<>&nbsp;</>}
            style={{ paddingTop: 0 }}
          >
            <ListItemIcon />
            <ListItemText
              style={{ textTransform: 'uppercase' }}
              primary={(
                <SortHeadersComponentV2
                  headers={headers}
                  inlineStylesHeaders={inlineStyles}
                  sortHelpers={queryableHelpers.sortHelpers}
                />
              )}
            />
          </ListItem>
          {loading
            ? <PaginatedListLoader Icon={DomainOutlined} headers={headers} headerStyles={inlineStyles} />
            : (
                <PaginatedList<Organization>
                  Icon={DomainOutlined}
                  secondaryAction={organization => (
                    <OrganizationPopover
                      organization={organization}
                      tagsMap={tagsMap}
                      onUpdate={(result: Organization) => setOrganizations(prev => prev.map(o => (o.organization_id !== result.organization_id ? o : result)))}
                      onDelete={(result: string) => setOrganizations(prev => prev.filter(o => (o.organization_id !== result)))}
                      openEditOnInit={organization.organization_id === searchId}
                    />
                  )}
                  headers={headers}
                  items={organizations}
                  rowKey="organization_id"
                  onRowClick={organization => navigate(`${SECURITY_ORGANIZATION_BASE_URL}/${organization.organization_id}`)}
                  itemWidth={inlineStyles}
                />
              )}
        </List>
      </div>
      <SecurityMenu />
    </div>
  );
};

export default Organizations;
