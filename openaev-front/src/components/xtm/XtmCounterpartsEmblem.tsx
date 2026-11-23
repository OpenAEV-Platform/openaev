import { Button, Chip, IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Box, Divider, Popover } from '@mui/material';
import { type FunctionComponent, type MouseEvent, useState } from 'react';

import { type CounterpartKindPath } from '../../actions/xtm/counterpart-actions';
import ocrqMask from '../../static/images/xtm/xtm-mark-opencrq.svg';
import octiMask from '../../static/images/xtm/xtm-mark-opencti.svg';
import xtmOneMask from '../../static/images/xtm/xtm-mark-xtmone.svg';
import { useAbility } from '../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
import { useFormatter } from '../i18n';
import { FACT_LABELS, OPENCTI_FACT_LABELS, XTM_ONE_FACT_LABELS } from './counterpartFacts';
import { type CounterpartAnswer } from './counterpartStore';
import { withSourceLink } from './sourcedCounterpart';
import useCounterpart, { OPENCTI_PRODUCT } from './useCounterpart';
import { EMBLEM_FOUND, EMBLEM_FOUND_XTM_ONE, EMBLEM_MUTED, MaskedEmblem } from './XtmEmblem';
import { CONNECT_XTM_ONE_DOC_URL, XTM_ONE_PRODUCT } from './XtmOnePivot';
import { CONNECT_OPENCRQ_DOC_URL, OPENCRQ_PRODUCT } from './XtmOpenCrq';
import { CONNECT_OPENCTI_DOC_URL } from './XtmPivot';
import { PivotFactList } from './XtmPivotPreview';

// One emblem per object, identical in OpenCTI, OpenAEV and OpenCRQ (Sam, 23 Nov 2026; parity with
// OpenCTI-Platform/opencti#20153): the XTM One mark in one header slot, purple when at least one
// other product holds the object, grey otherwise. A click opens a small anchored surface (a
// popover, never a dialog) with one section per product: its status, the facts it answered and a
// button to open the object there.
// - not connected: a "Connect X" entry for a user who can configure the platform, nothing else;
// - not supported (the product has no lookup for this object): the product is left out;
// - nothing at all to show (no product connected and the user cannot connect one): no emblem.
// Everything sits behind the dedicated preview feature XTM_COUNTERPARTS (off by default): while it
// is off useCounterpart asks nothing and answers not connected, and the backend answers 404.

const SECONDARY_INK = 'var(--text-default-secondary)';

interface ProductDef {
  product: string;
  name: string;
  mask: string;
  foundColor: string;
  facts: Record<string, string>;
  // Literal i18n keys, translated where they are drawn.
  open: string;
  connect: string;
  notFoundHint: string;
  unavailableHint: string;
  connectUrl: string;
  kinds?: ReadonlyArray<CounterpartKindPath>;
}

// The fixed order of every surface: XTM One, OpenCTI, then OpenCRQ (OpenAEV itself skipped).
export const XTM_PRODUCTS: ReadonlyArray<ProductDef> = [
  {
    product: XTM_ONE_PRODUCT,
    name: 'XTM One',
    mask: xtmOneMask,
    foundColor: EMBLEM_FOUND_XTM_ONE,
    facts: XTM_ONE_FACT_LABELS,
    open: 'Open in XTM One',
    connect: 'Connect XTM One',
    notFoundHint: 'XTM One holds it once a source of its inventory reports this asset.',
    unavailableHint: 'XTM One did not answer this time. The pivot comes back on its own.',
    connectUrl: CONNECT_XTM_ONE_DOC_URL,
    // XTM One's inventory holds assets only.
    kinds: ['assets'],
  },
  {
    product: OPENCTI_PRODUCT,
    name: 'OpenCTI',
    mask: octiMask,
    foundColor: EMBLEM_FOUND,
    facts: OPENCTI_FACT_LABELS,
    open: 'Open in OpenCTI',
    connect: 'Connect OpenCTI',
    notFoundHint: 'OpenCTI holds it once a connector or an analyst creates it there.',
    unavailableHint: 'OpenCTI did not answer this time. The pivot comes back on its own.',
    connectUrl: CONNECT_OPENCTI_DOC_URL,
  },
  {
    product: OPENCRQ_PRODUCT,
    name: 'OpenCRQ',
    mask: ocrqMask,
    foundColor: EMBLEM_FOUND,
    facts: FACT_LABELS,
    open: 'Open in OpenCRQ',
    connect: 'Connect OpenCRQ',
    notFoundHint: 'OpenCRQ holds it once a risk scenario or a supporting asset names it.',
    unavailableHint: 'OpenCRQ did not answer this time. The pivot comes back on its own.',
    connectUrl: CONNECT_OPENCRQ_DOC_URL,
  },
];

