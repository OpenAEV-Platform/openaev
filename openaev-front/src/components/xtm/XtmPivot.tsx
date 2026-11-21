import { Button, Chip, IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { CloseOutlined, LinkOffOutlined } from '@mui/icons-material';
import { Avatar, Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent, type ReactNode, useState } from 'react';

import { type CounterpartKindPath } from '../../actions/xtm/counterpart-actions';
import octiDark from '../../static/images/xtm/octi_dark.png';
import octiLight from '../../static/images/xtm/octi_light.png';
import useExplorationGraph from '../../utils/hooks/useExplorationGraph';
import { useAbility } from '../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
import { useFormatter } from '../i18n';
import { keyFact, OPENCTI_FACT_LABELS } from './counterpartFacts';
import { type CounterpartAnswer } from './counterpartStore';
import useCounterpart, { OPENCTI_PRODUCT } from './useCounterpart';
import { PivotNoMatch, PivotPreview, PivotTeaser } from './XtmPivotPreview';

// One visual language for every cross-platform pivot of OpenAEV: the product mark, "Open in
// OpenCTI" when the counterpart exists, a muted "Not in OpenCTI" when it does not, and a discreet
// "Connect OpenCTI" call to action when the tenant is not connected (configurers only; the others
// read a one-line hint). OpenCTI renders the mirror components with the same wording.

// The OpenCTI connection of a tenant is deployment configuration (openaev.xtm.opencti.{tenant}.*),
// so the call to action opens its documentation page.
export const CONNECT_OPENCTI_DOC_URL = 'https://docs.openaev.io/latest/integrations/xtm-suite/xtm-suite-connector/';
const DISMISS_KEY = 'xtm-connect-opencti-dismissed';

const isDismissed = () => {
  try {
    return window.localStorage.getItem(DISMISS_KEY) === 'true';
  } catch {
    return false;
  }
};

export const ProductMark: FunctionComponent<{ size?: number }> = ({ size = 18 }) => {
  const theme = useTheme();
  return (
    <Avatar
      src={theme.palette.mode === 'dark' ? octiDark : octiLight}
      alt=""
      style={{
        width: size,
        height: size,
      }}
    />
  );
};

// Read by a screen reader, never shown: the hint a disabled call to action carries.
const SR_ONLY = {
  position: 'absolute',
  width: 1,
  height: 1,
  overflow: 'hidden',
  clip: 'rect(0 0 0 0)',
  whiteSpace: 'nowrap',
} as const;

const useCanConfigure = () => {
  const ability = useAbility();
  return ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
};

// One size per slot in the three products: the hero pivot is the FDS Button md (36 px), the size of
// its header siblings (Explore in graph is an FDS IconButton md) and of the same pivot in OpenCTI
// and OpenCRQ. "sm" stays available for a dense panel.
export type PivotSize = 'sm' | 'md';

/** Not connected: the preview of what OpenCTI would show here, and the next step for this user. */
const OpenCtiTeaser: FunctionComponent<{ canConfigure: boolean }> = ({ canConfigure }) => {
  const { t } = useFormatter();
  return (
    <PivotTeaser
      mark={<ProductMark />}
      productName="OpenCTI"
      labels={OPENCTI_FACT_LABELS}
      pitch={t('Connect OpenCTI to see the reports and intelligence on this object.')}
      next={canConfigure ? t('Open the connection guide to connect OpenCTI.') : t('Ask an administrator to connect OpenCTI to see this object there.')}
    />
  );
};

/** The "Connect OpenCTI" call to action, shown where a pivot would be when not connected. */
export const ConnectOpenCtiCta: FunctionComponent<{
  variant?: 'button' | 'inline';
  size?: PivotSize;
}> = ({ variant = 'button', size = 'md' }) => {
  const { t } = useFormatter();
  const canConfigure = useCanConfigure();
  const [dismissed, setDismissed] = useState(isDismissed);
  if (dismissed) {
    return null;
  }
  const dismiss = () => {
    try {
      window.localStorage.setItem(DISMISS_KEY, 'true');
    } catch {
      // Storage refused: dismissed for this view only.
    }
    setDismissed(true);
  };
  if (!canConfigure) {
    // A disabled button fires no pointer event: the teaser hangs on a focusable wrapper.
    return (
      <Tooltip>
        <TooltipTrigger asChild>
          <Box component="span" tabIndex={0} sx={{ display: 'inline-flex' }} data-testid="xtm-connect-hint">
            <Button priority="secondary" size={size} disabled>
              <ProductMark />
              {t('Connect OpenCTI')}
            </Button>
            <Box component="span" sx={SR_ONLY}>{t('Ask an administrator to connect OpenCTI to see this object there.')}</Box>
          </Box>
        </TooltipTrigger>
        <TooltipContent><OpenCtiTeaser canConfigure={false} /></TooltipContent>
      </Tooltip>
    );
  }
  return (
    <Box
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 0.5,
      }}
      data-testid="xtm-connect-cta"
    >
      <Tooltip>
        <TooltipTrigger asChild>
          <Button asChild priority="secondary" size={size}>
            <a href={CONNECT_OPENCTI_DOC_URL} target="_blank" rel="noopener noreferrer">
              <ProductMark />
              {t('Connect OpenCTI')}
            </a>
          </Button>
        </TooltipTrigger>
        <TooltipContent><OpenCtiTeaser canConfigure /></TooltipContent>
      </Tooltip>
      {variant === 'button' && (
        <Tooltip>
          <TooltipTrigger asChild>
            <IconButton
              icon={<CloseOutlined fontSize="small" />}
              onClick={dismiss}
              aria-label={t('Hide this suggestion')}
              priority="tertiary"
              size="sm"
            />
          </TooltipTrigger>
          <TooltipContent>{t('Hide this suggestion')}</TooltipContent>
        </Tooltip>
      )}
    </Box>
  );
};

