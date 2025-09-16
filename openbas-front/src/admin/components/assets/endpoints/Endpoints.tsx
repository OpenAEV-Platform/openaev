import { DevicesOtherOutlined, HelpOutlineOutlined } from '@mui/icons-material';
import {
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  ToggleButtonGroup,
} from '@mui/material';
import { type CSSProperties, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { searchEndpoints } from '../../../../actions/assets/endpoint-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import ExportButton from '../../../../components/common/ExportButton';
import AssetPlatformFragment from '../../../../components/common/list/fragments/AssetPlatformFragment';
import EndpointActiveFragment from '../../../../components/common/list/fragments/EndpointActiveFragment';
import EndpointAgentsPrivilegeFragment from '../../../../components/common/list/fragments/EndpointAgentsPrivilegeFragment';
import EndpointArchFragment from '../../../../components/common/list/fragments/EndpointArchFragment';
import { initSorting } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { ENDPOINT_BASE_URL } from '../../../../constants/BaseUrls';
import { type EndpointOutput, type SearchPaginationInput } from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import EndpointListItemFragments from '../../common/endpoints/EndpointListItemFragments';
import EndpointAgentsExecutorsFragment from '../../common/endpoints/fragments/EndpointAgentsExecutorsFragment';
import EndpointCreation from './EndpointCreation';
import EndpointPopover from './EndpointPopover';
import ImportUploaderEndpoints from './ImportUploaderEndpoints';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

const inlineStyles: Record<string, CSSProperties> = {
  asset_name: { width: '25%' },
  endpoint_active: { width: '10%' },
  endpoint_agents_privilege: { width: '12%' },
  endpoint_platform: { width: '10%' },
  endpoint_arch: { width: '10%' },
  endpoint_agents_executor: { width: '13%' },
  asset_tags: { width: '15%' },
};

const Endpoints = () => {
  // Standard hooks
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const { t } = useFormatter();

  // Query param
  const [searchParams] = useSearchParams();
  const [search] = searchParams.getAll('search');

  const availableFilterNames = [
    'endpoint_platform',
    'endpoint_arch',
    'asset_tags',
  ];

  const [endpoints, setEndpoints] = useState<EndpointOutput[]>([]);
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('asset', buildSearchPagination({
    sorts: initSorting('asset_name'),
    textSearch: search,
  }));

  // Export
  const exportProps = {
    exportType: 'ENDPOINTS',
    exportKeys: [],
    exportData: endpoints,
    exportFileName: `${t('Endpoints')}.csv`,
    searchPaginationInput: searchPaginationInput,
  };

  const [loading, setLoading] = useState<boolean>(true);

  const searchEndpointsToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchEndpoints(input).finally(() => setLoading(false));
  };

  // Headers
  const headers = [
    {
      field: EndpointListItemFragments.ASSET_NAME,
      label: 'Name',
      isSortable: true,
      value: (endpoint: EndpointOutput) => endpoint.asset_name,
    },
    {
      field: EndpointListItemFragments.ENDPOINT_ACTIVE,
      label: 'Status',
      isSortable: false,
      value: (endpoint: EndpointOutput) => <EndpointActiveFragment activity_map={endpoint.asset_agents.map(a => a.agent_active ?? false)} />,
    },
    {
      field: EndpointListItemFragments.ENDPOINT_AGENTS_PRIVILEGE,
      label: 'Agents Privileges',
      isSortable: false,
      value: (endpoint: EndpointOutput) => <EndpointAgentsPrivilegeFragment privileges={endpoint.asset_agents.map(a => a.agent_privilege)} />,
    },
    {
      field: EndpointListItemFragments.ENDPOINT_PLATFORM,
      label: 'Platform',
      isSortable: true,
      value: (endpoint: EndpointOutput) => <AssetPlatformFragment platform={endpoint.endpoint_platform} />,
    },
    {
      field: EndpointListItemFragments.ENDPOINT_ARCH,
      label: 'Architecture',
      isSortable: true,
      value: (endpoint: EndpointOutput) => <EndpointArchFragment arch={endpoint.endpoint_arch} />,
    },
    {
      field: 'endpoint_agents_executor',
      label: 'Executors',
      isSortable: false,
      value: (endpoint: EndpointOutput) => <EndpointAgentsExecutorsFragment endpoint={endpoint} />,
    },
    {
      field: EndpointListItemFragments.ASSET_TAGS,
      label: 'Tags',
      isSortable: false,
      value: (endpoint: EndpointOutput) => <ItemTags variant="list" tags={endpoint.asset_tags ?? []} />,
    },
  ];

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{ label: t('Assets') }, {
          label: t('Endpoints'),
          current: true,
        }]}
      />
      <PaginationComponentV2
        fetch={searchEndpointsToLoad}
        searchPaginationInput={searchPaginationInput}
        setContent={setEndpoints}
        entityPrefix="asset"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        topBarButtons={(
          <ToggleButtonGroup value="fake" exclusive>
            <ExportButton totalElements={queryableHelpers.paginationHelpers.getTotalElements()} exportProps={exportProps} />
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSETS}>
              <ImportUploaderEndpoints />
            </Can>
          </ToggleButtonGroup>
        )}
      />
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          style={{ paddingTop: 0 }}
          secondaryAction={<>&nbsp;</>}
        >
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
        </ListItem>
        {
          loading
            ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} />
            : endpoints.map((endpoint: EndpointOutput) => {
                return (
                  <ListItem
                    key={endpoint.asset_id}
                    divider
                    secondaryAction={(
                      <EndpointPopover
                        inline
                        endpoint={{ ...endpoint }}
                        agentless={endpoint.asset_agents?.length === 0}
                        onUpdate={result => setEndpoints(endpoints.map(e => (e.asset_id !== result.asset_id ? e : result as EndpointOutput)))}
                        onDelete={result => setEndpoints(endpoints.filter(e => (e.asset_id !== result)))}
                      />
                    )}
                    disablePadding
                  >
                    <ListItemButton
                      component={Link}
                      to={`${ENDPOINT_BASE_URL}/${endpoint.asset_id}`}
                      classes={{ root: classes.item }}
                    >
                      <ListItemIcon>
                        <DevicesOtherOutlined color="primary" />
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
                                {header.value(endpoint)}
                              </div>
                            ))}
                          </div>
                        )}
                      />
                    </ListItemButton>
                  </ListItem>
                );
              })
        }
      </List>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSETS}>
        <EndpointCreation onCreate={result => setEndpoints([result as EndpointOutput, ...endpoints])} agentless={true} />
      </Can>
    </>
  );
};

export default Endpoints;
