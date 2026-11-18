import {
  Card,
  CardDescription,
  CardFooter,
  CardHeader,
  CardInfo,
  CardInfoGroup,
  CardOverview,
  CardTitle,
  IconButton,
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@filigran/design-system';
import { FileDownloadOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import { downloadReportingGenerationUrl } from '../../../actions/reporting/reporting-actions';
import CardThumbnail from '../../../components/common/card/CardThumbnail';
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
    // Library Card (Figma Design_System_2026, node 8461:13185): framed icon,
    // context and title, description, last update line, then a footer with
    // the format and the generation status, download and ⋮ on the right.
    <Card data-testid="reporting-card" to={`/admin/reporting/${reporting.reporting_id}`} linkComponent={Link}>
      <CardHeader icon={<CardThumbnail icon={<ContextIcon sx={{ fontSize: 24 }} />} />}>
        <CardOverview>{t(REPORTING_CONTEXT_LABELS[reporting.reporting_context_type])}</CardOverview>
        <CardTitle>{reporting.reporting_name}</CardTitle>
      </CardHeader>
      {reporting.reporting_description && <CardDescription>{reporting.reporting_description}</CardDescription>}
      <CardInfoGroup>
        <CardInfo label={`${t('Updated')}:`} value={fldt(reporting.reporting_updated_at)} />
      </CardInfoGroup>
      <CardFooter
        start={(
          <>
            <ReportingFormatFragment format={reporting.reporting_default_format} />
            {generation && <ReportingStatusChip status={generation.reporting_generation_status} />}
          </>
        )}
        end={(
          <>
            {downloadable && (
              <Tooltip>
                <TooltipTrigger asChild>
                  <IconButton
                    icon={<FileDownloadOutlined fontSize="small" />}
                    aria-label={t('Download latest generation')}
                    onClick={(event) => {
                      // Above the card link: keep the click from opening the report.
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
            )}
            <ReportingPopover reporting={reporting} onUpdate={onUpdate} onDelete={onDelete} inList />
          </>
        )}
      />
    </Card>
  );
};

export default ReportingCard;
