import { ShieldOutlined, type SvgIconComponent, TrackChangesOutlined } from '@mui/icons-material';
import { Card, CardActionArea, CardContent, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext, useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { SIMULATIONS } from '../../../../../../../components/common/queryable/filter/constants';
import SimulationField from '../../../../../../../components/fields/SimulationField';
import { useFormatter } from '../../../../../../../components/i18n';
import Loader from '../../../../../../../components/Loader';
import { type InjectExpectation, type Series } from '../../../../../../../utils/api-types';
import type { GroupOption } from '../../../../../../../utils/Option';
import { CustomDashboardContext } from '../../../CustomDashboardContext';
import { extractGroupOptionsFromCustomDashboardParameters, getSeries, updateSimulationFilterOnSeries } from '../../WidgetUtils';

const useStyles = makeStyles()(theme => ({
  container: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    gap: theme.spacing(2),
  },
  content: {
    display: 'grid',
    gridTemplateColumns: '1fr',
    gap: theme.spacing(2),
    justifyItems: 'center',
  },
  allWidth: { gridColumn: 'span 2' },
}));

const perspectives: {
  icon: () => SvgIconComponent;
  title: string;
  description: string;
  type: InjectExpectation['inject_expectation_type'];
}[] = [{
  icon: () => TrackChangesOutlined,
  title: 'Detection',
  description: 'Focus your widget on injects detected by your security system',
  type: 'DETECTION',
}, {
  icon: () => ShieldOutlined,
  title: 'Prevention',
  description: 'Focus your widget on injects prevented by your security system',
  type: 'PREVENTION',
}];

interface Props {
  value: Series[];
  onChange: (series: Series[]) => void;
  onSubmit: () => void;
  isSimulationFilterMandatory?: boolean;
}

const WidgetSecurityCoverageSeriesSelection: FunctionComponent<Props> = ({ value, onChange, onSubmit, isSimulationFilterMandatory = false }) => {
  // Standard hooks
  const { t } = useFormatter();
  const { classes } = useStyles();
  const theme = useTheme();
  const { customDashboard } = useContext(CustomDashboardContext);

  const [simulationId, setSimulationId] = useState<string | undefined>();
  const [loader, setLoader] = useState<boolean>(true);
  const [defaultSimulationOptions, setDefaultSimulationOptions] = useState<Map<string, GroupOption[]>>(new Map());
  const [showSimulationError, setShowSimulationError] = useState<boolean>(false);

  useEffect(() => {
    setDefaultSimulationOptions(extractGroupOptionsFromCustomDashboardParameters(customDashboard?.custom_dashboard_parameters ?? []));
    if (value.length > 0 && value[0].filter?.filters) {
      const simulationId = value[0].filter.filters.find(f => f.key === 'base_simulation_side')?.values?.[0];
      setSimulationId(simulationId);
    }
    setLoader(false);
  }, []);

  const onChangeSeries = (series: Series[]) => {
    onChange(series);
    if (isSimulationFilterMandatory && !simulationId) {
      setShowSimulationError(true);
    } else {
      onSubmit();
    }
  };

  const onSimulationChange = (simulationId: string | undefined) => {
    setSimulationId(simulationId);
    setShowSimulationError(!simulationId);
    if (simulationId && value?.length > 0 && value[0].filter !== undefined) {
      updateSimulationFilterOnSeries(value, simulationId);
      onChange(value);
      onSubmit();
    }
  };

  if (loader) {
    return <Loader />;
  }

  return (
    <div className={classes.container}>
      {perspectives.map((perspective) => {
        const isSelected = value.filter(v => v.filter?.filters?.find(f => f.values?.includes(perspective.type))).length > 0;
        const Icon = perspective.icon();
        return (
          <Card
            key={perspective.title}
            variant="outlined"
            style={{ borderColor: isSelected ? `${theme.palette.primary.main}` : undefined }}
          >
            <CardActionArea
              onClick={() => onChangeSeries(getSeries(perspective.type, simulationId))}
              aria-label={t(perspective.title)}
            >
              <CardContent className={classes.content}>
                <Icon color="primary" fontSize="large" />
                <Typography variant="h5">{perspective.title}</Typography>
                <Typography sx={{ textAlign: 'center' }}>{perspective.description}</Typography>
              </CardContent>
            </CardActionArea>
          </Card>
        );
      })}
      <SimulationField
        label={t('Simulation')}
        required={isSimulationFilterMandatory}
        error={showSimulationError}
        value={simulationId ?? ''}
        className={classes.allWidth}
        onChange={onSimulationChange}
        searchOptionsConfig={{
          filterKey: SIMULATIONS,
          defaultValues: defaultSimulationOptions.get('base_simulation_side'),
        }}
      />
    </div>
  );
};

export default WidgetSecurityCoverageSeriesSelection;
