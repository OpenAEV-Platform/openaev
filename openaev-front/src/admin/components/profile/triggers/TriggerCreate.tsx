import { Add } from '@mui/icons-material';
import { Button } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { createNotificationTrigger } from '../../../../actions/notifications/notification-trigger-actions';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type NotificationTriggerInput, type NotificationTriggerOutput } from '../../../../utils/api-types';
import TriggerForm from './TriggerForm';

interface Props { onCreate?: (result: NotificationTriggerOutput) => void }

/**
 * Creation entry point aligned with OpenCTI's TriggerCreation: two dedicated
 * buttons ("Create Regular digest" and "Create Live trigger"), each opening
 * its own creation drawer directly - no intermediate type-choice dialog.
 */
const TriggerCreate: FunctionComponent<Props> = ({ onCreate }) => {
  const { t } = useFormatter();
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

  const buttonSx = {
    whiteSpace: 'nowrap',
    flexShrink: 0,
  };

  return (
    <>
      <Button
        onClick={() => setTriggerType('DIGEST')}
        color="primary"
        variant="contained"
        size="small"
        data-testid="button-create-digest"
        startIcon={<Add />}
        sx={{
          ...buttonSx,
          marginRight: 1,
        }}
      >
        {t('Create Regular digest')}
      </Button>
      <Button
        onClick={() => setTriggerType('LIVE')}
        color="primary"
        variant="contained"
        size="small"
        data-testid="button-create-live"
        startIcon={<Add />}
        sx={buttonSx}
      >
        {t('Create Live trigger')}
      </Button>
      <Drawer
        open={triggerType !== null}
        handleClose={() => setTriggerType(null)}
        title={triggerType === 'DIGEST' ? t('Create a regular digest') : t('Create a live trigger')}
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
