import { type CounterpartKindPath } from '../../actions/xtm/counterpart-actions';
import ocrqMask from '../../static/images/xtm/xtm-mark-opencrq.svg';
import octiMask from '../../static/images/xtm/xtm-mark-opencti.svg';
import xtmOneMask from '../../static/images/xtm/xtm-mark-xtmone.svg';
import { FACT_LABELS, OPENCTI_FACT_LABELS, XTM_ONE_FACT_LABELS } from './counterpartFacts';
import { type CounterpartAnswer } from './counterpartStore';
import { OPENCTI_PRODUCT } from './useCounterpart';
import { EMBLEM_FOUND, EMBLEM_FOUND_XTM_ONE } from './XtmEmblem';
import { CONNECT_XTM_ONE_DOC_URL, XTM_ONE_PRODUCT } from './XtmOnePivot';
import { CONNECT_OPENCRQ_DOC_URL, OPENCRQ_PRODUCT } from './XtmOpenCrq';
import { CONNECT_OPENCTI_DOC_URL } from './XtmPivot';

// The model of the single XTM One emblem (XtmCounterpartsEmblem): the products of its surface,
// their order, and which sections a user sees. Kept apart from the component so it stays a
// components-only module.

export interface ProductDef {
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
