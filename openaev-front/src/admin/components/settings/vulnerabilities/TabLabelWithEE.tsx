import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../../components/i18n';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import EEChip from '../../common/entreprise_edition/EEChip';

const TabLabelWithEE = ({ label }: { label: string }) => {
  const { isValidated: isEE } = useEnterpriseEdition();
  const theme = useTheme();
  const { t } = useFormatter();

  return (
    <Box display="flex" alignItems="center">
      {label}
      {!isEE && (
        <EEChip
          style={{ marginLeft: theme.spacing(1) }}
          clickable
          featureDetectedInfo={t(label)}
        />
      )}
    </Box>
  );
};

export default TabLabelWithEE;
