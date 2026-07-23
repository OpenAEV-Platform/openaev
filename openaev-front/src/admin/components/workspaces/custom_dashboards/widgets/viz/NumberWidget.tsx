import {
  BoltOutlined,
  BugReportOutlined,
  CrisisAlertOutlined,
  DnsOutlined,
  HubOutlined,
  MovieFilterOutlined,
  NumbersOutlined,
} from '@mui/icons-material';
import { Box, Button, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Binoculars, SelectGroup } from 'mdi-material-ui';
import { type FunctionComponent, memo, type ReactElement, useCallback, useContext, useMemo } from 'react';

import { type EsCountInterval, type Widget } from '../../../../../../utils/api-types';
import useCountUp from '../../../../../../utils/hooks/useCountUp';
import { compactNumber } from '../../../../../../utils/number';
import { CustomDashboardContext } from '../../CustomDashboardContext';
import TrendChip from './TrendChip';

interface Props {
  widgetId: string;
  widgetConfig?: Widget['widget_config'];
  data: EsCountInterval;
}

interface EntityVisual {
  icon: ReactElement;
  color: string;
}

const NumberWidget: FunctionComponent<Props> = ({ widgetId, widgetConfig, data }) => {
  const theme = useTheme();
  const { openWidgetDataDrawer } = useContext(CustomDashboardContext);

  const animatedCount = useCountUp(data.interval_count ?? 0, 1200);

  // Resolve an icon + accent color from the widget's base_entity filter
  const visual = useMemo<EntityVisual>(() => {
    let entity = '';
    if (widgetConfig && 'series' in widgetConfig) {
      entity = widgetConfig.series?.[0]?.filter?.filters
        ?.find(f => f.key === 'base_entity')
        ?.values?.[0] ?? '';
    }
    // Icons intentionally mirror the left navigation bar for consistency
    const visuals: Record<string, EntityVisual> = {
      'scenario': {
        icon: <MovieFilterOutlined />,
        color: theme.palette.secondary.main,
      },
      'simulation': {
        icon: <HubOutlined />,
        color: theme.palette.primary.main,
      },
      'inject': {
        icon: <BoltOutlined />,
        color: theme.palette.warning.main,
      },
      'endpoint': {
        icon: <DnsOutlined />,
        color: theme.palette.primary.main,
      },
      'finding': {
        icon: <Binoculars />,
        color: theme.palette.error.main,
      },
      'vulnerable-endpoint': {
        icon: <BugReportOutlined />,
        color: theme.palette.error.main,
      },
      'asset-group': {
        icon: <SelectGroup />,
        color: theme.palette.primary.main,
      },
      'expectation-inject': {
        icon: <CrisisAlertOutlined />,
        color: theme.palette.warning.main,
      },
    };
    return visuals[entity] ?? {
      icon: <NumbersOutlined />,
      color: theme.palette.primary.main,
    };
  }, [widgetConfig, theme]);

  const onClick = useCallback(() => {
    openWidgetDataDrawer({
      widgetId,
      filter_values_map: {},
      series_index: 0,
    });
  }, [openWidgetDataDrawer, widgetId]);

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        overflow: 'hidden',
        height: '100%',
        paddingBottom: 8,
      }}
    >
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 42,
          height: 42,
          borderRadius: 1,
          flexShrink: 0,
          color: visual.color,
          background: `${visual.color}1a`,
          boxShadow: `inset 0 0 14px ${visual.color}22`,
        }}
      >
        {visual.icon}
      </Box>
      <Tooltip title={data.interval_count != null ? data.interval_count.toLocaleString() : ''}>
        <Button
          onClick={onClick}
          variant="text"
          className="noDrag"
          sx={{
            fontSize: 36,
            height: 46,
            fontWeight: 500,
            fontFamily: '"Geologica", sans-serif',
            padding: 0,
            minWidth: 0,
            color: 'text.primary',
          }}
        >
          {data.interval_count != null ? compactNumber(Math.round(animatedCount)) : '-'}
        </Button>
      </Tooltip>
      <TrendChip
        difference={data.difference_count ?? 0}
        previous={data.previous_interval_count}
      />
    </div>
  );
};

export default memo(NumberWidget);
