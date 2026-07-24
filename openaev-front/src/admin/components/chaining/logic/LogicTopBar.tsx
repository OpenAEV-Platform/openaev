import { Add } from '@mui/icons-material';
import { Button } from '@mui/material';

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

  return (
    <div
      style={{
        alignItems: 'flex-start',
        display: 'flex',
        gap: 8,
        left: 10,
        position: 'absolute',
        right: 10,
        top: 10,
        zIndex: 5,
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
