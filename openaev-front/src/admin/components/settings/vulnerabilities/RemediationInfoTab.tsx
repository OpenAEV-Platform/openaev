import { Alert, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../../components/i18n';
import type { VulnerabilityOutput } from '../../../../utils/api-types';

interface Props { vulnerability: VulnerabilityOutput }

const RemediationInfoTab = ({ vulnerability }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const remediation = vulnerability?.vulnerability_remediation;

  if (!remediation) {
    return (
      <Alert severity="info" variant="outlined" style={{ marginTop: theme.spacing(2) }}>
        {t('There is no information yet on a vulnerability remediation for this vulnerability.')}
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
export default RemediationInfoTab;
