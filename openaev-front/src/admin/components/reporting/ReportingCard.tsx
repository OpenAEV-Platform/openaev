import { IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { FileDownloadOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import { downloadReportingGenerationUrl } from '../../../actions/reporting/reporting-actions';
import EntityCard from '../../../components/common/card/EntityCard';
import { useFormatter } from '../../../components/i18n';
import { type Reporting } from '../../../utils/api-types';
import {
  latestGeneration,
  REPORTING_CONTEXT_ICONS,
  REPORTING_CONTEXT_LABELS,
} from './ReportingContexts';
import { ReportingFormatFragment, ReportingStatusChip } from './ReportingFragments';
import ReportingPopover from './ReportingPopover';

interface Props {
  reporting: Reporting;
  onUpdate?: (result: Reporting) => void;
  onDelete?: (reportingId: string) => void;
}

const ReportingCard: FunctionComponent<Props> = ({ reporting, onUpdate, onDelete }) => {
  const { t, fldt } = useFormatter();
  const ContextIcon = REPORTING_CONTEXT_ICONS[reporting.reporting_context_type];
  const generation = latestGeneration(reporting);
  const downloadable = generation?.reporting_generation_status === 'SUCCESS' && generation.reporting_generation_document;

  return (
    <EntityCard
      testId="reporting-card"
      to={`/admin/reporting/${reporting.reporting_id}`}
      icon={<ContextIcon sx={{ fontSize: 32 }} />}
      overline={t(REPORTING_CONTEXT_LABELS[reporting.reporting_context_type])}
      title={reporting.reporting_name}
      description={reporting.reporting_description}
      caption={`${t('Updated at')} ${fldt(reporting.reporting_updated_at)}`}
      menu={<ReportingPopover reporting={reporting} onUpdate={onUpdate} onDelete={onDelete} inList />}
      footerStart={(
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
        >
          <ReportingFormatFragment format={reporting.reporting_default_format} />
          {generation && (
            <ReportingStatusChip status={generation.reporting_generation_status} />
          )}
        </div>
      )}
      footerEnd={downloadable ? (
        <Tooltip>
          <TooltipTrigger asChild>
            <IconButton
              icon={<FileDownloadOutlined fontSize="small" />}
              aria-label={t('Download latest generation')}
              onClick={(event) => {
                // Inside the card link: keep the click from opening the report.
                event.preventDefault();
                event.stopPropagation();
                window.location.assign(downloadReportingGenerationUrl(generation.reporting_generation_id));
              }}
              priority="tertiary"
              size="sm"
            />
          </TooltipTrigger>
          <TooltipContent>{t('Download latest generation')}</TooltipContent>
        </Tooltip>
      ) : undefined}
    />
  );
};

export default ReportingCard;
