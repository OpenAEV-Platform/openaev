import { ShieldOutlined, type SvgIconComponent, TrackChangesOutlined } from '@mui/icons-material';
import { Card, CardActionArea, CardContent, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../components/i18n';
import type { DateHistogramSeries, InjectExpectation, StructuralHistogramSeries } from '../../../../../utils/api-types';
import { getSeries } from './WidgetUtils';

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
  value: DateHistogramSeries[] | StructuralHistogramSeries[];
  onChange: (series: DateHistogramSeries[] | StructuralHistogramSeries[]) => void;
  onSubmit: () => void;
}

const WidgetCreationSecurityCoverageSeries: FunctionComponent<Props> = ({ value, onChange, onSubmit }) => {
  // Standard hooks
  const { t } = useFormatter();
  const { classes } = useStyles();
  const theme = useTheme();

  const onChangeSeries = (series: DateHistogramSeries[] | StructuralHistogramSeries[]) => {
    onChange(series);
    onSubmit();
  };

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
              onClick={() => onChangeSeries(getSeries(perspective.type))}
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
    </div>
  );
};

export default WidgetCreationSecurityCoverageSeries;
