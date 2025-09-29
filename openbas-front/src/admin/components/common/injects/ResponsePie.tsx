import { BugReportOutlined, InfoOutlined, SensorOccupiedOutlined, ShieldOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, memo, useCallback, useMemo } from 'react';
import Chart from 'react-apexcharts';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { type ExpectationResultsByType, type ResultDistribution } from '../../../../utils/api-types';
import { donutChartOptions } from '../../../../utils/Charts';
import { getStatusColor } from '../../../../utils/statusColors';
import { expectationResultTypes } from './expectations/Expectation';

interface Props {
  expectationResultsByTypes?: ExpectationResultsByType[] | null;
  humanValidationLink?: string;
  disableChartAnimation?: boolean;
  hasTitles?: boolean;
  forceSize?: number;
}

const getTotal = (distribution: ResultDistribution[]) => {
  return distribution.reduce((sum, item) => sum + (item.value!), 0)!;
};

const ResponsePie: FunctionComponent<Props> = ({
  expectationResultsByTypes,
  humanValidationLink,
  disableChartAnimation,
  forceSize,
  hasTitles = true,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const iconOverlay = {
    position: 'absolute',
    top: '50%',
    left: '50%',
    transform: 'translate(-50%, -50%)',
    fontSize: forceSize ? forceSize * 0.3 : 35,
  };

  const definedTypes = Array.from(new Set(expectationResultsByTypes?.map(r => r?.type).filter(Boolean)));

  const expectationResultsMap = useMemo(() => {
    const result = expectationResultsByTypes || [];
    return result.reduce((acc, curr) => {
      acc[curr.type] = curr;
      return acc;
    }, {} as Record<string, ExpectationResultsByType>);
  }, [expectationResultsByTypes]);

  const humanResponse = expectationResultsMap['HUMAN_RESPONSE'];
  const pending = useMemo(() => humanResponse?.distribution?.filter(res => res.label === 'Pending' && (res.value ?? 0) > 0) ?? [], [humanResponse]);
  const displayHumanValidationBtn = humanValidationLink && (pending.length > 0);
  const renderIcon = (type: string, hasDistribution: boolean | undefined) => {
    switch (type) {
      case 'prevention':
        return <ShieldOutlined color={hasDistribution ? 'inherit' : 'disabled'} sx={iconOverlay} />;
      case 'detection':
        return <TrackChangesOutlined color={hasDistribution ? 'inherit' : 'disabled'} sx={iconOverlay} />;
      case 'vulnerability':
        return <BugReportOutlined color={hasDistribution ? 'inherit' : 'disabled'} sx={iconOverlay} />;
      default:
        return <SensorOccupiedOutlined color={hasDistribution ? 'inherit' : 'disabled'} sx={iconOverlay} />;
    }
  };

  const Pie = useCallback(({ title, expectationResultsByType, type }: {
    title: string;
    expectationResultsByType?: ExpectationResultsByType;
    type: string;
  }) => {
    const hasDistribution = expectationResultsByType && expectationResultsByType.distribution && expectationResultsByType.distribution.length > 0;
    const labels = hasDistribution
      ? expectationResultsByType.distribution.map(e => `${t(e.label)} (${(((e.value!) / getTotal(expectationResultsByType.distribution!)) * 100).toFixed(1)}%)`)
      : [`${t('No expectation for')} ${title}`];
    const colors = hasDistribution ? expectationResultsByType.distribution.map(e => getStatusColor(theme, e.label)) : ['rgba(202, 203, 206, 0.18)'];
    const data = useMemo(() => (hasDistribution ? expectationResultsByType.distribution.map(e => e.value) : [1]), [expectationResultsByType]);

    return (
      <div style={{
        position: 'relative',
        width: '100%',
        paddingLeft: theme.spacing(1),
        paddingRight: theme.spacing(1),
      }}
      >
        {renderIcon(type, hasDistribution)}
        <Chart
          options={
            donutChartOptions({
              theme,
              labels,
              chartColors: colors,
              displayLegend: false,
              displayLabels: hasDistribution,
              displayValue: hasDistribution,
              displayTooltip: hasDistribution,
              isFakeData: !hasDistribution,
              disableAnimation: disableChartAnimation,
            })
          }
          series={data}
          type="donut"
          height={forceSize ? forceSize : 120}
          width={forceSize ? forceSize : '100%'}
        />
      </div>
    );
  }, [theme, displayHumanValidationBtn, humanValidationLink, pending]);

  const pieTitle = (title: string, expectationResultsByType?: ExpectationResultsByType) => {
    const hasDistribution = expectationResultsByType?.distribution && expectationResultsByType?.distribution.length > 0;
    return (
      <div
        style={{
          ...(!hasDistribution ? { color: theme.palette.text?.disabled } : {}),
          fontWeight: 300,
          textAlign: 'center',
        }}
      >
        {title}
      </div>
    );
  };

  return (
    <div
      id="score_details"
      style={{
        display: 'grid',
        gridTemplateColumns: `repeat(${definedTypes.length}, minmax(25%, 1fr))`,
        width: '100%',
      }}
    >
      {definedTypes
        .sort((a, b) => expectationResultTypes.indexOf(a) - expectationResultTypes.indexOf(b))
        .map(type => (
          <div key={type}>
            <Pie
              type={type.toLowerCase()}
              title={t(type)}
              expectationResultsByType={expectationResultsMap[type]}
            />
            {hasTitles && pieTitle(t(type))}

            {displayHumanValidationBtn && type === 'HUMAN_RESPONSE' && (
              <Button
                startIcon={<InfoOutlined />}
                color="primary"
                component={Link}
                to={humanValidationLink}
                style={{
                  textAlign: 'center',
                  fontSize: 'clamp(0.75rem, 0.5vw, 1rem)',
                  width: '100%',
                }}
              >
                {`${pending.length} ${t('validations needed')}`}
              </Button>
            )}
          </div>
        ))}
    </div>
  );
};

export default memo(ResponsePie);
