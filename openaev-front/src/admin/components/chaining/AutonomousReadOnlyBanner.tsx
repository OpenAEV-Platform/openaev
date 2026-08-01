import { AutoAwesomeOutlined } from '@mui/icons-material';
import { Alert } from '@mui/material';

import { useFormatter } from '../../../components/i18n';

// Shown on the Scope and Logic tabs of an autonomous (AI-driven) run: those tabs are exposed for
// inspection only - the orchestrator provisions and drives the attack path, so nothing is editable.
const AutonomousReadOnlyBanner = () => {
  const { t } = useFormatter();
  return (
    <Alert
      severity="info"
      icon={<AutoAwesomeOutlined fontSize="inherit" />}
      variant="outlined"
      sx={{ marginBottom: 2 }}
    >
      {t('Read-only view. This attack path is provisioned and driven by the AI orchestrator; use it to understand what the orchestrator is doing and where.')}
    </Alert>
  );
};

export default AutonomousReadOnlyBanner;
