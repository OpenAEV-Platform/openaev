import { Chip } from '@mui/material';
import { alpha } from '@mui/material/styles';

import { useFormatter } from '../../../../../../../../components/i18n';
import { expectationTypeColor, expectationTypeIcon } from '../../../../../../common/ExpectationIconByType';

// Human label per expectation type (falls back to the raw value humanized).
const EXPECTATION_TYPE_LABELS: Record<string, string> = {
  PREVENTION: 'Prevention',
  DETECTION: 'Detection',
  VULNERABILITY: 'Vulnerability',
  HUMAN_RESPONSE: 'Human Response',
  MANUAL: 'Manual',
  ARTICLE: 'Article',
  CHALLENGE: 'Challenge',
};

const ExpectationTypeChip = ({ type }: { type?: string }) => {
  const { t } = useFormatter();
  if (!type) {
    return <span>-</span>;
  }
  const key = type.toUpperCase();
  const color = expectationTypeColor(key);
  const Icon = expectationTypeIcon(key);
  return (
    <Chip
      icon={(
        <Icon style={{
          fontSize: 14,
          color,
        }}
        />
      )}
      label={t(EXPECTATION_TYPE_LABELS[key] ?? type)}
      size="small"
      variant="outlined"
      sx={{
        'height': 22,
        'fontSize': 11,
        'fontWeight': 600,
        'borderRadius': 1,
        'color': color,
        'borderColor': alpha(color, 0.4),
        'backgroundColor': alpha(color, 0.08),
        '& .MuiChip-icon': { marginLeft: 0.5 },
      }}
    />
  );
};

export default ExpectationTypeChip;
