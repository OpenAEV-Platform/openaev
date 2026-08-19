import { Paper } from '@filigran/design-system';
import { OpenInNew } from '@mui/icons-material';
import { Box, Button, Link as MUILink, Skeleton, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { searchScenarios } from '../../../actions/scenarios/scenario-actions';
import { buildFilter } from '../../../components/common/queryable/filter/FilterUtils';
import type { Page } from '../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import ExpandableMarkdown from '../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../components/i18n';
import ItemCategory from '../../../components/ItemCategory';
import ItemSeverity from '../../../components/ItemSeverity';
import ItemTags from '../../../components/ItemTags';
import PlatformIconGroup from '../../../components/PlatformIconGroup';
import { SCENARIO_BASE_URL } from '../../../constants/BaseUrls';
import { type FilterGroup, type Scenario } from '../../../utils/api-types';
import GettingStartedSectionHeader from './GettingStartedSectionHeader';

// A starter-pack scenario rendered as a marketplace card: category + severity
// header band, clamped title, expandable description, platforms / tags meta
// row and the launch CTA.
// The hover lives in a class rather than in `style`: a pseudo-selector cannot
// be written inline, and a class adds no DOM level.
const useStyles = makeStyles()(theme => ({
  carte: {
    'transition': 'transform 150ms ease, border-color 150ms ease',
    '&:hover': {
      transform: 'translateY(-2px)',
      borderColor: alpha(theme.palette.primary.main, 0.45),
    },
  },
}));

const ScenarioCard = ({ scenario }: { scenario: Scenario }) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const theme = useTheme();

  return (
    <Paper
      padding={0}
      data-testid="getting-started-scenario-card"
      className={classes.carte}
      style={{
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
      }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 1,
        padding: theme.spacing(1.5, 2),
        background: `linear-gradient(120deg, ${alpha(theme.palette.primary.main, 0.12)} 0%, transparent 70%)`,
        borderBottom: `1px solid ${alpha(theme.palette.text.primary, 0.06)}`,
      }}
      >
        <Box sx={{
          color: 'primary.main',
          fontSize: 13,
          minWidth: 0,
        }}
        >
          <ItemCategory
            category={scenario.scenario_category ?? 'attack-scenario'}
            label={t(scenario.scenario_category ?? 'attack-scenario')}
            size="small"
          />
        </Box>
        <ItemSeverity
          label={t(scenario.scenario_severity ?? 'Unknown')}
          severity={scenario.scenario_severity ?? 'Unknown'}
          variant="inList"
        />
      </Box>
      <Box sx={{
        padding: theme.spacing(2, 2, 0),
        display: 'flex',
        flexDirection: 'column',
        gap: 1,
        flexGrow: 1,
      }}
      >
        <Typography sx={{
          fontFamily: '"Geologica", sans-serif',
          fontSize: 15,
          fontWeight: 600,
          lineHeight: 1.35,
          display: '-webkit-box',
          WebkitLineClamp: 2,
          WebkitBoxOrient: 'vertical',
          overflow: 'hidden',
          wordBreak: 'break-word',
        }}
        >
          {scenario.scenario_name}
        </Typography>
        <Box sx={{
          'fontSize': 13,
          'color': 'text.secondary',
          'lineHeight': 1.55,
          '& p': { margin: theme.spacing(0, 0, 1) },
        }}
        >
          <ExpandableMarkdown
            source={scenario.scenario_description}
            limit={220}
          />
        </Box>
      </Box>
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        flexWrap: 'wrap',
        padding: theme.spacing(1.5, 2),
        marginTop: 1,
        borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.06)}`,
      }}
      >
        {(scenario.scenario_platforms ?? []).length > 0 && (
          <PlatformIconGroup platforms={scenario.scenario_platforms} width={18} />
        )}
        {(scenario.scenario_tags ?? []).length > 0 && (
          <ItemTags variant="reduced-view" tags={scenario.scenario_tags} />
        )}
        <div style={{ flex: 1 }} />
        <MUILink
          href="https://docs.openaev.io/latest/usage/scenarios-and-simulations/"
          target="_blank"
          rel="noopener noreferrer"
          underline="hover"
          sx={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 0.5,
            fontSize: 12.5,
            whiteSpace: 'nowrap',
          }}
        >
          <OpenInNew sx={{ fontSize: 14 }} />
          {t('learn_more')}
        </MUILink>
        <Button
          variant="contained"
          color="primary"
          size="small"
          component={Link}
          to={`${SCENARIO_BASE_URL}/${scenario.scenario_id}`}
        >
          {t('try_scenario')}
        </Button>
      </Box>
    </Paper>
  );
};

const GettingStartedScenarios = () => {
  const { t } = useFormatter();

  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [loading, setLoading] = useState(true);

  const filter: FilterGroup = {
    mode: 'and',
    filters: [
      buildFilter('scenario_dependencies', ['STARTERPACK'], 'contains'),
    ],
  };
  const input = buildSearchPagination({ filterGroup: filter });
  useEffect(() => {
    setLoading(true);
    searchScenarios(input).then((result: { data: Page<Scenario> }) => setScenarios(result.data.content))
      .finally(() => setLoading(false));
  }, []);

  if (!loading && scenarios.length === 0) {
    return null;
  }

  return (
    <div>
      <GettingStartedSectionHeader
        title={t('getting_started_scenarios')}
        subtitle={t('getting_started_scenarios_explanation')}
      />
      <Box sx={{
        display: 'grid',
        gap: 2.5,
        marginTop: 2,
        gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))',
        alignItems: 'stretch',
      }}
      >
        {loading
          ? [0, 1, 2].map(idx => (
              <Skeleton
                key={idx}
                variant="rounded"
                height={260}
                sx={{ borderRadius: 1 }}
              />
            ))
          : scenarios.map(scenario => (
              <ScenarioCard key={scenario.scenario_id} scenario={scenario} />
            ))}
      </Box>
    </div>
  );
};

export default GettingStartedScenarios;
