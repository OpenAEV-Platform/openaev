import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../../components/i18n';
import type { CveOutput } from '../../../../utils/api-types';

interface Props { cve: CveOutput }

const RemediationInfoTab = ({ cve }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();

  return (
    <div style={{ padding: theme.spacing(2, 1, 0, 0) }}>
      <Typography variant="subtitle2" gutterBottom>
        {t('Vulnerability Remediation')}
      </Typography>
      <pre>
        <Typography variant="body2" gutterBottom>
          {cve?.cve_remediation ?? t('There is no information yet on a vulnerability remediation for this vulnerability.')}
        </Typography>
      </pre>
    </div>
  );
};
export default RemediationInfoTab;
