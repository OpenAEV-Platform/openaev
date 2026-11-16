import { Chip } from '@filigran/design-system';

import { useFormatter } from '../../../../../../../../components/i18n';
import { expectationTypeColor, expectationTypeIcon } from '../../../../../../common/ExpectationIconByType';
import { expectationTypeLabelKey } from '../../../../../../common/injects/expectations/Expectation';

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
      label={t(expectationTypeLabelKey(key) ?? type)}
      color={color}
    />
  );
};

export default ExpectationTypeChip;
