import { Button, Chip, IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { CloseOutlined, LinkOffOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent, type ReactNode, useState } from 'react';

import { type CounterpartKindPath } from '../../actions/xtm/counterpart-actions';
import ocrqMark from '../../static/images/xtm/ocrq.svg';
import { type FactOutput } from '../../utils/api-types';
import useExplorationGraph from '../../utils/hooks/useExplorationGraph';
import { useAbility } from '../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
import { useFormatter } from '../i18n';
import { FACT_LABELS, keyFact, MONEY_FACTS, orderedFacts } from './counterpartFacts';
import { type CounterpartAnswer } from './counterpartStore';
import useCounterpart from './useCounterpart';
import { CONNECT_OPENCTI_DOC_URL, type PivotSize, ROW_MARK_SLOT_WIDTH } from './XtmPivot';
import { PivotNoMatch, PivotPreview, PivotTeaser } from './XtmPivotPreview';

// The OpenCRQ side of the cross-platform pivots: same visual language and wording as the OpenCTI
// ones (XtmPivot), the OpenCRQ mark, and the risk OpenCRQ quantifies for the object (the risk
// scenarios it drives, their annual loss expectancy and, for a CVE, the loss avoided by fixing it).

export const OPENCRQ_PRODUCT = 'opencrq';
// The OpenCRQ connection of a tenant is deployment configuration (openaev.xtm.opencrq.{tenant}.*),
// documented on the same XTM integration page as the OpenCTI one.
export const CONNECT_OPENCRQ_DOC_URL = CONNECT_OPENCTI_DOC_URL;
const DISMISS_KEY = 'xtm-connect-opencrq-dismissed';

const isDismissed = () => {
  try {
    return window.localStorage.getItem(DISMISS_KEY) === 'true';
  } catch {
    return false;
  }
};

export const OpenCrqMark: FunctionComponent<{ size?: number }> = ({ size = 18 }) => (
  <img src={ocrqMark} alt="" aria-hidden width={size} height={size} style={{ borderRadius: 2 }} />
);

// Read by a screen reader, never shown: the hint a disabled call to action carries.
const SR_ONLY = {
  position: 'absolute',
  width: 1,
  height: 1,
  overflow: 'hidden',
  clip: 'rect(0 0 0 0)',
  whiteSpace: 'nowrap',
} as const;

/** Not connected: the preview of what OpenCRQ would show here, and the next step for this user. */
const OpenCrqTeaser: FunctionComponent<{ canConfigure: boolean }> = ({ canConfigure }) => {
  const { t } = useFormatter();
  return (
    <PivotTeaser
      mark={<OpenCrqMark />}
      productName="OpenCRQ"
      labels={FACT_LABELS}
      pitch={t('Connect OpenCRQ to see the financial impact of this object: its annual loss and the loss avoided by fixing it.')}
      next={canConfigure ? t('Open the connection guide to connect OpenCRQ.') : t('Ask an administrator to connect OpenCRQ to see this object there.')}
    />
  );
};

/** The "Connect OpenCRQ" call to action, shown where a pivot would be when not connected. */
export const ConnectOpenCrqCta: FunctionComponent<{ size?: PivotSize }> = ({ size = 'md' }) => {
  const { t } = useFormatter();
  const ability = useAbility();
  const canConfigure = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
  const [dismissed, setDismissed] = useState(isDismissed);
  if (dismissed) {
    return null;
  }
  if (!canConfigure) {
    // A disabled button fires no pointer event: the teaser hangs on a focusable wrapper.
    return (
      <Tooltip>
        <TooltipTrigger asChild>
          <Box component="span" tabIndex={0} sx={{ display: 'inline-flex' }} data-testid="xtm-crq-connect-hint">
            <Button priority="secondary" size={size} disabled>
              <OpenCrqMark />
              {t('Connect OpenCRQ')}
            </Button>
            <Box component="span" sx={SR_ONLY}>{t('Ask an administrator to connect OpenCRQ to see this object there.')}</Box>
          </Box>
        </TooltipTrigger>
        <TooltipContent><OpenCrqTeaser canConfigure={false} /></TooltipContent>
      </Tooltip>
    );
  }
  const dismiss = () => {
    try {
      window.localStorage.setItem(DISMISS_KEY, 'true');
    } catch {
      // Storage refused: dismissed for this view only.
    }
    setDismissed(true);
  };
  return (
    <Box
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 0.5,
      }}
      data-testid="xtm-crq-connect-cta"
    >
      <Tooltip>
        <TooltipTrigger asChild>
          <Button asChild priority="secondary" size={size}>
            <a href={CONNECT_OPENCRQ_DOC_URL} target="_blank" rel="noopener noreferrer">
              <OpenCrqMark />
              {t('Connect OpenCRQ')}
            </a>
          </Button>
        </TooltipTrigger>
        <TooltipContent><OpenCrqTeaser canConfigure /></TooltipContent>
      </Tooltip>
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
    </Box>
  );
};

