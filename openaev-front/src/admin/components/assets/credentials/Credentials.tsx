import { PlayCircleOutlineOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { Box, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchCredential, searchCredentials } from '../../../../actions/assets/credential-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { initSorting } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../components/common/SortHeadersList';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import {
  type CredentialFullOutput,
  type CredentialOutput,
  type SearchPaginationInput,
} from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { humanizeEnum } from '../asset-categories';
import AssetCategoryIcon from '../AssetCategoryIcon';
import AssetStatus from '../AssetStatus';
import CredentialCreation from './CredentialCreation';
import CredentialPopover from './CredentialPopover';
import convertCredentialFullOutputToCredentialInput from './credentialUtils';

const inlineStyles: Record<string, CSSProperties> = {
  credential_name: { width: '12%' },
  credential_type: { width: '10%' },
  credential_auth_method: { width: '13%' },
  credential_status: { width: '11%' },
  credential_created_by: { width: '14%' },
  credential_created_at: { width: '14%' },
  credential_last_verified_at: { width: '12%' },
  credential_tags_ids: { width: '14%' },
};

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

const Credentials = () => {
  const { t, fldt } = useFormatter();
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const [loading, setLoading] = useState<boolean>(true);
  const [credentials, setCredentials] = useState<CredentialOutput[]>([]);

  const [searchParams] = useSearchParams();
  const [search] = searchParams.getAll('search');

  const resolveCredentialInitialValues = async (credentialId: string) => {
    const result = await fetchCredential(credentialId);
    const detail: CredentialFullOutput = result.data;
    return convertCredentialFullOutputToCredentialInput(detail);
  };

  const availableFilterNames = [
    'secret_reference_credential_type',
    'secret_reference_credential_auth_method',
    'secret_reference_status',
    'secret_reference_created_by',
    'secret_reference_tags',
  ];

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'credentials',
    buildSearchPagination({
      sorts: initSorting('credential_created_at', 'DESC'),
      textSearch: search,
    }),
  );

  const searchCredentialsToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchCredentials(input).finally(() => setLoading(false));
  };

  const headers: Header[] = useMemo(() => [
    {
      field: 'credential_name',
      label: t('Name'),
      isSortable: true,
      value: (credential: CredentialOutput) => credential.credential_name,
    },
    {
      field: 'credential_type',
      label: t('Type'),
      isSortable: false,
      value: (credential: CredentialOutput) => credential.credential_type ? humanizeEnum(credential.credential_type) : '-',
    },
    {
      field: 'credential_auth_method',
      label: t('Auth Method'),
      isSortable: false,
      value: (credential: CredentialOutput) => credential.credential_auth_method ? humanizeEnum(credential.credential_auth_method) : '-',
    },
    {
      field: 'credential_status',
      label: t('Status'),
      isSortable: false,
      value: (credential: CredentialOutput) => credential.credential_status == 'ACTIVE' || credential.credential_status == 'INACTIVE'
        ? <AssetStatus variant="list" status={credential.credential_status.toUpperCase() == 'ACTIVE' ? 'Active' : 'Inactive'} />
        : '-',
    },
    {
      field: 'credential_created_by',
      label: t('Created by'),
      isSortable: false,
      value: (credential: CredentialOutput) => credential.credential_created_by?.user_name || '-',
    },
    {
      field: 'credential_created_at',
      label: t('Created'),
      isSortable: true,
      value: (credential: CredentialOutput) => (credential.credential_created_at ? fldt(credential.credential_created_at) : '-'),
    },
    {
      field: 'credential_last_verified_at',
      label: t('Last verified'),
      isSortable: true,
      value: (credential: CredentialOutput) => (credential.credential_last_verified_at ? fldt(credential.credential_last_verified_at) : '-'),
    },
    {
      field: 'credential_tags_ids',
      label: t('Tags'),
      isSortable: false,
      value: (credential: CredentialOutput) => <ItemTags variant="list" tags={credential.credential_tags_ids ?? []} />,
    },
  ], [fldt, t]);

  return (
    <section>
      <Breadcrumbs
        variant="list"
        elements={[
          {
            label: t('Credentials'),
            current: true,
          },
        ]}
      />
      <PaginationComponentV2
        fetch={searchCredentialsToLoad}
        searchPaginationInput={searchPaginationInput}
        setContent={setCredentials}
        entityPrefix="credential"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        topBarButtons={(
          <Box display="flex" gap={1} alignItems="center">
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
              <CredentialCreation
                onCreate={result => setCredentials(current => [result, ...current])}
              />
            </Can>
          </Box>
        )}
      />

      <ListItem
        classes={{ root: classes.itemHead }}
        divider={false}
        secondaryAction={<span>&nbsp;</span>}
      >
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
      </ListItem>

      {loading
        ? <PaginatedListLoader Icon={PlayCircleOutlineOutlined} headers={headers} headerStyles={inlineStyles} />
        : credentials.map((credential: CredentialOutput) => (
            <ListItem
              key={credential.credential_id}
              disablePadding
              divider
              secondaryAction={(
                <CredentialPopover
                  credentialId={credential.credential_id ?? ''}
                  credentialName={credential.credential_name ?? ''}
                  resolveInitialValues={() => resolveCredentialInitialValues(credential.credential_id ?? '')}
                  onUpdate={(updated) => {
                    setCredentials(current => current.map(item => (
                      item.credential_id === updated.credential_id
                        ? {
                            ...item,
                            ...updated,
                          }
                        : item
                    )));
                  }}
                  onDelete={(deletedId) => {
                    setCredentials(current => current.filter(item => item.credential_id !== deletedId));
                  }}
                />
              )}
            >
              <ListItemButton
                classes={{ root: classes.item }}
                component={Link}
                to={`/admin/credentials/${credential.credential_id}`}
              >
                <ListItemIcon>
                  <AssetCategoryIcon
                    scope="credential"
                    category={credential.credential_type ?? null}
                    color="primary"
                  />
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
                          {header.value?.(credential)}
                        </div>
                      ))}
                    </div>
                  )}
                />
              </ListItemButton>
            </ListItem>
          ))}

      {!loading && credentials.length === 0 && (
        <Empty
          icon={TrackChangesOutlined}
          message={t('No credential found.')}
        />
      )}
    </section>
  );
};

export default Credentials;
