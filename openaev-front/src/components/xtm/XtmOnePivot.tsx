import { type FunctionComponent } from 'react';

import { type CounterpartKindPath } from '../../actions/xtm/counterpart-actions';
// The XTM One mark alone (the highlight group of the design system's product-switcher XtmoneLogo,
// viewBox 0 0 35 34), painted through a CSS mask in every state.
import xtmOneMask from '../../static/images/xtm/xtm-mark-xtmone.svg';
import { useAbility } from '../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
import { useFormatter } from '../i18n';
import { XTM_ONE_FACT_LABELS } from './counterpartFacts';
import { type CounterpartAnswer } from './counterpartStore';
import { withSourceLink } from './sourcedCounterpart';
import useCounterpart from './useCounterpart';
import CounterpartEmblem, { EMBLEM_FOUND_XTM_ONE, EMBLEM_GLYPH_SIZE, EMBLEM_MUTED, MaskedEmblem } from './XtmEmblem';
import { PivotPreview, PivotTeaser } from './XtmPivotPreview';

// The XTM One side of the cross-platform pivots (contract v3, Sam, 23 Nov 2026): the first emblem
// of every group, found when XTM One's CTEM inventory holds the asset (read with the user's own
// XTM One account), in the canonical XTM One purple. XTM One's inventory holds assets only.

export const XTM_ONE_PRODUCT = 'xtmone';
// The XTM One connection is deployment configuration (openaev.xtm.one.*), documented on its page.
export const CONNECT_XTM_ONE_DOC_URL = 'https://docs.openaev.io/latest/integrations/xtm-suite/xtm-one/';

/** The XTM One glyph: the canonical XTM One purple when found, the disabled ink otherwise. */
export const XtmOneMark: FunctionComponent<{
  size?: number;
  muted?: boolean;
}> = ({ size = EMBLEM_GLYPH_SIZE, muted = false }) => (
  <MaskedEmblem maskUrl={xtmOneMask} size={size} color={muted ? EMBLEM_MUTED : EMBLEM_FOUND_XTM_ONE} />
);

/** Not connected: the preview of what XTM One would show here, and the next step for this user. */
const XtmOneTeaser: FunctionComponent<{ canConfigure: boolean }> = ({ canConfigure }) => {
  const { t } = useFormatter();
  return (
    <PivotTeaser
      mark={<XtmOneMark size={16} muted />}
      productName="XTM One"
      labels={XTM_ONE_FACT_LABELS}
      pitch={t('Connect XTM One to see this asset in the CTEM inventory: its criticality, category and every source that reports it.')}
      next={canConfigure ? t('Open the connection guide to connect XTM One.') : t('Ask an administrator to connect XTM One to see this asset there.')}
    />
  );
};

/** The preview of an XTM One match: the inventory asset the click opens. */
export const XtmOneMatchPreview: FunctionComponent<{ answer: CounterpartAnswer }> = ({ answer }) => {
  const { t } = useFormatter();
  return (
    <PivotPreview
      mark={<XtmOneMark size={16} />}
      productName="XTM One"
      entityType={answer.counterpart?.counterpart_entity_type}
      name={answer.counterpart?.counterpart_name}
      facts={answer.counterpart?.counterpart_facts}
      labels={XTM_ONE_FACT_LABELS}
      action={t('See this asset in the XTM One CTEM inventory, with every source that reports it.')}
    />
  );
};

/** Renders one answer as the icon-only emblem of contract v3; tests drive every state. */
export const XtmOnePivotView: FunctionComponent<{ answer: CounterpartAnswer }> = ({ answer }) => {
  const { t } = useFormatter();
  const ability = useAbility();
  const canConfigure = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
  return (
    <CounterpartEmblem
      answer={answer}
      maskUrl={xtmOneMask}
      foundColor={EMBLEM_FOUND_XTM_ONE}
      labels={{
        open: t('Open in XTM One'),
        connect: t('Connect XTM One'),
        notConnected: t('XTM One is not connected'),
        notFound: t('Not found in XTM One'),
        notFoundHint: t('XTM One holds it once a source of its inventory reports this asset.'),
        unavailable: t('XTM One is unavailable'),
        unavailableHint: t('XTM One did not answer this time. The pivot comes back on its own.'),
      }}
      preview={<XtmOneMatchPreview answer={answer} />}
      teaser={<XtmOneTeaser canConfigure={canConfigure} />}
      connectUrl={canConfigure ? CONNECT_XTM_ONE_DOC_URL : undefined}
      testId="xtm-one-pivot"
    />
  );
};

interface PivotProps {
  kind: CounterpartKindPath;
  objectKey: string | null | undefined;
  /** The object's source link (an asset's external reference): found from it first. */
  sourceUrl?: string | null;
}

/** Hero action: the XTM One emblem, in the state of the counterpart. */
const XtmOnePivot: FunctionComponent<PivotProps> = ({ kind, objectKey, sourceUrl }) => {
  const answer = useCounterpart(kind, objectKey, XTM_ONE_PRODUCT);
  return <XtmOnePivotView answer={withSourceLink(answer, sourceUrl)} />;
};

export default XtmOnePivot;
