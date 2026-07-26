import { Dialog, DialogContent, DialogTitle, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Typography } from '@mui/material';
import { Link } from 'react-router';

import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { type NotificationOutput } from '../../../../utils/api-types';
import NotificationOperationChip from './NotificationOperationChip';
import { contentGroupsOf, eventUrl, operationIcon } from './notificationUtils';

/**
 * Details of a digest notification: every composed trigger group with its
 * matched events. Events pointing to an existing entity are navigable
 * (mirrors OpenCTI's digest drawer).
 */
const DigestNotificationDialog = ({ notification, onClose }: {
  notification: NotificationOutput | null;
  onClose: () => void;
}) => {
  const { t } = useFormatter();
  return (
    <Dialog
      open={notification !== null}
      onClose={onClose}
      TransitionComponent={Transition}
      fullWidth
      maxWidth="md"
      PaperProps={{ elevation: 1 }}
    >
      <DialogTitle>{t('Digest details')}</DialogTitle>
      <DialogContent>
        {notification !== null && contentGroupsOf(notification).map(group => (
          <div key={`${notification.notification_id}-${group.title}`}>
            {group.title && (
              <Typography variant="subtitle2" style={{ marginTop: 8 }}>
                {group.title}
              </Typography>
            )}
            <List dense disablePadding>
              {(group.events ?? []).map((event, index) => {
                const url = eventUrl(event);
                const content = (
                  <>
                    <ListItemIcon>
                      {operationIcon(event.operation ?? 'NONE')}
                    </ListItemIcon>
                    <div style={{
                      marginRight: 10,
                      flexShrink: 0,
                    }}
                    >
                      <NotificationOperationChip operation={event.operation ?? 'NONE'} />
                    </div>
                    <ListItemText primary={event.message} />
                  </>
                );
                return url
                  ? (
                      <ListItemButton
                        key={`${event.resource_id}-${index}`}
                        divider
                        component={Link}
                        to={url}
                        onClick={onClose}
                      >
                        {content}
                      </ListItemButton>
                    )
                  : (
                      <ListItem key={`${event.resource_id}-${index}`} divider>
                        {content}
                      </ListItem>
                    );
              })}
            </List>
          </div>
        ))}
      </DialogContent>
    </Dialog>
  );
};

export default DigestNotificationDialog;
