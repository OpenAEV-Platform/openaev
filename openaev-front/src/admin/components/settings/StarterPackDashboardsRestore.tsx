import { Button } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';

import { restoreStarterPackDashboards } from '../../../actions/custom_dashboards/customdashboard-action';
import DialogConfirmation from '../../../components/common/DialogConfirmation';
import { useFormatter } from '../../../components/i18n';
import type { StarterPackDashboardsRestoreOutput } from '../../../utils/api-types';
import { MESSAGING$ } from '../../../utils/Environment';

interface Props {
  onRestored?: (result: StarterPackDashboardsRestoreOutput) => void;
}

/**
 * Re-creates the starter-pack dashboards that were deleted, from the archives bundled in the
 * platform, and sets the empty default dashboards back. Idempotent on the server side.
 */
const StarterPackDashboardsRestore: FunctionComponent<Props> = ({ onRestored }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const [open, setOpen] = useState(false);

  const submitRestore = async () => {
    try {
      const { data }: { data: StarterPackDashboardsRestoreOutput } = await restoreStarterPackDashboards();
      const restored = data.restored_dashboards.length;
      MESSAGING$.notifySuccess(restored === 0
        ? t('All starter pack dashboards already exist')
        : t('Starter pack dashboards restored: {count}', { count: restored }));
      onRestored?.(data);
    } finally {
      setOpen(false);
    }
  };

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(1),
      marginTop: theme.spacing(2.5),
    }}
    >
      <span>{t('Re-create the starter pack dashboards that were deleted, under their original names, and set the empty default dashboards back. Existing dashboards are kept.')}</span>
      <div>
        <Button type="button" priority="secondary" onClick={() => setOpen(true)}>
          {t('Restore starter pack dashboards')}
        </Button>
      </div>
      <DialogConfirmation
        open={open}
        handleClose={() => setOpen(false)}
        handleSubmit={submitRestore}
        text={t('Do you want to restore the starter pack dashboards?')}
        submitLabel={t('Restore')}
      />
    </div>
  );
};

export default StarterPackDashboardsRestore;
