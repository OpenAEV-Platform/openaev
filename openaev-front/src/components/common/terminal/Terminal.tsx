import { IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { ArrowDownward } from '@mui/icons-material';
import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Fragment, type ReactNode, useRef } from 'react';

import { useFormatter } from '../../i18n';
import { FONT_FAMILY_CODE } from '../../Theme';

export type TerminalLineLevel = 'default' | 'error' | 'warning' | 'info';

export interface TerminalLine {
  key: string;
  date?: ReactNode;
  content: ReactNode;
  level?: TerminalLineLevel;
}

interface TerminalProps {
  lines: TerminalLine[];
  maxHeight?: number;
}

const Terminal = ({ lines, maxHeight = 400 }: TerminalProps) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const isDark = theme.palette.mode === 'dark';

  const containerRef = useRef<HTMLDivElement | null>(null);

  const getColor = (level?: TerminalLine['level']) => {
    switch (level) {
      case 'error':
        return theme.palette.error.main;
      case 'warning':
        return theme.palette.warning.main;
      case 'info':
        return theme.palette.info.main;
      default:
        return isDark
          ? theme.palette.common.white
          : theme.palette.common.black;
    }
  };

  const scrollToBottom = () => {
    const el = containerRef.current;
    if (!el) return;

    el.scrollTo({
      top: el.scrollHeight,
      behavior: 'smooth',
    });
  };

  return (
    <Box sx={{ position: 'relative' }}>
      <div
        ref={containerRef}
        style={{
          background: isDark ? theme.palette.common.black : theme.palette.common.white,
          color: isDark ? theme.palette.common.white : theme.palette.common.black,
          fontFamily: FONT_FAMILY_CODE,
          padding: theme.spacing(2),
          borderRadius: theme.spacing(1),
          whiteSpace: 'pre-wrap',
          fontSize: theme.typography.h4.fontSize,
          overflowX: 'auto',
          maxHeight,
          display: 'grid',
          gridTemplateColumns: 'max-content 1fr',
          columnGap: theme.spacing(1),
          alignContent: 'start',
        }}
      >
        {lines.map(line => (
          <Fragment key={line.key}>
            {line.date && (
              <Box
                sx={{
                  color: 'text.secondary',
                  whiteSpace: 'nowrap',
                }}
              >
                {line.date}
              </Box>
            )}

            <Box
              sx={{
                color: getColor(line.level),
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
                gridColumn: line.date ? undefined : '1 / -1',
              }}
            >
              {line.content}
            </Box>
          </Fragment>
        ))}
      </div>
      <Tooltip>
        <TooltipTrigger asChild>
          <IconButton
            icon={<ArrowDownward fontSize="small" />}
            aria-label={t('Scroll to bottom')}
            onClick={scrollToBottom}
            style={{
              position: 'absolute',
              top: theme.spacing(),
              right: theme.spacing(2),
            }}
            priority="tertiary"
            size="sm"
          />
        </TooltipTrigger>
        <TooltipContent>{t('Scroll to bottom')}</TooltipContent>
      </Tooltip>
    </Box>
  );
};

export default Terminal;
