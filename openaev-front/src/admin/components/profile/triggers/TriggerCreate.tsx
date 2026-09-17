import { Button } from '@filigran/design-system';
import { Add } from '@mui/icons-material';
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

  return (
    <>
      <Button
        startIcon={<Add fontSize="small" />}
        onClick={() => setTriggerType('DIGEST')}
        data-testid="button-create-digest"
        style={{
          marginRight: 8,
          whiteSpace: 'nowrap',
          flexShrink: 0,
        }}
      >
        {t('Create Regular digest')}
      </Button>
      <Button
        startIcon={<Add fontSize="small" />}
        onClick={() => setTriggerType('LIVE')}
        data-testid="button-create-live"
        style={{
          whiteSpace: 'nowrap',
          flexShrink: 0,
        }}
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