/** The preview of an OpenCRQ match: the financial impact the click opens. */
export const OpenCrqMatchPreview: FunctionComponent<{ answer: CounterpartAnswer }> = ({ answer }) => {
  const { t } = useFormatter();
  return (
    <PivotPreview
      mark={<OpenCrqMark />}
      productName="OpenCRQ"
      entityType={answer.counterpart?.counterpart_entity_type}
      name={answer.counterpart?.counterpart_name}
      facts={answer.counterpart?.counterpart_facts}
      labels={FACT_LABELS}
      action={t('See the financial impact of this object and the risk scenarios it drives in OpenCRQ.')}
    />
  );
};

/** Renders one answer; kept separate so tests drive every state. */
export const OpenCrqPivotView: FunctionComponent<{
  answer: CounterpartAnswer;
  size?: PivotSize;
}> = ({ answer, size = 'md' }) => {
  const { t, n } = useFormatter();
  switch (answer.state) {
    case 'match': {
      // The annual loss rides next to the label when OpenCRQ answered one; the FDS button never wraps.
      const loss = keyFact(answer.counterpart?.counterpart_facts, 'annual_loss');
      return (
        <Tooltip>
          <TooltipTrigger asChild>
            <Button asChild priority="secondary" size={size}>
              <a href={answer.counterpart?.counterpart_url} target="_blank" rel="noopener noreferrer" data-testid="xtm-crq-pivot-open">
                <OpenCrqMark />
                {t('Open in OpenCRQ')}
                {loss && (
                  <Box component="span" sx={{ opacity: 0.72 }} data-testid="xtm-crq-pivot-figure">
                    {`· ${t('Annual loss')} ${n(Number(loss.fact_value))}`}
                  </Box>
                )}
              </a>
            </Button>
          </TooltipTrigger>
          <TooltipContent><OpenCrqMatchPreview answer={answer} /></TooltipContent>
        </Tooltip>
      );
    }
    case 'not_connected':
      return <ConnectOpenCrqCta size={size} />;
    case 'no_match':
      return (
        <Tooltip>
          <TooltipTrigger asChild>
            <Box component="span" tabIndex={0} sx={{ display: 'inline-flex' }} data-testid="xtm-crq-pivot-none">
              <Button priority="secondary" size={size} disabled>
                <OpenCrqMark />
                {t('Not in OpenCRQ')}
              </Button>
            </Box>
          </TooltipTrigger>
          <TooltipContent>
            <PivotNoMatch
              mark={<OpenCrqMark />}
              productName="OpenCRQ"
              explanation={t('OpenCRQ has not quantified this object yet: add it to a risk scenario there to see its annual loss.')}
            />
          </TooltipContent>
        </Tooltip>
      );
    case 'unavailable':
      return (
        <Tooltip>
          <TooltipTrigger asChild>
            <Box component="span" tabIndex={0} sx={{ display: 'inline-flex' }} data-testid="xtm-crq-pivot-unavailable">
              <Button priority="secondary" size={size} disabled>
                <LinkOffOutlined fontSize="small" />
                {t('OpenCRQ is unavailable')}
              </Button>
            </Box>
          </TooltipTrigger>
          <TooltipContent>{t('OpenCRQ did not answer this time. The pivot comes back on its own.')}</TooltipContent>
        </Tooltip>
      );
    default:
      return null;
  }
};

