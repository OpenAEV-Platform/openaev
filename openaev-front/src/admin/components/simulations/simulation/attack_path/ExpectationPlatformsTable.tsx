import { Popover, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ReactNode, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import ItemSecurityPlatformType from '../../../../../components/ItemSecurityPlatformType';
import StatusPill from '../../../atomic_testings/atomic_testing/target_result/StatusPill';

export interface ExpectationPlatformAlert {
  id: string;
  title: string;
  date?: string | null;
}

export interface ExpectationPlatformRow {
  key: string;
  icon?: ReactNode;
  name: string;
  type?: string;
  status?: string;
  statusLabel?: string;
  statusTone?: string;
  detectedAt?: string;
  alerts: ExpectationPlatformAlert[];
}

interface Props { rows: ExpectationPlatformRow[] }

const GRID_TEMPLATE_COLUMNS = 'minmax(180px, 2fr) 150px 140px 170px 80px';
const ROW_MIN_WIDTH = 720;

const ExpectationPlatformsTable = ({ rows }: Props) => {
  const theme = useTheme();
  const { t, fldt } = useFormatter();
  const normalizeType = (type?: string) => (type ?? '').trim().toLowerCase();
  const [popover, setPopover] = useState<{
    anchor: HTMLElement;
    row: ExpectationPlatformRow;
  } | null>(null);

  const headerSx = {
    fontSize: 11,
    textTransform: 'uppercase',
    letterSpacing: '0.08em',
    color: 'text.secondary',
    fontFamily: theme.typography.h1.fontFamily,
    whiteSpace: 'nowrap',
  } as const;

  return (
    <div style={{ overflowX: 'auto' }}>
      <div style={{ minWidth: ROW_MIN_WIDTH }}>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: GRID_TEMPLATE_COLUMNS,
            alignItems: 'center',
            columnGap: theme.spacing(1),
          }}
        >
          <Typography sx={headerSx}>{t('Security platforms')}</Typography>
          <Typography sx={headerSx}>{t('Type')}</Typography>
          <Typography sx={headerSx}>{t('Status')}</Typography>
          <Typography sx={headerSx}>{t('Detection time')}</Typography>
          <Typography sx={headerSx}>{t('Alerts')}</Typography>
        </div>

        {rows.map((row) => {
          const count = row.alerts.length;
          const rowType = normalizeType(row.type);
          const hasDisplayType = rowType.length > 0 && rowType !== 'collector';
          return (
            <div
              key={row.key}
              style={{
                display: 'grid',
                gridTemplateColumns: GRID_TEMPLATE_COLUMNS,
                alignItems: 'center',
                columnGap: theme.spacing(1),
                minHeight: 44,
                borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
              }}
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: theme.spacing(1),
                  minWidth: 0,
                }}
              >
                <div
                  style={{
                    width: 28,
                    height: 28,
                    borderRadius: 6,
                    flexShrink: 0,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    backgroundColor: alpha(theme.palette.text.primary, 0.04),
                  }}
                >
                  {row.icon}
                </div>
                <Typography
                  sx={{
                    fontSize: 13,
                    minWidth: 0,
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                >
                  {row.name}
                </Typography>
              </div>
              <div>
                {hasDisplayType ? <ItemSecurityPlatformType type={row.type ?? ''} /> : <Typography variant="body2">-</Typography>}
              </div>
              <div>
                {row.status
                  ? <StatusPill label={t(row.statusLabel ?? row.status)} status={row.statusTone ?? row.status} />
                  : <Typography variant="body2">-</Typography>}
              </div>
              <Typography sx={{
                fontSize: 13,
                fontVariantNumeric: 'tabular-nums',
              }}
              >
                {row.detectedAt ? fldt(row.detectedAt) : '-'}
              </Typography>
              {count > 0 ? (
                <Typography
                  component="button"
                  type="button"
                  onClick={e => setPopover({
                    anchor: e.currentTarget,
                    row,
                  })}
                  sx={{
                    border: 'none',
                    background: 'none',
                    padding: 0,
                    margin: 0,
                    textDecoration: 'underline',
                    color: 'primary.main',
                    cursor: 'pointer',
                    fontSize: 13,
                    textAlign: 'left',
                  }}
                >
                  {`${count} ${count === 1 ? t('alert') : t('alerts')}`}
                </Typography>
              ) : (
                <Typography variant="body2">-</Typography>
              )}
            </div>
          );
        })}
      </div>

      <Popover
        open={Boolean(popover)}
        anchorEl={popover?.anchor ?? null}
        onClose={() => setPopover(null)}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'left',
        }}
      >
        <div style={{
          padding: theme.spacing(1.5),
          minWidth: 240,
        }}
        >
          <Typography variant="subtitle2" gutterBottom>
            {`${t('Alerts')} (${popover?.row.alerts.length ?? 0})`}
          </Typography>
          {(popover?.row.alerts ?? []).map(alert => (
            <div
              key={alert.id}
              style={{
                padding: '4px 0',
                borderBottom: `1px solid ${theme.palette.divider}`,
              }}
            >
              <Typography variant="body2">{alert.title}</Typography>
              {alert.date && (
                <Typography variant="caption" color="text.secondary">{fldt(alert.date)}</Typography>
              )}
            </div>
          ))}
        </div>
      </Popover>
    </div>
  );
};

export default ExpectationPlatformsTable;
