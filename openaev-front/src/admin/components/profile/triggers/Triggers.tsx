import { CampaignOutlined, InboxOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useMemo, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { searchNotificationTriggers } from '../../../../actions/notifications/notification-trigger-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../components/common/SortHeadersList';
import { useFormatter } from '../../../../components/i18n';
import ItemBoolean from '../../../../components/ItemBoolean';
import { type NotificationTriggerOutput } from '../../../../utils/api-types';
import NotificationCenterTabs from '../NotificationCenterTabs';
import { TriggerEventChips, TriggerResourceChip, TriggerTypeChip } from './TriggerChips';
import TriggerCreate from './TriggerCreate';
import TriggerPopover from './TriggerPopover';

const useStyles = makeStyles()(() => ({ itemHead: { textTransform: 'uppercase' } }));

const inlineStyles: Record<string, CSSProperties> = {
  notification_trigger_name: { width: '30%' },
  notification_trigger_type: { width: '15%' },
  notification_trigger_resource_type: { width: '20%' },
  notification_trigger_event_types: { width: '20%' },
  notification_trigger_enabled: { width: '15%' },
};

const Triggers = () => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();

  const [triggers, setTriggers] = useState<NotificationTriggerOutput[]>([]);

  const availableFilterNames = [
    'notification_trigger_name',
    'notification_trigger_type',
    'notification_trigger_resource_type',
  ];
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('notification-triggers', buildSearchPagination({}));

  const headers: Header[] = useMemo(() => [
    {
      field: 'notification_trigger_name',
      label: 'Name',
      isSortable: true,
      value: (trigger: NotificationTriggerOutput) => trigger.notification_trigger_name,
    },
    {
      field: 'notification_trigger_type',
      label: 'Type',
      isSortable: true,
      value: (trigger: NotificationTriggerOutput) => (
        <TriggerTypeChip type={trigger.notification_trigger_type} />
      ),
    },
    {
      field: 'notification_trigger_resource_type',
      label: 'Resource type',
      isSortable: true,
      value: (trigger: NotificationTriggerOutput) => (
        <TriggerResourceChip trigger={trigger} />
      ),
    },
    {
      field: 'notification_trigger_event_types',
      label: 'Events',
      isSortable: false,
      value: (trigger: NotificationTriggerOutput) => (
        <TriggerEventChips trigger={trigger} />
      ),
    },
    {
      field: 'notification_trigger_enabled',
      label: 'Enabled',
      isSortable: false,
      value: (trigger: NotificationTriggerOutput) => (
        <ItemBoolean
          variant="inList"
          status={trigger.notification_trigger_enabled ?? false}
          label={trigger.notification_trigger_enabled ? t('Enabled') : t('Disabled')}
        />
      ),
    },
  ], []);

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Notification center'),
          current: true,
        }]}
      />
      <NotificationCenterTabs current="triggers" />
      <PaginationComponentV2
        fetch={searchNotificationTriggers}
        searchPaginationInput={searchPaginationInput}
        setContent={setTriggers}
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        entityPrefix="notification_trigger"
        topBarButtons={(
          <TriggerCreate onCreate={result => setTriggers([result, ...triggers])} />
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
        {triggers.map(trigger => (
          <ListItem
            key={trigger.notification_trigger_id}
            secondaryAction={(
              <TriggerPopover
                trigger={trigger}
                onUpdate={result => setTriggers(triggers.map(existing => (
                  existing.notification_trigger_id !== result.notification_trigger_id ? existing : result
                )))}
                onDelete={result => setTriggers(triggers.filter(existing => existing.notification_trigger_id !== result))}
              />
            )}
            divider
          >
            <ListItemIcon>
              {trigger.notification_trigger_type === 'DIGEST'
                ? <InboxOutlined color="secondary" />
                : <CampaignOutlined color="primary" />}
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
                      {header.value?.(trigger)}
                    </div>
                  ))}
                </div>
              )}
            />
          </ListItem>
        ))}
      </List>
    </>
  );
};

export default Triggers;
