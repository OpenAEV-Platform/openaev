import { DnsOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useMemo, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { searchCustomDomains } from '../../../../actions/custom_domains/customdomain-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../components/common/SortHeadersList';
import { useFormatter } from '../../../../components/i18n';
import { type CustomDomain } from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import CustomizationMenu from '../CustomizationMenu';
import CustomDomainCreate from './CustomDomainCreate';
import CustomDomainPopover from './CustomDomainPopover';
import CustomDomainStatusChip from './CustomDomainStatusChip';

const useStyles = makeStyles()(() => ({ itemHead: { textTransform: 'uppercase' } }));

const inlineStyles: Record<string, CSSProperties> = {
  custom_domain_hostname: { width: '45%' },
  custom_domain_status: { width: '30%' },
  custom_domain_verified_at: { width: '25%' },
};

const CustomDomains = () => {
  const { t, fldt } = useFormatter();
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();

  const [customDomains, setCustomDomains] = useState<CustomDomain[]>([]);

  const availableFilterNames = [
    'custom_domain_hostname',
    'custom_domain_status',
  ];
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'custom-domains',
    buildSearchPagination({}),
  );

  const upsert = (result: CustomDomain) =>
    setCustomDomains(prev => (prev.some(d => d.custom_domain_id === result.custom_domain_id)
      ? prev.map(d => (d.custom_domain_id === result.custom_domain_id ? result : d))
      : [...prev, result]));

  const headers: Header[] = useMemo(() => [
    {
      field: 'custom_domain_hostname',
      label: 'Domain',
      isSortable: true,
      value: (domain: CustomDomain) => domain.custom_domain_hostname,
    },
    {
      field: 'custom_domain_status',
      label: 'Status',
      isSortable: true,
      value: (domain: CustomDomain) => <CustomDomainStatusChip status={domain.custom_domain_status} />,
    },
    {
      field: 'custom_domain_verified_at',
      label: 'Verified on',
      isSortable: true,
      value: (domain: CustomDomain) =>
        (domain.custom_domain_verified_at ? fldt(domain.custom_domain_verified_at) : '-'),
    },
  ], [fldt]);

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="list"
          elements={[{ label: t(SETTINGS_LABEL) }, { label: t('Customization') }, {
            label: t('Custom domains'),
            current: true,
          }]}
        />
        <PaginationComponentV2
          fetch={searchCustomDomains}
          searchPaginationInput={searchPaginationInput}
          setContent={setCustomDomains}
          availableFilterNames={availableFilterNames}
          queryableHelpers={queryableHelpers}
          entityPrefix="custom_domain"
          topBarButtons={(
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANT_SETTINGS}>
              <CustomDomainCreate onCreate={upsert} onUpdate={upsert} />
            </Can>
          )}
        />
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
                <SortHeadersComponentV2
                  headers={headers}
                  inlineStylesHeaders={inlineStyles}
                  sortHelpers={queryableHelpers.sortHelpers}
                />
              )}
            />
          </ListItem>
          {customDomains.map((domain: CustomDomain) => (
            <ListItem
              key={domain.custom_domain_id}
              secondaryAction={(
                <CustomDomainPopover
                  customDomain={domain}
                  onDelete={result => setCustomDomains(prev => prev.filter(d => d.custom_domain_id !== result))}
                  onUpdate={upsert}
                />
              )}
              divider
            >
              <ListItemIcon>
                <DnsOutlined color="primary" />
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
                        {header.value?.(domain)}
                      </div>
                    ))}
                  </div>
                )}
              />
            </ListItem>
          ))}
        </List>
      </div>
      <CustomizationMenu />
    </div>
  );
};

export default CustomDomains;
