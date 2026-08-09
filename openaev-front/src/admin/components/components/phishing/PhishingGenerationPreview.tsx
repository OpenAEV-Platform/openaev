import { CodeOutlined, VisibilityOutlined } from '@mui/icons-material';
import { Box, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo, useState } from 'react';

import CodeEditor from '../../../../components/fields/CodeEditor';
import { useFormatter } from '../../../../components/i18n';
import { parseAgentJson } from './phishingAiJson';
import PhishingHtmlPreview from './PhishingHtmlPreview';

export type PhishingGenerationVariant = 'landing' | 'email';

interface Props {
  raw: string;
  loading: boolean;
  variant: PhishingGenerationVariant;
}

interface ParsedResult {
  previewDocument: string;
  chromeSubject?: string;
  tabs: {
    key: string;
    label: string;
    language: string;
    code: string;
  }[];
}

const landingDocument = (html: string, css: string) =>
  `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><style>html,body{margin:0;padding:0;background:#ffffff;}</style><style>${css}</style></head><body>${html}</body></html>`;

const emailDocument = (html: string) =>
  `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><style>html,body{margin:0;padding:16px;background:#ffffff;color:#111111;font-family:Arial,Helvetica,sans-serif;}</style></head><body>${html}</body></html>`;

/**
 * Renders an AI generation result the way an operator actually reads it: a live
 * rendered preview (what the recipient will see) with a toggle to inspect the
 * highlighted HTML / CSS source. While the stream is still arriving and not yet
 * parseable, it shows the incoming text so there is immediate feedback - never
 * a raw JSON blob as the final deliverable.
 */
const PhishingGenerationPreview: FunctionComponent<Props> = ({ raw, loading, variant }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const [view, setView] = useState<'preview' | 'code'>('preview');
  const [codeTab, setCodeTab] = useState(0);

  const parsed = useMemo<ParsedResult | null>(() => {
    const json = parseAgentJson(raw);
    if (!json) return null;
    if (variant === 'landing') {
      const html = typeof json.html === 'string' ? json.html : '';
      const css = typeof json.css === 'string' ? json.css : '';
      if (!html && !css) return null;
      return {
        previewDocument: landingDocument(html, css),
        tabs: [
          {
            key: 'html',
            label: 'HTML',
            language: 'html',
            code: html,
          },
          {
            key: 'css',
            label: 'CSS',
            language: 'css',
            code: css,
          },
        ],
      };
    }
    const htmlBody = typeof json.html_body === 'string' ? json.html_body : '';
    const textBody = typeof json.text_body === 'string' ? json.text_body : '';
    const subject = typeof json.subject === 'string' ? json.subject : undefined;
    if (!htmlBody && !textBody) return null;
    return {
      previewDocument: emailDocument(htmlBody),
      chromeSubject: subject,
      tabs: [
        {
          key: 'html',
          label: 'HTML',
          language: 'html',
          code: htmlBody,
        },
        {
          key: 'text',
          label: t('Text'),
          language: 'markup',
          code: textBody,
        },
      ],
    };
  }, [raw, variant, t]);

  // Stream still arriving and not yet a valid object: show the raw text as it
  // lands so the dialog feels alive, with a light "generating" affordance.
  if (!parsed) {
    return (
      <Box
        sx={{
          border: `1px solid ${alpha(theme.palette.text.primary, 0.12)}`,
          borderRadius: 1,
          backgroundColor: theme.palette.background.code ?? theme.palette.background.paper,
          padding: 1.5,
          minHeight: 160,
        }}
      >
        <Typography
          variant="caption"
          sx={{
            display: 'block',
            marginBottom: 1,
            color: 'text.secondary',
          }}
        >
          {loading ? t('Generating the preview...') : t('Waiting for a valid result...')}
        </Typography>
        <Box
          component="pre"
          sx={{
            margin: 0,
            fontFamily: 'Consolas, Monaco, "Courier New", monospace',
            fontSize: 12,
            lineHeight: 1.6,
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
            color: 'text.secondary',
            maxHeight: 280,
            overflow: 'auto',
          }}
        >
          {raw}
        </Box>
      </Box>
    );
  }

  const activeTab = parsed.tabs[Math.min(codeTab, parsed.tabs.length - 1)];
  const subjectChrome = variant === 'email' && parsed.chromeSubject
    ? (
        <Box sx={{
          borderBottom: '1px solid rgba(0,0,0,0.08)',
          backgroundColor: '#f7f8fa',
          px: 2,
          py: 1.25,
        }}
        >
          <Typography sx={{
            fontSize: 13,
            fontWeight: 600,
            color: 'rgba(0,0,0,0.87)',
          }}
          >
            {parsed.chromeSubject}
          </Typography>
        </Box>
      )
    : undefined;

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 1.5,
    }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 1,
      }}
      >
        <ToggleButtonGroup
          size="small"
          exclusive
          value={view}
          onChange={(_, next) => next && setView(next)}
          sx={{ '& .MuiToggleButton-root.Mui-selected .MuiSvgIcon-root': { color: 'primary.main' } }}
        >
          <ToggleButton value="preview">
            <VisibilityOutlined sx={{
              fontSize: 16,
              marginRight: 0.75,
            }}
            />
            {t('Preview')}
          </ToggleButton>
          <ToggleButton value="code">
            <CodeOutlined sx={{
              fontSize: 16,
              marginRight: 0.75,
            }}
            />
            {t('Code')}
          </ToggleButton>
        </ToggleButtonGroup>
        {loading && (
          <Typography variant="caption" sx={{ color: 'ai.main' }}>
            {t('Generating the preview...')}
          </Typography>
        )}
      </Box>

      {view === 'preview'
        ? (
            <PhishingHtmlPreview
              title={t('Preview')}
              iframeTitle={t('Preview')}
              srcDoc={parsed.previewDocument}
              chrome={subjectChrome}
              height={360}
            />
          )
        : (
            <Box sx={{
              display: 'flex',
              flexDirection: 'column',
              gap: 1,
            }}
            >
              {parsed.tabs.length > 1 && (
                <ToggleButtonGroup
                  size="small"
                  exclusive
                  value={activeTab.key}
                  onChange={(_, next) => {
                    if (!next) return;
                    setCodeTab(parsed.tabs.findIndex(tab => tab.key === next));
                  }}
                >
                  {parsed.tabs.map(tab => (
                    <ToggleButton key={tab.key} value={tab.key}>{tab.label}</ToggleButton>
                  ))}
                </ToggleButtonGroup>
              )}
              <CodeEditor
                value={activeTab.code}
                language={activeTab.language}
                badge={activeTab.label}
                readOnly
                minHeight={320}
              />
            </Box>
          )}
    </Box>
  );
};

export default PhishingGenerationPreview;
