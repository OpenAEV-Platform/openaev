import { OpenInNew } from '@mui/icons-material';
import { Box, Button, Card, CardActions, CardContent, CardHeader, Link as MUILink, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';

import { searchScenarios } from '../../../actions/scenarios/scenario-actions';
import { buildFilter } from '../../../components/common/queryable/filter/FilterUtils';
import type { Page } from '../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import ExpandableMarkdown from '../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import { SCENARIO_BASE_URL } from '../../../constants/BaseUrls';
import { type FilterGroup, type Scenario } from '../../../utils/api-types';

const GettingStartedScenarios = () => {
  const { t } = useFormatter();
  const theme = useTheme();

  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [loading, setLoading] = useState(true);

  const filter: FilterGroup = {
    mode: 'and',
    filters: [
      buildFilter('from_starter_pack', ['true'], 'eq'),
    ],
  };
  const input = buildSearchPagination({ filterGroup: filter });
  useEffect(() => {
    setLoading(true);
    searchScenarios(input).then((result: { data: Page<Scenario> }) => setScenarios(result.data.content))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return <Loader />;
  }

  if (scenarios.length === 0) {
    return null;
  }

  return (
    <Box>
      <Typography variant="h1">
        {t('getting_started_scenarios')}
      </Typography>
      <Typography variant="h3">
        {t('getting_started_scenarios_explanation')}
      </Typography>
      <div style={{
        display: 'grid',
        gridTemplateColumns: '1fr 1fr 1fr',
        gap: theme.spacing(3),
      }}
      >
        {scenarios.map((scenario: Scenario) => (
          <Card
            key={scenario.scenario_id}
            variant="outlined"
            sx={{
              display: 'flex',
              flexDirection: 'column',
              minHeight: 350,
              borderRadius: 2,
            }}
          >
            <CardHeader
              title={(
                <Typography variant="h6">
                  {scenario.scenario_name}
                </Typography>
              )}
            />
            <CardContent sx={{ flexGrow: 1 }}>
              <ExpandableMarkdown
                source={scenario.scenario_description}
                limit={300}
              />
            </CardContent>
            <CardActions
              sx={{
                display: 'flex',
                justifyContent: 'space-between',
                px: 2,
                pb: 2,
              }}
            >
              <MUILink
                href="https://docs.openbas.io/latest/usage/scenarios_and_simulations/"
                target="_blank"
                rel="noopener noreferrer"
                underline="hover"
                sx={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 1,
                }}
              >
                <OpenInNew fontSize="small" />
                {t('learn_more')}
              </MUILink>
              <Button
                variant="contained"
                color="primary"
                component={Link}
                to={`${SCENARIO_BASE_URL}/${scenario.scenario_id}`}
              >
                {t('try_scenario')}
              </Button>
            </CardActions>
          </Card>
        ))}
      </div>
    </Box>
  );
};

export default GettingStartedScenarios;
