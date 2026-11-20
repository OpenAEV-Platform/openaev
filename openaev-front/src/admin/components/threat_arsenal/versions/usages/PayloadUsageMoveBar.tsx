import { Button, Spinner, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { fdsTableSectionStyle } from '../../../../../components/common/fdsTableSpec';
import { useFormatter } from '../../../../../components/i18n';
import PayloadVersionSelect from '../PayloadVersionSelect';
import { type UsageMove } from './useUsageMove';

interface Props { move: UsageMove }

const PayloadUsageMoveBar: FunctionComponent<Props> = ({ move }) => {
  const { t } = useFormatter();
  const theme = useTheme();

  return (
    <div style={{
      ...fdsTableSectionStyle(theme),
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'flex-end',
      gap: theme.spacing(1),
    }}
    >
      {move.targets.length === 0
        ? <Text variant="content-compact" className="text-default-secondary">{t('No other version to move to')}</Text>
        : (
            <>
              <Text variant="content-compact" className="text-default-secondary" style={{ marginRight: 'auto' }}>
                {t('{count} selected', { count: String(move.chosenCount) })}
              </Text>
              <Text variant="content-compact">{t('Move to')}</Text>
              <PayloadVersionSelect value={move.target} versions={move.targets} onChange={move.setTarget} />
              <Button disabled={!move.canMove} onClick={move.move}>
                {move.moving ? <Spinner size="sm" /> : t('Move')}
              </Button>
            </>
          )}
    </div>
  );
};

export default PayloadUsageMoveBar;