export type SectionKind = 'found' | 'not_found' | 'unavailable' | 'not_connected';

export const sectionKindOf = (answer: CounterpartAnswer): SectionKind | null => {
  switch (answer.state) {
    case 'match':
      return 'found';
    case 'no_match':
      return 'not_found';
    case 'unavailable':
      return 'unavailable';
    case 'not_connected':
      return 'not_connected';
    default:
      return null;
  }
};

/** A counterpart link is followed only when it is an http(s) address. */
export const safeCounterpartUrl = (url: string | null | undefined): string | undefined => {
  if (!url) {
    return undefined;
  }
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'https:' || parsed.protocol === 'http:' ? parsed.toString() : undefined;
  } catch {
    return undefined;
  }
};

export interface EmblemSection {
  def: ProductDef;
  answer: CounterpartAnswer;
  kind: SectionKind;
}

/** The sections a surface shows: not connected for a configurer only, loading and unsupported never. */
export const visibleSections = (
  entries: ReadonlyArray<{
    def: ProductDef;
    answer: CounterpartAnswer;
  }>,
  canConfigure: boolean,
): EmblemSection[] => entries
  .map(({ def, answer }) => ({
    def,
    answer,
    kind: sectionKindOf(answer),
  }))
  .filter((s): s is EmblemSection => s.kind !== null && (s.kind !== 'not_connected' || canConfigure));

const ProductSection: FunctionComponent<EmblemSection> = ({ def, answer, kind }) => {
  const { t } = useFormatter();
  const status: Record<SectionKind, {
    label: string;
    severity: 'info' | 'neutral' | 'medium';
  }> = {
    found: {
      label: t('Found'),
      severity: 'info',
    },
    not_found: {
      label: t('Not found'),
      severity: 'neutral',
    },
    unavailable: {
      label: t('Unavailable'),
      severity: 'medium',
    },
    not_connected: {
      label: t('Not connected'),
      severity: 'neutral',
    },
  };
  const counterpart = kind === 'found' ? answer.counterpart : undefined;
  const href = safeCounterpartUrl(counterpart?.counterpart_url);
  return (
    <Box
      component="section"
      aria-label={def.name}
      data-testid={`${def.product}-section`}
      data-status={kind}
      sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 1,
      }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
      }}
      >
        <MaskedEmblem maskUrl={def.mask} size={16} color={kind === 'found' ? def.foundColor : EMBLEM_MUTED} />
        <Box
          component="span"
          sx={{
            fontWeight: 600,
            flex: 1,
          }}
        >
          {def.name}
        </Box>
        <Chip label={status[kind].label} severity={status[kind].severity} />
      </Box>
      {counterpart && (
        <>
          {(counterpart.counterpart_entity_type || counterpart.counterpart_name) && (
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
              minWidth: 0,
            }}
            >
              {counterpart.counterpart_entity_type && <Chip label={counterpart.counterpart_entity_type} />}
              {counterpart.counterpart_name && (
                <Box
                  component="span"
                  sx={{
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {counterpart.counterpart_name}
                </Box>
              )}
            </Box>
          )}
          <PivotFactList facts={counterpart.counterpart_facts} labels={def.facts} />
        </>
      )}
      {kind === 'not_found' && <Box component="span" sx={{ color: SECONDARY_INK }}>{t(def.notFoundHint)}</Box>}
      {kind === 'unavailable' && <Box component="span" sx={{ color: SECONDARY_INK }}>{t(def.unavailableHint)}</Box>}
      {href && (
        <Box>
          <Button asChild priority="secondary" size="sm">
            <a href={href} target="_blank" rel="noopener noreferrer" data-testid={`${def.product}-open`}>{t(def.open)}</a>
          </Button>
        </Box>
      )}
      {kind === 'not_connected' && (
        <Box>
          <Button asChild priority="secondary" size="sm">
            <a href={def.connectUrl} target="_blank" rel="noopener noreferrer" data-testid={`${def.product}-connect`}>{t(def.connect)}</a>
          </Button>
        </Box>
      )}
    </Box>
  );
};

