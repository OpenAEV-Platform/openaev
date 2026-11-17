import { Button, Text } from '@filigran/design-system';
import { NorthEastOutlined, ScheduleOutlined, ShowChartOutlined, StorageOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import type React from 'react';
import type { ReactNode } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { tint } from '../../../../../utils/tint';

interface MetricHighlightProps {
  icon: ReactNode;
  title: string;
  description: string;
  accent: string;
}

const MetricHighlight: React.FC<MetricHighlightProps> = ({ icon, title, description, accent }) => {
  const theme = useTheme();

  return (
    <div style={{
      display: 'flex',
      gap: theme.spacing(1),
      alignItems: 'flex-start',
    }}
    >
      <span style={{
        display: 'flex',
        color: accent,
      }}
      >
        {icon}
      </span>
      <div style={{ minWidth: 0 }}>
        <Text variant="content-compact-bold">{title}</Text>
        <Text variant="content-caption" className="text-default-secondary">{description}</Text>
      </div>
    </div>
  );
};

interface Props { metricsUrl: string }

/**
 * Pitch shown to a tenant whose XTM Hub registration runs on the SaaS commercial model:
 * its subscription, contract and usage metrics live on the Hub.
 */
const XtmHubSaasMetrics: React.FC<Props> = ({ metricsUrl }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const accent = theme.palette.primary.main;
  const hairline = `1px solid ${tint(theme.palette.text.primary, 8)}`;

  return (
    <section
      data-testid="xtm-hub-saas-metrics"
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(1.5),
        padding: theme.spacing(2),
        borderRadius: theme.shape.borderRadius,
        border: `1px solid ${tint(accent, 30)}`,
        background: tint(accent, 6),
      }}
    >
      <div>
        <Text
          variant="content-caption"
          className="uppercase"
          // Same overline as the ExperienceCard header: the library ships no letter-spacing scale.
          style={{
            color: accent,
            letterSpacing: '0.12em',
          }}
        >
          {t('New · SaaS metrics')}
        </Text>
        <Text variant="content-base-bold" as="h3" style={{ margin: 0 }}>
          {t('Your OpenAEV, by the numbers')}
        </Text>
      </div>
      <Text variant="content-base" className="text-default-secondary">
        {t('Your platform runs in the Filigran cloud. XTM Hub shows you the whole picture in one place: what you subscribed to, how much storage your data takes, the uptime you are guaranteed and how much time is left on your contract.')}
      </Text>
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
        gap: theme.spacing(2),
      }}
      >
        <MetricHighlight
          accent={accent}
          icon={<ShowChartOutlined fontSize="small" />}
          title={t('Your plan')}
          description={t('Know exactly what you are running on')}
        />
        <MetricHighlight
          accent={accent}
          icon={<ScheduleOutlined fontSize="small" />}
          title={t('Contract countdown')}
          description={t('Days left before renewal, no surprises')}
        />
        <MetricHighlight
          accent={accent}
          icon={<StorageOutlined fontSize="small" />}
          title={t('Storage & uptime')}
          description={t('Database space used and the SLA you can count on')}
        />
      </div>
      <div style={{
        display: 'flex',
        justifyContent: 'flex-end',
        paddingTop: theme.spacing(1.5),
        borderTop: hairline,
      }}
      >
        <Button asChild>
          <a href={metricsUrl} target="_blank" rel="noreferrer">
            {t('See my OpenAEV metrics')}
            <NorthEastOutlined fontSize="small" />
          </a>
        </Button>
      </div>
    </section>
  );
};

export default XtmHubSaasMetrics;
