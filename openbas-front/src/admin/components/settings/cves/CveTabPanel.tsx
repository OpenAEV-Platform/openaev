import { Box, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type CveOutput } from '../../../../utils/api-types';
import { type CveStatus } from './CveDetail';

interface CveTabPanelProps {
  status: CveStatus;
  cve: CveOutput | null;
  children: React.ReactNode;
}

const CveTabPanel = ({ status, cve, children }: CveTabPanelProps) => {
  const theme = useTheme();
  const { t } = useFormatter();

  switch (status) {
    case 'loading':
      return <Loader />;
    case 'notAvailable':
      return (
        <Box padding={theme.spacing(2, 1, 0, 0)}>
          <Typography variant="body2" gutterBottom>
            {t('There is no information about this vulnerability yet.')}
          </Typography>
        </Box>
      );
    case 'loaded':
      return cve ? <>{children}</> : null;
    default:
      return null;
  }
};

export default CveTabPanel;