/** The OpenCRQ facts of a counterpart: risk scenarios driven, annual loss, loss avoided by fixing. */
export const OpenCrqFacts: FunctionComponent<{ facts: FactOutput[] | undefined }> = ({ facts }) => {
  const { t, n } = useFormatter();
  const shown = orderedFacts(facts);
  if (shown.length === 0) {
    return null;
  }
  return (
    <Box
      component="dl"
      sx={{
        display: 'grid',
        gridTemplateColumns: 'max-content 1fr',
        columnGap: 2,
        rowGap: 0.5,
        margin: 0,
        typography: 'body2',
      }}
      data-testid="xtm-crq-facts"
    >
      {shown.map((fact) => {
        const value = Number(fact.fact_value);
        // Amounts are whole units of the OpenCRQ tenant's currency, shown compact (125K).
        const display = MONEY_FACTS.has(fact.fact_key ?? '') && Number.isFinite(value) ? n(value) : fact.fact_value;
        return (
          <Box key={fact.fact_key} sx={{ display: 'contents' }}>
            <Box component="dt" sx={{ color: 'text.secondary' }}>{t(FACT_LABELS[fact.fact_key ?? ''])}</Box>
            <Box component="dd" sx={{ margin: 0 }}>{display}</Box>
          </Box>
        );
      })}
    </Box>
  );
};

interface PivotProps {
  kind: CounterpartKindPath;
  objectKey: string | null | undefined;
}

/** Hero action: "Open in OpenCRQ", "Not in OpenCRQ" or "Connect OpenCRQ". */
const OpenCrqPivotLookup: FunctionComponent<PivotProps & { size?: PivotSize }> = ({ kind, objectKey, size }) => {
  const answer = useCounterpart(kind, objectKey, OPENCRQ_PRODUCT);
  return <OpenCrqPivotView answer={answer} size={size} />;
};

interface RowMarkProps extends PivotProps {
  label?: string;
  reserveSlot?: boolean;
}

/**
 * List-row chip: the OpenCRQ mark linking to the counterpart, nothing otherwise (same contract as
 * OpenCtiRowMark). The tooltip names the action and the counterpart.
 * With reserveSlot, a row of a connected tenant keeps an empty slot of the mark's width.
 */
const OpenCrqRowMarkLookup: FunctionComponent<RowMarkProps> = ({
  kind,
  objectKey,
  label = 'Open in OpenCRQ',
  reserveSlot = false,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const answer = useCounterpart(kind, objectKey, OPENCRQ_PRODUCT);
  const slot = (content: ReactNode) => (
    <Box
      component="span"
      data-testid="xtm-crq-row-mark-slot"
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
          data-testid="xtm-crq-row-mark"
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
          <OpenCrqMark size={16} />
        </Box>
      </TooltipTrigger>
      <TooltipContent><OpenCrqMatchPreview answer={answer} /></TooltipContent>
    </Tooltip>
  );
  return reserveSlot ? slot(mark) : mark;
};

/** Overview card: "In OpenCRQ" with the risk OpenCRQ quantifies for the object, or the CTA. */
export const OpenCrqCardView: FunctionComponent<{ answer: CounterpartAnswer }> = ({ answer }) => {
  const { t } = useFormatter();
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
      data-testid="xtm-crq-card"
    >
      {answer.state === 'match' && (
        <>
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
          }}
          >
            <OpenCrqMark />
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
          <OpenCrqFacts facts={answer.counterpart?.counterpart_facts} />
        </>
      )}
      <OpenCrqPivotView answer={answer} />
      {answer.state === 'no_match' && (
        <Box
          component="span"
          sx={{
            typography: 'caption',
            color: 'text.secondary',
          }}
        >
          {t('This object is not known in the connected OpenCRQ.')}
        </Box>
      )}
    </Box>
  );
};

const OpenCrqCardLookup: FunctionComponent<PivotProps> = ({ kind, objectKey }) => {
  const answer = useCounterpart(kind, objectKey, OPENCRQ_PRODUCT);
  return <OpenCrqCardView answer={answer} />;
};

// The pivots belong to the exploration graph preview feature: off (the default), they draw nothing
// and never ask the backend (the lookup component, and its request, is not mounted).
export const OpenCrqRowMark: FunctionComponent<RowMarkProps> = props => (useExplorationGraph() ? <OpenCrqRowMarkLookup {...props} /> : null);
export const OpenCrqCard: FunctionComponent<PivotProps> = props => (useExplorationGraph() ? <OpenCrqCardLookup {...props} /> : null);
const OpenCrqPivot: FunctionComponent<PivotProps & { size?: PivotSize }> = props => (useExplorationGraph() ? <OpenCrqPivotLookup {...props} /> : null);

export default OpenCrqPivot;
