import { Alert, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../components/i18n';

interface Props { remediation?: string }

// Dedicated tab (rather than a Field in the Cloud details grid) because OCSF/Prowler
// remediation guidance can be long, structured free text - it deserves its own reading
// space instead of competing for room in a compact key/value grid.
const OCSFRemediationTab = ({ remediation }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();

  if (!remediation) {
    return (
      <Alert severity="info" variant="outlined" sx={{ marginTop: 2 }}>
        {t('There is no remediation information for this finding.')}
      </Alert>
    );
  }

  return (
    <div style={{ padding: theme.spacing(2, 1, 0, 0) }}>
      <Typography
        variant="body2"
        sx={{
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
        }}
      >
        {remediation}
      </Typography>
    </div>
  );
};

export default OCSFRemediationTab;
