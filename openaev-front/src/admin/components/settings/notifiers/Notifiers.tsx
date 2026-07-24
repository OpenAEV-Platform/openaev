import { EmailOutlined, NotificationsOutlined, WebhookOutlined } from '@mui/icons-material';
import { Chip, List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useMemo, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { searchNotifiers } from '../../../../actions/notifications/notifier-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../components/common/SortHeadersList';
import { useFormatter } from '../../../../components/i18n';
import { type NotifierOutput } from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import CustomizationMenu from '../CustomizationMenu';
import NotifierCreate from './NotifierCreate';
import NotifierPopover from './NotifierPopover';

const useStyles = makeStyles()(() => ({ itemHead: { textTransform: 'uppercase' } }));

const inlineStyles: Record<string, CSSProperties> = {
  notifier_name: { width: '30%' },
  notifier_type: { width: '15%' },
  notifier_description: { width: '40%' },
  notifier_built_in: { width: '15%' },
};

const typeIcon = (type?: string) => {
  switch (type) {
    case 'EMAIL':
      return <EmailOutlined color="primary" />;
    case 'WEBHOOK':
      return <WebhookOutlined color="primary" />;
    default:
      return <NotificationsOutlined color="primary" />;
  }
};

const Notifiers = () => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();

  const [notifiers, setNotifiers] = useState<NotifierOutput[]>([]);

  const availableFilterNames = ['notifier_name', 'notifier_type'];
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('notifiers', buildSearchPagination({}));

  const headers: Header[] = useMemo(() => [
    {
      field: 'notifier_name',
      label: 'Name',
      isSortable: true,
      value: (notifier: NotifierOutput) => notifier.notifier_name,
    },
    {
      field: 'notifier_type',
      label: 'Type',
      isSortable: true,
      value: (notifier: NotifierOutput) => {
        const labels: Record<string, string> = {
          UI: 'User interface',
          EMAIL: 'Email',
          WEBHOOK: 'Webhook',
        };
        return (
          <Chip
            label={t(labels[notifier.notifier_type ?? 'UI'])}
            size="small"
            variant="outlined"
            color="primary"
          />
        );
      },
    },
    {
      field: 'notifier_description',
      label: 'Description',
      isSortable: false,
      value: (notifier: NotifierOutput) => notifier.notifier_description,
    },
    {
      field: 'notifier_built_in',
      label: 'Built-in',
      isSortable: false,
      value: (notifier: NotifierOutput) => (notifier.notifier_built_in
        ? (
            <Chip
              label={t('Built-in')}
              size="small"
              variant="outlined"
              color="secondary"
            />
          )
        : undefined),
    },
  ], []);

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="list"
          elements={[{ label: t(SETTINGS_LABEL) }, { label: t('Customization') }, {
            label: t('Notifiers'),
            current: true,
          }]}
        />
        <PaginationComponentV2
          fetch={searchNotifiers}
          searchPaginationInput={searchPaginationInput}
          setContent={setNotifiers}
          availableFilterNames={availableFilterNames}
          queryableHelpers={queryableHelpers}
          entityPrefix="notifier"
          topBarButtons={(
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANT_SETTINGS}>
              <NotifierCreate onCreate={result => setNotifiers([...notifiers, result])} />
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
          {notifiers.map(notifier => (
            <ListItem
              key={notifier.notifier_id}
              secondaryAction={(
                <NotifierPopover
                  notifier={notifier}
                  onUpdate={result => setNotifiers(notifiers.map(existing => (
                    existing.notifier_id !== result.notifier_id ? existing : result
                  )))}
                  onDelete={result => setNotifiers(notifiers.filter(existing => existing.notifier_id !== result))}
                />
              )}
              divider
            >
              <ListItemIcon>
                {typeIcon(notifier.notifier_type)}
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
                        {header.value?.(notifier)}
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

export default Notifiers;
