import { DomainOutlined, FileDownloadOutlined } from '@mui/icons-material';
import { IconButton, List, ListItem, ListItemIcon, ListItemSecondaryAction, ListItemText, Tooltip } from '@mui/material';
import { type CSSProperties } from 'react';
import { CSVLink } from 'react-csv';
import { useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type OrganizationHelper, type TagHelper, type UserHelper } from '../../../actions/helper';
import { fetchOrganizations } from '../../../actions/Organization';
import Breadcrumbs from '../../../components/Breadcrumbs';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { useFormatter } from '../../../components/i18n';
import ItemTags from '../../../components/ItemTags';
import SearchFilter from '../../../components/SearchFilter';
import { useHelper } from '../../../store';
import { type Organization } from '../../../utils/api-types';
import { exportData } from '../../../utils/Environment';
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import { Can } from '../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import useSearchAnFilter from '../../../utils/SortingFiltering';
import { truncate } from '../../../utils/String';
import TagsFilter from '../common/filters/TagsFilter';
import SecurityMenu from '../settings/SecurityMenu';
import CreateOrganization from './organizations/CreateOrganization';
import OrganizationPopover from './organizations/OrganizationPopover';

const useStyles = makeStyles()(() => ({
  parameters: {
    marginTop: -10,
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  filters: {
    display: 'flex',
    gap: '10px',
  },
  itemHead: {
    textTransform: 'uppercase',
    cursor: 'pointer',
    paddingLeft: 10,
  },
  item: { height: 50 },
  downloadButton: { marginRight: 15 },
}));

const inlineStylesHeaders: Record<string, CSSProperties> = {
  iconSort: {
    position: 'absolute',
    margin: '0 0 0 5px',
    padding: 0,
    top: '0px',
  },
  organization_name: {
    width: '30%',
    fontSize: 12,
    fontWeight: '700',
  },
  organization_description: {
    width: '40%',
    fontSize: 12,
    fontWeight: '700',
  },
  organization_tags: {
    width: '30%',
    fontSize: 12,
    fontWeight: '700',
  },
};

const inlineStyles: Record<string, CSSProperties> = {
  organization_name: { width: '30%' },
  organization_description: { width: '40%' },
  organization_tags: { width: '30%' },
};

const Organizations = () => {
  // Standard hooks
  const dispatch = useAppDispatch();
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const { t } = useFormatter();

  // Fetching data
  const { organizations, tagsMap }: {
    organizations: ReturnType<OrganizationHelper['getOrganizations']>;
    tagsMap: ReturnType<TagHelper['getTagsMap']>;
  }
    = useHelper((helper: UserHelper & TagHelper & OrganizationHelper) => ({
      organizations: helper.getOrganizations(),
      tagsMap: helper.getTagsMap(),
    }));

  useDataLoader(() => {
    dispatch(fetchOrganizations());
  });

  // Query param
  const [searchParams] = useSearchParams();
  const [search] = searchParams.getAll('search');
  const [searchId] = searchParams.getAll('id');

  // Filter and sort hook
  const filtering = useSearchAnFilter(
    'organization',
    'name',
    [
      'name',
      'description',
    ],
    { defaultKeyword: search },
  );

  const sortedOrganizations: Organization[] = filtering.filterAndSort(organizations);

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="list"
          elements={[{ label: t('Settings') }, { label: t('Security') }, {
            label: t('Organizations'),
            current: true,
          }]}
        />
        <div className={classes.parameters}>
          <div className={classes.filters}>
            <SearchFilter
              variant="small"
              onChange={filtering.handleSearch}
              keyword={filtering.keyword}
            />
            <TagsFilter
              onAddTag={filtering.handleAddTag}
              onRemoveTag={filtering.handleRemoveTag}
              currentTags={filtering.tags}
              tagsFetched
            />
          </div>
          <div className={classes.downloadButton}>
            {sortedOrganizations.length > 0 ? (
              <CSVLink
                data={exportData(
                  'organization',
                  [
                    'organization_name',
                    'organization_description',
                    'organization_tags',
                  ],
                  sortedOrganizations,
                  tagsMap,
                )}
                filename={`${t('Organizations')}.csv`}
              >
                <Tooltip title={t('Export this list')}>
                  <IconButton size="large">
                    <FileDownloadOutlined color="primary" />
                  </IconButton>
                </Tooltip>
              </CSVLink>
            ) : (
              <IconButton size="large" disabled>
                <FileDownloadOutlined />
              </IconButton>
            )}
          </div>
        </div>
        <div className="clearfix" />
        <List>
          <ListItem
            classes={{ root: classes.itemHead }}
            divider={false}
            style={{ paddingTop: 0 }}
            secondaryAction={<>&nbsp;</>}
          >
            <ListItemIcon />
            <ListItemText
              primary={(
                <div style={bodyItemsStyles.bodyItems}>
                  {filtering.buildHeader(
                    'organization_name',
                    'Name',
                    true,
                    inlineStylesHeaders,
                  )}
                  {filtering.buildHeader(
                    'organization_description',
                    'Description',
                    true,
                    inlineStylesHeaders,
                  )}
                  {filtering.buildHeader(
                    'organization_tags',
                    'Tags',
                    true,
                    inlineStylesHeaders,
                  )}
                </div>
              )}
            />
            <ListItemSecondaryAction> &nbsp; </ListItemSecondaryAction>
          </ListItem>
          {sortedOrganizations.map(organization => (
            <ListItem
              key={organization.organization_id}
              classes={{ root: classes.item }}
              secondaryAction={(
                <OrganizationPopover
                  organization={organization}
                  tagsMap={tagsMap}
                  openEditOnInit={organization.organization_id === searchId}
                />
              )}
              divider
            >
              <ListItemIcon>
                <DomainOutlined color="primary" />
              </ListItemIcon>
              <ListItemText
                primary={(
                  <div style={bodyItemsStyles.bodyItems}>
                    <div
                      style={{
                        ...bodyItemsStyles.bodyItem,
                        ...inlineStyles.organization_name,
                      }}
                    >
                      {organization.organization_name}
                    </div>
                    <div
                      style={{
                        ...bodyItemsStyles.bodyItem,
                        ...inlineStyles.organization_description,
                      }}
                    >
                      {truncate(
                        organization.organization_description || '-',
                        50,
                      )}
                    </div>
                    <div
                      style={{
                        ...bodyItemsStyles.bodyItem,
                        ...inlineStyles.organization_tags,
                      }}
                    >
                      <ItemTags
                        variant="list"
                        tags={organization.organization_tags}
                      />
                    </div>
                  </div>
                )}
              />
            </ListItem>
          ))}
        </List>
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
          <CreateOrganization />
        </Can>
      </div>
      <SecurityMenu />
    </div>
  );
};

export default Organizations;
