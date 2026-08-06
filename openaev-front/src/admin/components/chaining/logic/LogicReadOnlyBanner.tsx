import { Lock } from '@mui/icons-material';
import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import AlertBanner from '../../common/AlertBanner';

/**
 * Read-only banner shown on a launched simulation. A launched simulation is frozen:
 * its logic map / scope can only be edited while the simulation is in Draft (see ADR-005).
 */
interface Props { message?: string }

const LogicReadOnlyBanner: FunctionComponent<Props> = ({ message }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  return (
    <AlertBanner color={theme.palette.info.main} title={t('Read only')}>
      <div style={{
        alignItems: 'center',
        display: 'flex',
        gap: theme.spacing(1),
      }}
      >
        <Lock fontSize="small" />
        <Typography variant="body2">
          {message ?? t('This simulation has been launched. Its logic map is read-only. Reset the simulation to edit it, or update the scenario and run it again.')}
        </Typography>
      </div>
    </AlertBanner>
  );
};

export default LogicReadOnlyBanner;
