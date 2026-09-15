import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import EEChip from '../../common/entreprise_edition/EEChip';

const TabLabelWithEE = ({ label }: { label: string }) => {
  const { isValidated: isEE } = useEnterpriseEdition();
  const theme = useTheme();

  return (
    <Box component="span" display="inline-flex" alignItems="center">
      {/* The theme's MuiTab override lowercases the label and re-capitalizes it
          with ::first-letter - a rule that does not apply to flex containers.
          Keeping the text in an inline-block span restores the capital letter. */}
      <Box
        component="span"
        sx={{
          'display': 'inline-block',
          '&::first-letter': { textTransform: 'uppercase' },
        }}
      >
        {label}
      </Box>
      {/* Informational only: selecting the tab itself opens the licence dialog when the licence is not active. */}
      {!isEE && <EEChip style={{ marginLeft: theme.spacing(1) }} />}
    </Box>
  );
};

export default TabLabelWithEE;
