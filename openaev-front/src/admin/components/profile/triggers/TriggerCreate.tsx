import { CampaignOutlined, InboxOutlined } from '@mui/icons-material';
import { Dialog, DialogTitle, List, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { createNotificationTrigger } from '../../../../actions/notifications/notification-trigger-actions';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type NotificationTriggerInput, type NotificationTriggerOutput } from '../../../../utils/api-types';
import TriggerForm from './TriggerForm';

interface Props { onCreate?: (result: NotificationTriggerOutput) => void }

/** Creation entry point: pick live or digest, then fill the matching form. */
const TriggerCreate: FunctionComponent<Props> = ({ onCreate }) => {
  const { t } = useFormatter();
  const [openChoice, setOpenChoice] = useState(false);
  const [triggerType, setTriggerType] = useState<'LIVE' | 'DIGEST' | null>(null);

  const onSubmit = (input: NotificationTriggerInput) => {
    createNotificationTrigger(input).then((result: { data: NotificationTriggerOutput }) => {
      if (result) {
        onCreate?.(result.data);
        setTriggerType(null);
      }
      return result;
    });
  };

  return (
    <>
      <ButtonCreate onClick={() => setOpenChoice(true)} />
      <Dialog open={openChoice} onClose={() => setOpenChoice(false)}>
        <DialogTitle>{t('Create a notification trigger')}</DialogTitle>
        <List style={{ paddingBottom: 16 }}>
          <ListItemButton
            onClick={() => {
              setOpenChoice(false);
              setTriggerType('LIVE');
            }}
          >
            <ListItemIcon><CampaignOutlined color="primary" /></ListItemIcon>
            <ListItemText
              primary={t('Live trigger')}
              secondary={t('Notify me in real time when matching events occur')}
            />
          </ListItemButton>
          <ListItemButton
            onClick={() => {
              setOpenChoice(false);
              setTriggerType('DIGEST');
            }}
          >
            <ListItemIcon><InboxOutlined color="secondary" /></ListItemIcon>
            <ListItemText
              primary={t('Digest trigger')}
              secondary={t('Aggregate the events of other triggers periodically')}
            />
          </ListItemButton>
        </List>
      </Dialog>
      <Drawer
        open={triggerType !== null}
        handleClose={() => setTriggerType(null)}
        title={triggerType === 'DIGEST' ? t('Create a digest trigger') : t('Create a live trigger')}
      >
        <TriggerForm
          triggerType={triggerType ?? 'LIVE'}
          onSubmit={onSubmit}
        />
      </Drawer>
    </>
  );
};

export default TriggerCreate;
