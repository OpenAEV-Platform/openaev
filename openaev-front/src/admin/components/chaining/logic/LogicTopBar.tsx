import { Add } from '@mui/icons-material';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../../components/i18n';
import LogicWarningBanner from './LogicWarningBanner';
import type { EventMeta } from './types';

interface LogicTopBarProps {
  eventMetas: Record<string, EventMeta>;
  onAddCompatibleAction: (field: string) => void;
  onAddComponent: () => void;
}

const LogicTopBar = ({ eventMetas, onAddCompatibleAction, onAddComponent }: LogicTopBarProps) => {
  const { t } = useFormatter();
  const theme = useTheme();

  return (
    <div
      style={{
        alignItems: 'flex-start',
        display: 'flex',
        gap: theme.spacing(2),
        left: theme.spacing(1),
        position: 'absolute',
        right: theme.spacing(1),
        top: theme.spacing(1),
      }}
    >
      <div style={{
        flex: 1,
        minWidth: 0,
      }}
      >
        <LogicWarningBanner
          eventMetas={eventMetas}
          onAddCompatibleAction={onAddCompatibleAction}
        />
      </div>
      <Button
        color="primary"
        startIcon={<Add />}
        variant="contained"
        onClick={onAddComponent}
      >
        {t('Add component')}
      </Button>
    </div>
  );
};

export default LogicTopBar;
