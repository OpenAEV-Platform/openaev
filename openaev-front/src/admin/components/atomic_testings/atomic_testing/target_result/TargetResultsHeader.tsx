import { ChevronLeftOutlined, ChevronRightOutlined, OpenInNewOutlined } from '@mui/icons-material';
import { Box, Button, IconButton, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../../../../../components/i18n';
import { type InjectTarget } from '../../../../../utils/api-types';
import { getTargetOverviewLabel, getTargetOverviewUrl } from '../../../../../utils/target/TargetUtils';
import NewAtomicTestingResult from '../NewAtomicTestingResult';
import TargetIcon from '../TargetIcon';

interface Props {
  target: InjectTarget;
  // 1-based position of the selected target within the current page, and the
  // page size, used to drive the prev/next switcher. Optional so the header
  // still renders when no switching context is available.
  position?: number;
  total?: number;
  onSelectPrevious?: () => void;
  onSelectNext?: () => void;
}

const TYPE_LABELS: Record<string, string> = {
  ASSETS_GROUPS: 'Asset group',
  ASSETS: 'Asset',
  TEAMS: 'Team',
  PLAYERS: 'Player',
  AGENT: 'Agent',
  AI_TARGETS: 'AI target',
};

const TargetResultsHeader: FunctionComponent<Props> = ({ target, position, total, onSelectPrevious, onSelectNext }) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const overviewUrl = getTargetOverviewUrl(target);
  const overviewLabel = t(getTargetOverviewLabel(target));
  const typeLabel = t(TYPE_LABELS[target.target_type] ?? target.target_type);

  const canSwitch = !!(total && total > 1 && position);
  const canPrevious = canSwitch && position! > 1;
  const canNext = canSwitch && position! < total!;

  return (
    <header
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: theme.spacing(1.5),
        flexWrap: 'wrap',
        paddingBottom: theme.spacing(2),
        borderBottom: `1px solid ${alpha(theme.palette.text.primary, 0.05)}`,
      }}
    >
      <TargetIcon target={target} size={40} />
      <div style={{
        flex: 1,
        minWidth: 160,
      }}
      >
        <Typography
          sx={{
            fontSize: 16,
            fontWeight: 600,
            lineHeight: 1.3,
            wordBreak: 'break-word',
            fontFamily: theme.typography.h1.fontFamily,
          }}
        >
          {target?.target_name}
        </Typography>
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          mt: 0.5,
          flexWrap: 'wrap',
        }}
        >
          <Box
            component="span"
            sx={{
              fontSize: 10,
              fontWeight: 600,
              textTransform: 'uppercase',
              letterSpacing: '0.08em',
              color: 'text.secondary',
              border: `1px solid ${theme.palette.divider}`,
              borderRadius: 0.5,
              paddingInline: 0.75,
              paddingBlock: '1px',
            }}
          >
            {typeLabel}
          </Box>
          <NewAtomicTestingResult target={target} />
        </Box>
      </div>
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        flexShrink: 0,
      }}
      >
        {canSwitch && (
          <Box
            sx={{
              display: 'inline-flex',
              alignItems: 'center',
              border: `1px solid ${theme.palette.divider}`,
              borderRadius: 1,
            }}
          >
            <Tooltip title={t('Previous target')}>
              <span>
                <IconButton
                  size="small"
                  onClick={onSelectPrevious}
                  disabled={!canPrevious}
                  aria-label={t('Previous target')}
                >
                  <ChevronLeftOutlined fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
            <Typography
              component="span"
              aria-live="polite"
              sx={{
                fontSize: 12,
                color: 'text.secondary',
                paddingInline: 0.5,
                fontVariantNumeric: 'tabular-nums',
                userSelect: 'none',
              }}
            >
              {t('{current} / {total}', {
                current: position,
                total,
              })}
            </Typography>
            <Tooltip title={t('Next target')}>
              <span>
                <IconButton
                  size="small"
                  onClick={onSelectNext}
                  disabled={!canNext}
                  aria-label={t('Next target')}
                >
                  <ChevronRightOutlined fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
          </Box>
        )}
        {overviewUrl && (
          <Button
            variant="outlined"
            color="primary"
            size="small"
            startIcon={<OpenInNewOutlined />}
            component={Link}
            to={overviewUrl}
          >
            {overviewLabel}
          </Button>
        )}
      </Box>
    </header>
  );
};

export default TargetResultsHeader;
