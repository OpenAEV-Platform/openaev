import { Box, Card, CardActionArea, CardContent, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../components/i18n';
import PlatformIcon from '../../../components/PlatformIcon';
import { type Executor } from '../../../utils/api-types';
import EEChip from '../common/entreprise_edition/EEChip';
import ExecutorBanner from './ExecutorBanner';

const useStyles = makeStyles()(theme => ({
  card: {
    overflow: 'hidden',
    height: 250,
  },
  area: {
    height: '100%',
    width: '100%',
  },
  content: {
    position: 'relative',
    padding: theme.spacing(0),
    textAlign: 'center',
    height: '100%',
  },
}));

interface ExecutorSelectorProps {
  executor: Executor;
  setSelectedExecutor: (executor: Executor) => void;
  isEEExecutor?: boolean;
}

const ExecutorSelector: React.FC<ExecutorSelectorProps> = ({ executor, setSelectedExecutor, isEEExecutor = false }) => {
  const theme = useTheme();
  const { classes } = useStyles();
  const { t } = useFormatter();

  const platforms = executor.executor_platforms || [];

  const openInstall = () => {
    setSelectedExecutor(executor);
  };

  return (
    <Card classes={{ root: classes.card }} variant="outlined">
      <CardActionArea
        classes={{ root: classes.area }}
        onClick={openInstall}
        disabled={platforms.length === 0}
      >
        <CardContent classes={{ root: classes.content }}>
          <ExecutorBanner executor={executor} height={140} />
          <Typography
            variant="h6"
            sx={{
              fontSize: 15,
              padding: theme.spacing(2, 0, 1),
              display: 'flex',
              justifyContent: 'center',
              alignItems: 'center',
              color: platforms.length === 0 ? theme.palette.text?.disabled : theme.palette.text?.primary,
            }}
          >
            {`${t('Install')} ${executor.executor_name}`}
            {isEEExecutor && <EEChip style={{ marginLeft: theme.spacing(1) }} />}
          </Typography>
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            {platforms.map((platform, index) => (
              <Card
                key={index}
                variant="outlined"
                sx={{
                  marginLeft: theme.spacing(1),
                  padding: theme.spacing(1),
                  display: 'flex',
                }}
              >
                <PlatformIcon platform={platform} width={20} />
              </Card>
            ))}
          </Box>
        </CardContent>
      </CardActionArea>
    </Card>
  );
};

export default ExecutorSelector;
