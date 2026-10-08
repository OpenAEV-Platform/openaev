import { Text } from '@filigran/design-system';
import { Card, CardActionArea, CardContent } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../components/i18n';
import PlatformIcon from '../../../components/PlatformIcon';
import { type ExecutorOutput } from '../../../utils/api-types';
import EEChip from '../common/entreprise_edition/EEChip';
import ExecutorBanner from './ExecutorBanner';

interface ExecutorSelectorProps {
  executor: ExecutorOutput;
  setSelectedExecutor: (executor: ExecutorOutput) => void;
  showEEChip?: boolean;
}

const ExecutorSelector: React.FC<ExecutorSelectorProps> = ({ executor, setSelectedExecutor, showEEChip = false }) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const platforms = executor.executor_platforms || [];

  const openInstall = () => {
    setSelectedExecutor(executor);
  };

  return (
    <Card
      variant="outlined"
      sx={{
        overflow: 'hidden',
        height: 250,
      }}
    >
      <CardActionArea
        onClick={openInstall}
        disabled={platforms.length === 0}
        sx={{
          height: '100%',
          width: '100%',
        }}
      >
        {/* Column layout so the platform buttons sit at the same height on every card, whether the
            "Install <executor>" title takes one line or wraps to two (e.g. Microsoft Defender for Endpoint). */}
        <CardContent
          sx={{
            position: 'relative',
            padding: 0,
            textAlign: 'center',
            height: '100%',
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          <ExecutorBanner executor={executor} height={140} />
          <div
            style={{
              flex: 1,
              display: 'flex',
              justifyContent: 'center',
              alignItems: 'center',
              padding: theme.spacing(0.5, 2),
            }}
          >
            <Text
              variant="content-base-bold"
              className={platforms.length === 0 ? 'text-default-disabled' : undefined}
            >
              {`${t('Install')} ${executor.executor_name}`}
            </Text>
            {showEEChip && <EEChip style={{ marginLeft: theme.spacing(1) }} />}
          </div>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              paddingBottom: theme.spacing(2),
            }}
          >
            {platforms.map((platform, index) => (
              <Card
                key={index}
                variant="outlined"
                sx={{
                  marginLeft: 1,
                  padding: 1,
                  display: 'flex',
                }}
              >
                <PlatformIcon platform={platform} width={20} />
              </Card>
            ))}
          </div>
        </CardContent>
      </CardActionArea>
    </Card>
  );
};

export default ExecutorSelector;