interface PivotProps {
  kind: CounterpartKindPath;
  objectKey: string | null | undefined;
}

/** The preview of an OpenCTI match: what the click opens, with the facts OpenCTI answered. */
export const OpenCtiMatchPreview: FunctionComponent<{ answer: CounterpartAnswer }> = ({ answer }) => {
  const { t } = useFormatter();
  return (
    <PivotPreview
      mark={<ProductMark />}
      productName="OpenCTI"
      entityType={answer.counterpart?.counterpart_entity_type}
      name={answer.counterpart?.counterpart_name}
      facts={answer.counterpart?.counterpart_facts}
      labels={OPENCTI_FACT_LABELS}
      action={t('See the reports, relationships and context of this object in OpenCTI.')}
    />
  );
};

/** Renders the answer once loaded; kept separate so tests drive every state. */
export const OpenCtiPivotView: FunctionComponent<{
  answer: CounterpartAnswer;
  size?: PivotSize;
}> = ({ answer, size = 'md' }) => {
  const { t } = useFormatter();
  switch (answer.state) {
    case 'match': {
      // The key figure rides next to the label when OpenCTI answered one; the FDS button never wraps.
      const reports = keyFact(answer.counterpart?.counterpart_facts, 'reports');
      return (
        <Tooltip>
          <TooltipTrigger asChild>
            <Button asChild priority="secondary" size={size}>
              <a href={answer.counterpart?.counterpart_url} target="_blank" rel="noopener noreferrer" data-testid="xtm-pivot-open">
                <ProductMark />
                {t('Open in OpenCTI')}
                {reports && (
                  <Box component="span" sx={{ opacity: 0.72 }} data-testid="xtm-pivot-figure">
                    {`· ${t('Reports')} ${reports.fact_value}`}
                  </Box>
                )}
              </a>
            </Button>
          </TooltipTrigger>
          <TooltipContent><OpenCtiMatchPreview answer={answer} /></TooltipContent>
        </Tooltip>
      );
    }
    case 'not_connected':
      return <ConnectOpenCtiCta size={size} />;
    case 'no_match':
      return (
        <Tooltip>
          <TooltipTrigger asChild>
            <Box component="span" tabIndex={0} sx={{ display: 'inline-flex' }} data-testid="xtm-pivot-none">
              <Button priority="secondary" size={size} disabled>
                <ProductMark />
                {t('Not in OpenCTI')}
              </Button>
            </Box>
          </TooltipTrigger>
          <TooltipContent>
            <PivotNoMatch
              mark={<ProductMark />}
              productName="OpenCTI"
              explanation={t('OpenCTI holds no intelligence on this object yet: it appears there once a connector or a report brings it in.')}
            />
          </TooltipContent>
        </Tooltip>
      );
    case 'unavailable':
      return (
        <Tooltip>
          <TooltipTrigger asChild>
            <Box component="span" tabIndex={0} sx={{ display: 'inline-flex' }} data-testid="xtm-pivot-unavailable">
              <Button priority="secondary" size={size} disabled>
                <LinkOffOutlined fontSize="small" />
                {t('OpenCTI is unavailable')}
              </Button>
            </Box>
          </TooltipTrigger>
          <TooltipContent>{t('OpenCTI did not answer this time. The pivot comes back on its own.')}</TooltipContent>
        </Tooltip>
      );
    default:
      return null;
  }
};

