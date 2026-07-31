import { Add, Circle } from '@mui/icons-material';
import { Box, Button, Chip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo } from 'react';

import { useFormatter } from '../../../../components/i18n';
import AlertBanner from '../../common/AlertBanner';
import { type ConditionGroup, formatConditionKeyLabel } from './events/event-types';
import type { EventMeta } from './types';
import { useOutputProviders } from './useOutputProviders';

interface Props {
  eventMetas: Record<string, EventMeta>;
  onAddCompatibleAction: (field: string) => void;
}

/** Recursively collect all leaf-condition field values from a ConditionGroup tree. */
const collectFields = (group: ConditionGroup): string[] => [
  ...group.conditions.map(c => c.field),
  ...group.subGroups.flatMap(sg => collectFields(sg)),
];

/**
 * Displays a collapsible warning banner listing every
 * (event, field) pair where the field is referenced by a condition but not
 * produced by any action currently on the Logic Map.
 */
const LogicWarningBanner: FunctionComponent<Props> = ({ eventMetas, onAddCompatibleAction }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { providers } = useOutputProviders();

  const unprovisionedItems = useMemo(() => {
    const items: Array<{
      eventId: string;
      eventName: string;
      field: string;
    }> = [];

    for (const meta of Object.values(eventMetas)) {
      // Flatten all condition fields across every condition group (including subgroups).
      const allFields = meta.formData.conditionGroups.flatMap(collectFields);

      // Deduplicate (eventId, field) pairs so the same missing field is reported only once per event.
      const reportedKeys = new Set<string>();

      for (const field of allFields) {
        if (!field) continue;

        const key = `${meta.eventId}::${field}`;

        // Report only if the field has no provider on the current canvas and hasn't been reported yet.
        if (!providers[field] && !reportedKeys.has(key)) {
          reportedKeys.add(key);
          items.push({
            eventId: meta.eventId,
            eventName: meta.formData.name,
            field,
          });
        }
      }
    }
    return items;
  }, [eventMetas, providers]);

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
