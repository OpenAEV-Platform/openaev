import { Chip, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import FdsAccordion from '../../../../components/common/accordion/FdsAccordion';
import MarketplaceGrid from '../../../../components/common/marketplace/MarketplaceGrid';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadDraftOutput, type ThreatArsenalAction } from '../../../../utils/api-types';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { tint } from '../../../../utils/tint';
import CreateThreatArsenalAction from '../CreateThreatArsenalAction';
import { newActionDraftName } from './newActionDraftFilters';

interface Props {
  drafts: PayloadDraftOutput[];
  /** A draft was published: the new action joins the list. */
  onCreate: (action: ThreatArsenalAction) => void;
  /** A draft was saved, discarded or published. */
  onDraftsChange: () => void;
}

/**
 * Drafts of new actions (Create, then Save draft), listed in the Active list for everyone who can read
 * the Threat Arsenal: drafts are shared. They have no action and no version until published, so they
 * sit in their own group above the actions. Opening one opens the Create form on it; a user who
 * cannot create actions sees it without opening it, like the drafts of an existing action.
 */
const NewActionDrafts: FunctionComponent<Props> = ({ drafts, onCreate, onDraftsChange }) => {
  const theme = useTheme();
  const { t, nsdt } = useFormatter();
  const canCreate = useAbility().can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);
  if (drafts.length === 0) {
    return null;
  }

  const tileContent = (draft: PayloadDraftOutput) => (
    <>
      <Text variant="content-base-bold">{newActionDraftName(draft) || t('Untitled')}</Text>
      <div style={{
        display: 'flex',
        gap: 4,
        flexWrap: 'wrap',
      }}
      >
        <Chip label={t('Draft')} />
        <Chip label={t('New action')} />
      </div>
      <Text variant="content-compact" className="text-default-secondary">
        {draft.draft_updated_by
          ? t('Saved by {name}, {date}', {
              name: draft.draft_updated_by,
              date: nsdt(draft.draft_updated_at),
            })
          : t('Saved {date}', { date: nsdt(draft.draft_updated_at) })}
      </Text>
    </>
  );
  const tileStyle = {
    boxSizing: 'border-box' as const,
    minHeight: 160,
    height: '100%',
    display: 'flex',
    flexDirection: 'column' as const,
    alignItems: 'flex-start',
    gap: theme.spacing(1),
    padding: theme.spacing(2),
    borderRadius: theme.shape.borderRadius,
    border: `1px solid ${tint(theme.palette.text.primary, 16)}`,
  };

  return (
    <FdsAccordion
      defaultExpanded
      title={t('Drafts of new actions')}
      meta={<Text variant="content-compact" className="text-default-secondary">{String(drafts.length)}</Text>}
    >
      <MarketplaceGrid loading={false} isEmpty={false} emptyState={null}>
        {drafts.map(draft => (canCreate
          ? (
              <CreateThreatArsenalAction
                key={draft.draft_id}
                draft={draft}
                onCreate={onCreate}
                onDraftsChange={onDraftsChange}
                renderTrigger={open => (
                  <button
                    type="button"
                    onClick={open}
                    style={{
                      all: 'unset',
                      cursor: 'pointer',
                      ...tileStyle,
                    }}
                  >
                    {tileContent(draft)}
                  </button>
                )}
              />
            )
          : <div key={draft.draft_id} style={tileStyle}>{tileContent(draft)}</div>))}
      </MarketplaceGrid>
    </FdsAccordion>
  );
};

export default NewActionDrafts;
