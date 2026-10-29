import { Button } from '@filigran/design-system';
import { Add } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../../components/i18n';
import LogicWarningBanner from './LogicWarningBanner';
import type { EventMeta } from './types';

interface LogicTopBarProps {
  eventMetas: Record<string, EventMeta>;
  onAddCompatibleAction: (field: string) => void;
  onAddComponent: () => void;
  readOnly?: boolean;
}

const LogicTopBar = ({ eventMetas, onAddCompatibleAction, onAddComponent, readOnly = false }: LogicTopBarProps) => {
  const { t } = useFormatter();
  const theme = useTheme();

  return (
    <div
      style={{
        alignItems: 'stretch',
        display: 'flex',
        flexDirection: 'column',
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
      {!readOnly && (
        <Button type="button" startIcon={<Add fontSize="small" />} onClick={onAddComponent} style={{ alignSelf: 'flex-end' }}>
          {t('Add component')}
        </Button>
      )}
    </div>
  );
};

export default LogicTopBar;
