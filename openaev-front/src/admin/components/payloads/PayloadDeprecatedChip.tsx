import { Chip, Tooltip } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../components/i18n';
import { type PayloadSimple } from '../../../utils/api-types';

interface Props { status?: PayloadSimple['payload_status'] }

/**
 * Compact inline "Deprecated" chip shown next to injects whose contract payload
 * is deprecated (issue #3839). Renders nothing for any other status so callers
 * can pass the payload status unconditionally.
 */
const PayloadDeprecatedChip = ({ status }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();

  if (status !== 'DEPRECATED') {
    return null;
  }
  return (
    <Tooltip title={t('Deprecated: Functionality not guaranteed')}>
      <Chip
        size="small"
        variant="outlined"
        label={t('Deprecated')}
        sx={{
          borderRadius: 1,
          height: 20,
          fontSize: 11,
          color: theme.palette.text.disabled,
          borderColor: alpha(theme.palette.text.disabled, 0.4),
          // Never let the chip shrink when rendered next to a truncating title
          flexShrink: 0,
        }}
      />
    </Tooltip>
  );
};

export default PayloadDeprecatedChip;
