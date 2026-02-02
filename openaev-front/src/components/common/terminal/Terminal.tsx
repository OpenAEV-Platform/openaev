import { ArrowDownward } from '@mui/icons-material';
import { Box, IconButton, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type ReactNode, useRef } from 'react';

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
    <Box position="relative">
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
        }}
      >
        {lines.map(line => (
          <Box
            key={line.key}
            sx={{
              display: 'grid',
              gridTemplateColumns: line.date ? '200px 1fr' : '1fr',
              columnGap: 1,
            }}
          >
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
              }}
            >
              {line.content}
            </Box>
          </Box>
        ))}
      </div>
      <Tooltip title="Scroll to bottom">
        <IconButton
          size="small"
          onClick={scrollToBottom}
          sx={{
            position: 'absolute',
            top: theme.spacing(),
            right: theme.spacing(2),
          }}
        >
          <ArrowDownward fontSize="small" />
        </IconButton>
      </Tooltip>
    </Box>
  );
};

export default Terminal;
