import { CloseOutlined, FullscreenOutlined } from '@mui/icons-material';
import { Box, Dialog, DialogContent, DialogTitle, IconButton, Paper, Tooltip, Typography } from '@mui/material';
import { type ReactNode, useCallback, useState } from 'react';

import { SECTION_LABEL_SX } from '../../../../components/common/detail/detailStyles';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';

interface Props {
  /** Section overline (e.g. "Preview"). */
  title: string;
  /** Accessible name for the iframe. */
  iframeTitle: string;
  /** Full HTML document (doctype + head + body) rendered inside the iframe. */
  srcDoc: string;
  /** Optional chrome rendered above the iframe (e.g. email From / Subject bar). */
  chrome?: ReactNode;
  /** Inline preview height: a px number, or a CSS length string (e.g. "100%")
   * to fill a sized parent (used by the full-page editor's sticky pane).
   * Fullscreen always fills the dialog. */
  height?: number | string;
}

/**
 * Shared phishing HTML preview: always paints the iframe on a light canvas so
 * recipient-facing pages and emails stay readable in dark theme, and exposes a
 * fullscreen dialog so operators can inspect the exact recipient experience.
 */
const PhishingHtmlPreview = ({ title, iframeTitle, srcDoc, chrome, height = 560 }: Props) => {
  const { t } = useFormatter();
  const [fullscreen, setFullscreen] = useState(false);
  const openFullscreen = useCallback(() => setFullscreen(true), []);
  const closeFullscreen = useCallback(() => setFullscreen(false), []);

  const frame = (frameHeight: number | string) => (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      height: typeof frameHeight === 'number' ? undefined : '100%',
      minHeight: 0,
      flex: typeof frameHeight === 'number' ? undefined : 1,
    }}
    >
      {chrome}
      <Box
        sx={{
          // Fixed light surface on purpose: phishing pages and lure emails are
          // authored for recipient browsers (almost always a light canvas).
          // Letting the app's dark paper bleed through made dark text illegible.
          backgroundColor: '#ffffff',
          flex: typeof frameHeight === 'number' ? undefined : 1,
          minHeight: 0,
          display: 'flex',
        }}
      >
        <iframe
          title={iframeTitle}
          srcDoc={srcDoc}
          sandbox=""
          style={{
            width: '100%',
            height: frameHeight,
            border: 0,
            display: 'block',
            backgroundColor: '#ffffff',
          }}
        />
      </Box>
    </Box>
  );

  return (
    <>
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
      }}
      >
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          minHeight: 32,
          marginBottom: 1.5,
        }}
        >
          <Typography sx={{
            ...SECTION_LABEL_SX,
            marginBottom: 0,
          }}
          >
            {title}
          </Typography>
          <Typography sx={{
            fontSize: 11,
            color: 'text.secondary',
          }}
          >
            {t('Matches what recipients see')}
          </Typography>
          <div style={{ flex: 1 }} />
          <Tooltip title={t('Fullscreen')}>
            <IconButton
              size="small"
              onClick={openFullscreen}
              aria-label={t('Fullscreen')}
              sx={{
                width: 32,
                height: 32,
              }}
            >
              <FullscreenOutlined fontSize="small" />
            </IconButton>
          </Tooltip>
        </Box>
        <Paper
          variant="outlined"
          sx={{
            padding: 0,
            borderRadius: 1,
            flex: 1,
            overflow: 'hidden',
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          {frame(height)}
        </Paper>
      </div>

      <Dialog
        open={fullscreen}
        onClose={closeFullscreen}
        fullScreen
        PaperProps={{ elevation: 1 }}
        TransitionComponent={Transition}
      >
        <DialogTitle sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          borderBottom: theme => `1px solid ${theme.palette.divider}`,
          py: 1.25,
        }}
        >
          <IconButton
            aria-label={t('Close')}
            onClick={closeFullscreen}
            size="large"
            color="primary"
          >
            <CloseOutlined fontSize="small" color="primary" />
          </IconButton>
          <Typography
            variant="h6"
            component="span"
            sx={{
              fontFamily: '"Geologica", sans-serif',
              fontWeight: 500,
            }}
          >
            {iframeTitle}
          </Typography>
        </DialogTitle>
        <DialogContent sx={{
          p: 0,
          display: 'flex',
          flexDirection: 'column',
          // Light canvas fills the whole fullscreen so dark theme never bleeds.
          backgroundColor: '#ffffff',
        }}
        >
          {frame('100%')}
        </DialogContent>
      </Dialog>
    </>
  );
};

export default PhishingHtmlPreview;
