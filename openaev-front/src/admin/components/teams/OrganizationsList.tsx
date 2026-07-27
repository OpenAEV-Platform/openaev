import { DomainOutlined, HelpOutlineOutlined } from '@mui/icons-material';
import { Box, Checkbox, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useContext, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { bulkDeleteOrganizations } from '../../../actions/Organization';
import { searchOrganizations } from '../../../actions/organizations/organization-actions';
import { fetchTags } from '../../../actions/tags/tag-action';
import { type TagHelper } from '../../../actions/tags/tag-helper';
import Breadcrumbs from '../../../components/Breadcrumbs';
import ExportButton from '../../../components/common/ExportButton';
import { initSorting } from '../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../components/common/SortHeadersList';
import { useFormatter } from '../../../components/i18n';
import ItemTags from '../../../components/ItemTags';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import { ORGANIZATION_BASE_URL } from '../../../constants/BaseUrls';
import { useHelper } from '../../../store';
import { type Organization, type SearchPaginationInput } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import useEntityToggle from '../../../utils/hooks/useEntityToggle';
import { AbilityContext, Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import ToolBar from '../common/ToolBar';
import CreateOrganization from './organizations/CreateOrganization';
import OrganizationPopover from './organizations/OrganizationPopover';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

const inlineStyles: Record<string, CSSProperties> = {
  organization_name: { width: '30%' },
  organization_description: { width: '40%' },
  organization_tags: { width: '30%' },
};

// Top-level Organizations list (left menu > Persons / Teams / Organizations).
// Deliberately a standalone view - duplicated from the admin Security >
// Organizations screen but WITHOUT the security right menu - so the two can
// diverge as more org-centric use cases land here.
const OrganizationsList = () => {
  // Standard hooks
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const bodyItemsStyles = useBodyItemsStyles();
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
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('organizations', buildSearchPagination({
    sorts: initSorting('organization_name'),
    textSearch: search,
  }));

  // Export
  const exportProps = {
    exportType: 'organization',
    exportKeys: [
      'organization_name',
      'organization_description',
      'organization_tags',
    ],
    exportData: organizations,
    exportFileName: `${t('Organizations')}.csv`,
  };

  const [loading, setLoading] = useState<boolean>(true);
  const searchOrganizationsToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchOrganizations(input).finally(() => setLoading(false));
  };

  // Bulk selection
  const ability = useContext(AbilityContext);
  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
  const {
    selectedElements,
    deSelectedElements,
    selectAll,
    handleClearSelectedElements,
    handleToggleSelectAll,
    onToggleEntity,
    numberOfSelectedElements,
  } = useEntityToggle<Organization>('organization', organizations, queryableHelpers.paginationHelpers.getTotalElements());

  const bulkDelete = () => {
    dispatch(bulkDeleteOrganizations({
      search_pagination_input: selectAll ? searchPaginationInput : undefined,
      organization_ids_to_process: selectAll ? undefined : Object.keys(selectedElements),
      organization_ids_to_ignore: Object.keys(deSelectedElements),
    })).then((result: { data?: string[] }) => {
      const deletedIds: string[] = result.data ?? [];
      const newTotal = Math.max(0, queryableHelpers.paginationHelpers.getTotalElements() - deletedIds.length);
      setOrganizations(organizations.filter(organization => !deletedIds.includes(organization.organization_id)));
      queryableHelpers.paginationHelpers.handleChangeTotalElements(newTotal);
      handleClearSelectedElements();
    });
  };

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
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
          <Box display="flex" gap={1} alignItems="center">
            <ExportButton totalElements={queryableHelpers.paginationHelpers.getTotalElements()} exportProps={exportProps} />
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANT_SETTINGS}>
              <CreateOrganization
                onCreate={(result: Organization) => setOrganizations([result, ...organizations])}
              />
            </Can>
          </Box>
        )}
      />
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          divider={false}
          sx={numberOfSelectedElements > 0
            ? {
                // Massive-operations toolbar: symmetric vertical padding keeps the
                // checkbox and actions vertically centered in the accent band.
                backgroundColor: 'background.accent',
                paddingBlock: 0.5,
              }
            : { paddingTop: 0 }}
          {...(numberOfSelectedElements === 0 ? { secondaryAction: <>&nbsp;</> } : {})}
        >
          {canManage && (
            <ListItemIcon style={{ minWidth: 40 }}>
              <Checkbox
                edge="start"
                checked={selectAll}
                disableRipple
                onChange={handleToggleSelectAll}
              />
            </ListItemIcon>
          )}
          {numberOfSelectedElements > 0 ? (
            <ListItemText
              primary={(
                <ToolBar
                  numberOfSelectedElements={numberOfSelectedElements}
                  handleClearSelectedElements={handleClearSelectedElements}
                  handleBulkDelete={bulkDelete}
                  canManage={canManage}
                  deleteConfirmationSingular={t('Do you want to delete this organization?')}
                  deleteConfirmationPlural={t('Do you want to delete these {count} organizations?', { count: String(numberOfSelectedElements) })}
                />
              )}
            />
          ) : (
            <>
              <ListItemIcon />
              <ListItemText
                primary={(
                  <SortHeadersComponentV2
                    headers={headers}
                    inlineStylesHeaders={inlineStyles}
                    sortHelpers={queryableHelpers.sortHelpers}
                  />
                )}
              />
            </>
          )}
        </ListItem>
        {loading
          ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} withCheckbox={canManage} />
          : organizations.map((organization: Organization) => (
              <ListItem
                key={organization.organization_id}
                divider
                disablePadding
                secondaryAction={(
                  <OrganizationPopover
                    organization={organization}
                    tagsMap={tagsMap}
                    onUpdate={(result: Organization) => setOrganizations(organizations.map(o => (o.organization_id !== result.organization_id ? o : result)))}
                    onDelete={(result: string) => setOrganizations(organizations.filter(o => (o.organization_id !== result)))}
                    openEditOnInit={organization.organization_id === searchId}
                  />
                )}
              >
                <ListItemButton classes={{ root: classes.item }} component={Link} to={`${ORGANIZATION_BASE_URL}/${organization.organization_id}`}>
                  {canManage && (
                    <ListItemIcon
                      style={{ minWidth: 40 }}
                      onClick={event => onToggleEntity(organization, event)}
                    >
                      <Checkbox
                        edge="start"
                        checked={
                          (selectAll && !(organization.organization_id in (deSelectedElements || {})))
                          || organization.organization_id in (selectedElements || {})
                        }
                        disableRipple
                      />
                    </ListItemIcon>
                  )}
                  <ListItemIcon>
                    <DomainOutlined color="primary" />
                  </ListItemIcon>
                  <ListItemText
                    primary={(
                      <div style={bodyItemsStyles.bodyItems}>
                        {headers.map(header => (
                          <div
                            key={header.field}
                            style={{
                              ...bodyItemsStyles.bodyItem,
                              ...inlineStyles[header.field],
                            }}
                          >
                            {header.value?.(organization)}
                          </div>
                        ))}
                      </div>
                    )}
                  />
                </ListItemButton>
              </ListItem>
            ))}
      </List>
    </>
  );
};

export default OrganizationsList;
