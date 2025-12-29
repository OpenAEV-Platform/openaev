import {
  ArrowDownwardOutlined,
  ArrowForwardOutlined,
  ArrowUpwardOutlined,
  InfoOutlined,
  OpenInFullOutlined,
} from '@mui/icons-material';
import { Box, darken, IconButton, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { memo, useContext } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { type Widget } from '../../../../../utils/api-types';
import { CustomDashboardContext } from '../CustomDashboardContext';
import { ALL_TIME_TIME_RANGE, getTimeRangeFromDashboard, getTimeRangeItem } from './configuration/common/TimeRangeUtils';
import WidgetPopover from './WidgetPopover';
import { getWidgetTitle, type WidgetVizData, WidgetVizDataType } from './WidgetUtils';

interface WidgetTitleProps {
  widget: Widget;
  setFullscreen: (fullscreen: boolean) => void;
  readOnly: boolean;
  handleWidgetUpdate: (widget: Widget) => void;
  handleWidgetDelete: (widgetId: string) => void;
  vizData: WidgetVizData;
}

const WidgetTitle = ({ widget, setFullscreen, readOnly, handleWidgetUpdate, handleWidgetDelete, vizData }: WidgetTitleProps) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const darkerInfoStyle = darken(theme.palette.info.main, 0.7);

  const { customDashboardParameters, customDashboard } = useContext(CustomDashboardContext);

  const createNumberTooltipContent = () => {
    if (vizData.type !== WidgetVizDataType.NUMBER || !vizData.data) {
      return (<></>);
    }
    const { difference_count, previous_interval_count } = vizData.data;
    // extract serie's name (only 1 serie for number widget)
    let resourceName = '';
    if ('series' in widget.widget_config) {
      const seriesItem = widget.widget_config.series[0];
      if (seriesItem) {
        const entityName = seriesItem.filter?.filters
          ?.find(f => f.key === 'base_entity')
          ?.values?.[0];

        resourceName = difference_count <= 1 && difference_count >= -1
          ? t(`${entityName}-singular`)
          : t(`${entityName}-plural`);
      }
    }
    // Compute the widget time range to get the correct sentence
    let widgetTimeRange;
    if (widget.widget_config.time_range == 'DEFAULT') {
      widgetTimeRange = getTimeRangeFromDashboard(customDashboard, customDashboardParameters);
    } else {
      widgetTimeRange = widget.widget_config.time_range;
    }
    const formattedDiff
      = !difference_count || difference_count > 0 ? `+${difference_count}` : `${difference_count}`;
    // Pick icon & color based on difference_count
    let Icon = ArrowForwardOutlined;
    let color = 'grey.400';
    if (difference_count && difference_count > 0) {
      Icon = ArrowUpwardOutlined;
      color = 'success.main';
    } else if (difference_count && difference_count < 0) {
      Icon = ArrowDownwardOutlined;
      color = 'error.main';
    }
    const labelKey = `${getTimeRangeItem(widgetTimeRange)?.label_key}_progression`;
    return (
      <Box display="flex" alignItems="center">
        <Icon sx={{ color }} fontSize="small" />
        <Typography
          variant="body2"
          component="span"
        >
          <Box
            component="strong"
            sx={{ color }}
          >
            {formattedDiff}
          </Box>
          {' '}
          <strong>
            {resourceName}
          </strong>
          {' '}
          <span>
            {t(labelKey)}
            {' '}
          </span>
          {widgetTimeRange !== ALL_TIME_TIME_RANGE && (
            <strong>
              {t('was previously', { previous_number: previous_interval_count })}
            </strong>
          )}
        </Typography>
      </Box>
    );
  };

  return (
    <Box
      display="flex"
      flexDirection="row"
      alignItems="center"
    >
      <Box
        display="flex"
        flexDirection="row"
        alignItems="center"
        paddingTop={theme.spacing(2.5)}
        flex={1}
        minWidth={0}
      >
        <Typography
          variant="h4"
          sx={{
            margin: 0,
            paddingLeft: theme.spacing(2),
            paddingRight: theme.spacing(1),
            textTransform: 'uppercase',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {getWidgetTitle(widget.widget_config.title, widget.widget_type, t)}
        </Typography>
        {'number' === widget.widget_type && (
          <Tooltip
            title={createNumberTooltipContent()}
            placement="right"
            slotProps={{
              tooltip: {
                sx: {
                  bgcolor: darkerInfoStyle,
                  color: theme.palette.getContrastText(darkerInfoStyle),
                  boxShadow: theme.shadows[1],
                },
              },
            }}
          >
            <InfoOutlined
              fontSize="small"
              color="primary"
            />
          </Tooltip>
        )}
      </Box>
      <Box
        display="flex"
        flexDirection="row"
        marginLeft="auto"
      >
        {widget.widget_type === 'security-coverage' && (
          <IconButton
            color="primary"
            className="noDrag"
            onClick={() => setFullscreen(true)}
            size="small"
          >
            <OpenInFullOutlined fontSize="small" />
          </IconButton>
        )}
        {!readOnly && (
          <WidgetPopover
            className="noDrag"
            customDashboardId={customDashboard!.custom_dashboard_id}
            widget={widget}
            onUpdate={widget => handleWidgetUpdate(widget)}
            onDelete={widgetId => handleWidgetDelete(widgetId)}
          />
        )}
      </Box>
    </Box>
  );
};

export default memo(WidgetTitle);
