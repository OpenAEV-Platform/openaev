import { ButtonGroup, ButtonGroupItem } from '@filigran/design-system';
import { TimelineOutlined, ViewListOutlined } from '@mui/icons-material';
import {
  Box,
  Tooltip,
} from '@mui/material';
import { useState } from 'react';

import { SectionLabel } from '../../../components/common/detail/EntityDetailCommon';
import type { Page } from '../../../components/common/queryable/Page';
import { useFormatter } from '../../../components/i18n';
import type { Finding, RelatedFindingOutput, SearchPaginationInput } from '../../../utils/api-types';
import FindingOccurrencesList from './FindingOccurrencesList';
import FindingOccurrencesTimeline from './FindingOccurrencesTimeline';

type ViewMode = 'list' | 'timeline';

const VIEW_MODE_STORAGE_KEY = 'finding-occurrences-view-mode';

const initialViewMode = (): ViewMode => (
  localStorage.getItem(VIEW_MODE_STORAGE_KEY) === 'timeline' ? 'timeline' : 'list'
);

interface Props {
  searchFindings: (input: SearchPaginationInput) => Promise<{ data: Page<RelatedFindingOutput> }>;
  finding: Pick<Finding, 'finding_type' | 'finding_value'>;
  contextId?: string;
}

// "Finding timeline" section: every occurrence of the finding (one per inject), either as a
// filterable table or as a horizontal time strip - the strip is what makes a recurring detection
// (e.g. a scheduled atomic testing re-finding the same credential) readable at a glance.
const FindingOccurrences = ({ searchFindings, finding, contextId }: Props) => {
  const { t } = useFormatter();
  const [viewMode, setViewMode] = useState<ViewMode>(initialViewMode);

  const handleViewMode = (mode: ViewMode | null) => {
    if (!mode) return;
    setViewMode(mode);
    localStorage.setItem(VIEW_MODE_STORAGE_KEY, mode);
  };

  return (
    <div>
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        minHeight: 32,
        marginBottom: 1,
      }}
      >
        <Box sx={{ '& > *': { marginBottom: '0 !important' } }}>
          <SectionLabel>{t('Finding timeline')}</SectionLabel>
        </Box>
        <div style={{ flex: 1 }} />
        <ButtonGroup
          size="sm"
          value={viewMode}
          onValueChange={mode => handleViewMode(mode as ViewMode)}
          aria-label={t('Occurrences view mode')}
        >
          <Tooltip title={t('List view')}>
            <ButtonGroupItem value="list" aria-label={t('List view')} icon={<ViewListOutlined fontSize="small" />} />
          </Tooltip>
          <Tooltip title={t('Timeline view')}>
            <ButtonGroupItem value="timeline" aria-label={t('Timeline view')} icon={<TimelineOutlined fontSize="small" />} />
          </Tooltip>
        </ButtonGroup>
      </Box>
      {viewMode === 'list'
        ? (
            <FindingOccurrencesList
              searchFindings={searchFindings}
              finding={finding}
              contextId={contextId}
            />
          )
        : (
            <FindingOccurrencesTimeline
              searchFindings={searchFindings}
              finding={finding}
            />
          )}
    </div>
  );
};

export default FindingOccurrences;
