import { Add, Circle } from '@mui/icons-material';
import { Box, Button, Chip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo } from 'react';

import { useFormatter } from '../../../../components/i18n';
import AlertBanner from '../../common/AlertBanner';
import { formatConditionKeyLabel } from './events/event-types';
import { findUnprovisionedLogicWarningItems } from './logic-warning-utils';
import type { EventMeta } from './types';
import { useOutputProviders } from './useOutputProviders';

interface Props {
  eventMetas: Record<string, EventMeta>;
  onAddCompatibleAction: (field: string) => void;
}

/**
 * Displays a collapsible warning banner listing every
 * (event, field) pair where the field is referenced by a condition but not
 * produced by any action currently on the Logic Map.
 */
const LogicWarningBanner: FunctionComponent<Props> = ({ eventMetas, onAddCompatibleAction }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { providers } = useOutputProviders();

  const unprovisionedItems = useMemo(
    () => findUnprovisionedLogicWarningItems(eventMetas, providers),
    [eventMetas, providers],
  );

  if (unprovisionedItems.length === 0) return null;

  return (
    <AlertBanner color={theme.palette.warning.main} title={t('Warning')}>
      {unprovisionedItems.map(item => (
        <Box
          key={`${item.eventId}::${item.field}`}
          sx={{
            alignItems: 'center',
            display: 'flex',
            flexWrap: 'wrap',
            gap: 1,
            py: 0.5,
          }}
        >
          <Circle sx={{
            color: theme.palette.warning.main,
            height: '10px',
          }}
          />
          <Typography variant="body2">
            {`${t('Event')} "${item.eventName}" ${t('references field:')}`}
          </Typography>
          <Chip
            label={formatConditionKeyLabel(item.field)}
            size="small"
            sx={{
              backgroundColor: 'action.hover',
              fontWeight: 600,
            }}
          />
          <Typography variant="body2">{t('which is')}</Typography>
          <Typography fontWeight={800} variant="body2">
            {t('not provisioned by any action.')}
          </Typography>
          <Button
            size="small"
            startIcon={<Add />}
            onClick={() => onAddCompatibleAction(item.field)}
            sx={{
              fontWeight: 600,
              p: 0.5,
              textTransform: 'none',
              border: `1px solid ${theme.palette.divider}`,
            }}
          >
            {t('Add Compatible Action')}
          </Button>
        </Box>
      ))}
    </AlertBanner>
  );
};

export default LogicWarningBanner;
