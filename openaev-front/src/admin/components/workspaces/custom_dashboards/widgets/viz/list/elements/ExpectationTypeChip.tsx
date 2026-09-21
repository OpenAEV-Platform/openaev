import { Chip } from '@filigran/design-system';

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
      startIcon={(
        <Icon style={{
          fontSize: 14,
          color,
        }}
        />
      )}
      label={t(EXPECTATION_TYPE_LABELS[key] ?? type)}
      color={color}
    />
  );
};

export default ExpectationTypeChip;
