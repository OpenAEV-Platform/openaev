import { IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { ContentCopyOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { copyToClipboard } from '../../../utils/utils';
import { useFormatter } from '../../i18n';

interface Props {
  content: string | null | undefined;
  language?: string;
}

/**
 * Shared monospace code block with a copy-to-clipboard button and an optional
 * language/label header. Extracted from ThreatArsenalActionOverview.
 */
const CodeBlock: FunctionComponent<Props> = ({ content, language }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const value = content ?? '';

  return (
    <Box
      sx={{
        position: 'relative',
        borderRadius: 1,
        border: `1px solid ${theme.palette.divider}`,
        backgroundColor: theme.palette.background.accent,
        overflow: 'hidden',
      }}
    >
      {language && (
        <Box sx={{
          paddingInline: 1.5,
          paddingBlock: 0.5,
          fontSize: 10.5,
          letterSpacing: '0.08em',
          textTransform: 'uppercase',
          color: 'text.secondary',
          borderBottom: `1px solid ${theme.palette.divider}`,
          backgroundColor: alpha(theme.palette.background.paper, 0.3),
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
        >
          <span>{language}</span>
        </Box>
      )}
      <Box
        component="pre"
        sx={{
          margin: 0,
          padding: 1.5,
          paddingRight: 5,
          fontFamily: 'Consolas, monaco, monospace',
          fontSize: 12,
          lineHeight: 1.6,
          color: 'text.primary',
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-all',
          overflow: 'auto',
          maxHeight: 320,
        }}
      >
        {value}
      </Box>
      <Tooltip>
        <TooltipTrigger asChild>
          <IconButton
            icon={<ContentCopyOutlined fontSize="small" />}
            priority="tertiary"
            size="sm"
            aria-label={t('Copy to clipboard')}
            onClick={(event) => {
              event.stopPropagation();
              copyToClipboard(t, value);
            }}
            style={{
              position: 'absolute',
              top: language ? 32 : 6,
              right: 6,
            }}

          />
        </TooltipTrigger>
        <TooltipContent>{t('Copy to clipboard')}</TooltipContent>
      </Tooltip>
    </Box>
  );
};

export default CodeBlock;
