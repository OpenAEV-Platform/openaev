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
import { type CounterpartAnswer } from './counterpartStore';
import useCounterpart, { OPENCTI_PRODUCT } from './useCounterpart';

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

const useCanConfigure = () => {
  const ability = useAbility();
  return ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
};

/** The "Connect OpenCTI" call to action, shown where a pivot would be when not connected. */
export const ConnectOpenCtiCta: FunctionComponent<{ variant?: 'button' | 'inline' }> = ({ variant = 'button' }) => {
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
    return (
      <Box
        component="span"
        sx={{
          typography: 'caption',
          color: 'text.secondary',
        }}
        data-testid="xtm-connect-hint"
      >
        {t('Ask an administrator to connect OpenCTI to see this object there.')}
      </Box>
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
      <Button asChild priority="tertiary" size="sm">
        <a href={CONNECT_OPENCTI_DOC_URL} target="_blank" rel="noopener noreferrer">
          <ProductMark />
          {t('Connect OpenCTI')}
        </a>
      </Button>
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

// "sm" in a hero bar beside its icon buttons; "md" in a panel of full-size actions (graph drawer).
type PivotSize = 'sm' | 'md';

/** Renders the answer once loaded; kept separate so tests drive every state. */
export const OpenCtiPivotView: FunctionComponent<{
  answer: CounterpartAnswer;
  size?: PivotSize;
}> = ({ answer, size = 'sm' }) => {
  const { t } = useFormatter();
  switch (answer.state) {
    case 'match':
      return (
        <Tooltip>
          <TooltipTrigger asChild>
            <Button asChild priority="secondary" size={size}>
              <a href={answer.counterpart?.counterpart_url} target="_blank" rel="noopener noreferrer" data-testid="xtm-pivot-open">
                <ProductMark />
                {t('Open in OpenCTI')}
              </a>
            </Button>
          </TooltipTrigger>
          <TooltipContent>{answer.counterpart?.counterpart_name}</TooltipContent>
        </Tooltip>
      );
    case 'not_connected':
      return <ConnectOpenCtiCta />;
    case 'no_match':
      return (
        <span data-testid="xtm-pivot-none">
          <Chip label={t('Not in OpenCTI')} />
        </span>
      );
    case 'unavailable':
      return (
        <Tooltip>
          <TooltipTrigger asChild>
            <Box component="span" sx={{ display: 'inline-flex' }} data-testid="xtm-pivot-unavailable">
              <LinkOffOutlined fontSize="small" color="disabled" />
            </Box>
          </TooltipTrigger>
          <TooltipContent>{t('OpenCTI is unavailable')}</TooltipContent>
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
  const text = answer.counterpart?.counterpart_name ? `${t(label)}: ${answer.counterpart.counterpart_name}` : t(label);
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
      <TooltipContent>{text}</TooltipContent>
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