/** Hero action: "Open in OpenCTI", "Not in OpenCTI" or "Connect OpenCTI". */
const OpenCtiPivotLookup: FunctionComponent<PivotProps & { size?: PivotSize }> = ({ kind, objectKey, size }) => {
  const answer = useCounterpart(kind, objectKey, OPENCTI_PRODUCT);
  return <OpenCtiPivotView answer={answer} size={size} />;
};

// Width of the mark slot a row keeps when its siblings may carry a mark, so the controls after it
// stay in one column whether this row matches or not.
export const ROW_MARK_SLOT_WIDTH = 24;

interface RowMarkProps extends PivotProps {
  label?: string;
  reserveSlot?: boolean;
}

/**
 * List-row chip: the product mark linking to the counterpart, nothing otherwise. The label names
 * the action ("Investigate in OpenCTI" on a collected alert), "Open in OpenCTI" by default.
 * With reserveSlot, a row of a connected tenant keeps an empty slot of the mark's width when it
 * has no counterpart, so controls placed after the mark line up on every row.
 */
const OpenCtiRowMarkLookup: FunctionComponent<RowMarkProps> = ({ kind, objectKey, label = 'Open in OpenCTI', reserveSlot = false }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const answer = useCounterpart(kind, objectKey, OPENCTI_PRODUCT);
  const slot = (content: ReactNode) => (
    <Box
      component="span"
      data-testid="xtm-row-mark-slot"
      sx={{
        display: 'inline-flex',
        justifyContent: 'center',
        alignItems: 'center',
        width: ROW_MARK_SLOT_WIDTH,
        flexShrink: 0,
      }}
    >
      {content}
    </Box>
  );
  if (answer.state !== 'match') {
    return reserveSlot && answer.state !== 'not_connected' ? slot(null) : null;
  }
  const mark = (
    <Tooltip>
      <TooltipTrigger asChild>
        <Box
          component="a"
          href={answer.counterpart?.counterpart_url}
          target="_blank"
          rel="noopener noreferrer"
          aria-label={t(label)}
          onClick={(e: MouseEvent) => e.stopPropagation()}
          data-testid="xtm-row-mark"
          sx={{
            'display': 'inline-flex',
            'borderRadius': '2px',
            // The OpenAEV global :focus reset removes the outline: a keyboard user still sees the mark.
            '&:focus-visible': {
              outline: `2px solid ${theme.palette.primary.main}`,
              outlineOffset: '2px',
            },
          }}
        >
          <ProductMark size={16} />
        </Box>
      </TooltipTrigger>
      <TooltipContent><OpenCtiMatchPreview answer={answer} /></TooltipContent>
    </Tooltip>
  );
  return reserveSlot ? slot(mark) : mark;
};

/** Overview card: "In OpenCTI" with the counterpart's name and type, or the CTA. */
const OpenCtiCardLookup: FunctionComponent<PivotProps & { children?: ReactNode }> = ({ kind, objectKey, children }) => {
  const { t } = useFormatter();
  const answer = useCounterpart(kind, objectKey, OPENCTI_PRODUCT);
  if (answer.state === 'loading') {
    return null;
  }
  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 1,
      }}
      data-testid="xtm-card"
    >
      {answer.state === 'match' ? (
        <>
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
          }}
          >
            <ProductMark />
            <Box
              component="span"
              sx={{
                typography: 'body2',
                fontWeight: 600,
              }}
            >
              {answer.counterpart?.counterpart_name}
            </Box>
            {answer.counterpart?.counterpart_entity_type && <Chip label={answer.counterpart.counterpart_entity_type} />}
          </Box>
          {children}
          <OpenCtiPivotView answer={answer} />
        </>
      ) : (
        <OpenCtiPivotView answer={answer} />
      )}
      {answer.state === 'no_match' && (
        <Box
          component="span"
          sx={{
            typography: 'caption',
            color: 'text.secondary',
          }}
        >
          {t('This object is not known in the connected OpenCTI.')}
        </Box>
      )}
    </Box>
  );
};

// The pivots belong to the exploration graph preview feature: off (the default), they draw nothing
// and never ask the backend (the lookup component, and its request, is not mounted).
export const OpenCtiRowMark: FunctionComponent<RowMarkProps> = props => (useExplorationGraph() ? <OpenCtiRowMarkLookup {...props} /> : null);
export const OpenCtiCard: FunctionComponent<PivotProps & { children?: ReactNode }> = props => (useExplorationGraph() ? <OpenCtiCardLookup {...props} /> : null);
const OpenCtiPivot: FunctionComponent<PivotProps & { size?: PivotSize }> = props => (useExplorationGraph() ? <OpenCtiPivotLookup {...props} /> : null);

export default OpenCtiPivot;