/** Renders the emblem and its surface from the sections; tests drive every state through it. */
export const XtmCounterpartsEmblemView: FunctionComponent<{ sections: EmblemSection[] }> = ({ sections }) => {
  const { t } = useFormatter();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  if (sections.length === 0) {
    return null;
  }
  const open = Boolean(anchor);
  const found = sections.filter(s => s.kind === 'found').length;
  const ink = found > 0 ? EMBLEM_FOUND_XTM_ONE : EMBLEM_MUTED;
  const label = found > 0 ? t('Across the XTM Platform') : t('Not found across the XTM Platform');
  return (
    <>
      <Tooltip>
        <TooltipTrigger asChild>
          <IconButton
            icon={<MaskedEmblem maskUrl={xtmOneMask} color={ink} />}
            aria-label={label}
            aria-haspopup="dialog"
            aria-expanded={open}
            priority="secondary"
            size="md"
            data-testid="xtm-emblem"
            data-found={found}
            onClick={(event: MouseEvent<HTMLElement>) => setAnchor(event.currentTarget)}
          />
        </TooltipTrigger>
        <TooltipContent>{label}</TooltipContent>
      </Tooltip>
      <Popover
        open={open}
        anchorEl={anchor}
        onClose={() => setAnchor(null)}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'right',
        }}
        transformOrigin={{
          vertical: 'top',
          horizontal: 'right',
        }}
        slotProps={{
          paper: {
            'role': 'dialog',
            'aria-label': t('Across the XTM Platform'),
            'data-testid': 'xtm-surface',
            'sx': { mt: 1 },
          } as object,
        }}
      >
        <Box sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
          width: 320,
          padding: 2,
        }}
        >
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
          }}
          >
            <MaskedEmblem maskUrl={xtmOneMask} size={16} color={ink} />
            <Box component="span" sx={{ fontWeight: 600 }}>{t('Across the XTM Platform')}</Box>
          </Box>
          {sections.map((section, index) => (
            <Box
              key={section.def.product}
              sx={{
                display: 'flex',
                flexDirection: 'column',
                gap: 2,
              }}
            >
              {index > 0 && <Divider data-testid="xtm-surface-separator" />}
              <ProductSection {...section} />
            </Box>
          ))}
        </Box>
      </Popover>
    </>
  );
};

interface EmblemProps {
  kind: CounterpartKindPath;
  objectKey: string | null | undefined;
  /** The object's source link (an asset's external reference): found from it first (rule 5). */
  sourceUrl?: string | null;
  /** OpenCRQ keys some objects otherwise (supporting assets by hostname). */
  openCrqKey?: string | null;
}

/** The single XTM emblem of an object header, and its cross-product surface. */
const XtmCounterpartsEmblem: FunctionComponent<EmblemProps> = ({ kind, objectKey, sourceUrl, openCrqKey }) => {
  const ability = useAbility();
  const canConfigure = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
  const supportsXtmOne = kind === 'assets';
  // Hooks run for every product, the order fixed; an unsupported product is dropped afterwards.
  const xtmOne = useCounterpart(kind, supportsXtmOne ? objectKey : null, XTM_ONE_PRODUCT);
  const openCti = useCounterpart(kind, objectKey, OPENCTI_PRODUCT);
  const openCrq = useCounterpart(kind, openCrqKey !== undefined ? openCrqKey : objectKey, OPENCRQ_PRODUCT);
  const answers: Record<string, CounterpartAnswer> = {
    [XTM_ONE_PRODUCT]: xtmOne,
    [OPENCTI_PRODUCT]: openCti,
    [OPENCRQ_PRODUCT]: openCrq,
  };
  const entries = XTM_PRODUCTS
    .filter(def => !def.kinds || def.kinds.includes(kind))
    .map(def => ({
      def,
      answer: withSourceLink(answers[def.product] as CounterpartAnswer & { connectionUrl?: string }, sourceUrl),
    }));
  return <XtmCounterpartsEmblemView sections={visibleSections(entries, canConfigure)} />;
};

export default XtmCounterpartsEmblem;
