import { Paper } from '@filigran/design-system';
import { AutoAwesomeOutlined, MenuBookOutlined, OpenInNew } from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { Github, Slack } from 'mdi-material-ui';
import { type ComponentType, type CSSProperties } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../components/i18n';
import { XTM_HUB_DEFAULT_URL } from '../../../utils/Environment';
import GettingStartedSectionHeader from './GettingStartedSectionHeader';

interface Resource {
  icon: ComponentType<{ sx?: object }>;
  color: string;
  title: string;
  description: string;
  href: string;
}

// The lift and the tinted border on hover. The tint is per-resource, so it
// travels as a custom property rather than as a generated class per colour.
const useStyles = makeStyles()({
  card: {
    'display': 'flex',
    'alignItems': 'flex-start',
    'gap': 12,
    'textDecoration': 'none',
    'transition': 'transform 150ms ease, border-color 150ms ease',
    '&:hover': {
      transform: 'translateY(-2px)',
      borderColor: 'var(--resource-hover-border)',
    },
  },
});

// A single external resource rendered as a clickable card: framed icon,
// title, one-line description, and an unobtrusive external-link affordance.
const ResourceCard = ({ resource }: { resource: Resource }) => {
  const Icon = resource.icon;
  const { classes } = useStyles();

  return (
    // `as="a"` keeps the surface itself the link — href, target and rel land on
    // a real anchor, so cmd-click and the new tab are unchanged (measured).
    // Iso with the MUI card it replaces: 4px radius, 16px padding, 12px gap and
    // the same border, which the sx was already borrowing from the library.
    <Paper
      as="a"
      href={resource.href}
      target="_blank"
      rel="noopener noreferrer"
      padding={16}
      className={classes.card}
      style={{ '--resource-hover-border': alpha(resource.color, 0.45) } as CSSProperties}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: 36,
        height: 36,
        borderRadius: 1,
        flexShrink: 0,
        color: resource.color,
        backgroundColor: alpha(resource.color, 0.12),
        boxShadow: `inset 0 0 12px ${alpha(resource.color, 0.13)}`,
      }}
      >
        <Icon sx={{ fontSize: 18 }} />
      </Box>
      <Box sx={{ minWidth: 0 }}>
        <Typography sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 0.75,
          fontSize: 13.5,
          fontWeight: 600,
          lineHeight: 1.3,
        }}
        >
          {resource.title}
          <OpenInNew sx={{
            fontSize: 13,
            color: 'text.secondary',
          }}
          />
        </Typography>
        <Typography sx={{
          fontSize: 12,
          lineHeight: 1.5,
          color: 'text.secondary',
          marginTop: 0.5,
        }}
        >
          {resource.description}
        </Typography>
      </Box>
    </Paper>
  );
};

const GettingStartedResources = () => {
  const { t } = useFormatter();
  const theme = useTheme();

  const resources: Resource[] = [
    {
      icon: MenuBookOutlined,
      color: theme.palette.primary.main,
      title: t('getting_started_resource_docs'),
      description: t('getting_started_resource_docs_text'),
      href: 'https://docs.openaev.io',
    },
    {
      icon: AutoAwesomeOutlined,
      color: theme.palette.xtmhub.main,
      title: t('XTM Hub Library'),
      description: t('getting_started_resource_hub_text'),
      href: `${XTM_HUB_DEFAULT_URL}/cybersecurity-solutions/open-bas-scenarios`,
    },
    {
      icon: Slack,
      color: theme.palette.ai.main,
      title: t('getting_started_resource_slack'),
      description: t('getting_started_resource_slack_text'),
      href: 'https://community.filigran.io',
    },
    {
      icon: Github,
      color: theme.palette.common.grey,
      title: t('getting_started_resource_github'),
      description: t('getting_started_resource_github_text'),
      href: 'https://github.com/OpenAEV-Platform/openaev',
    },
  ];

  return (
    <div>
      <GettingStartedSectionHeader
        title={t('getting_started_resources')}
        subtitle={t('getting_started_resources_explanation')}
      />
      <Box sx={{
        display: 'grid',
        gap: 2,
        marginTop: 2,
        gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
      }}
      >
        {resources.map(resource => (
          <ResourceCard key={resource.href} resource={resource} />
        ))}
      </Box>
    </div>
  );
};

export default GettingStartedResources;
