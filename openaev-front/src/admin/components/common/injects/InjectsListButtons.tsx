import { ButtonGroup, ButtonGroupItem, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { BarChartOutlined, ReorderOutlined, ViewTimelineOutlined } from '@mui/icons-material';
import { type FunctionComponent, useContext } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { InjectContext, PermissionsContext, ViewModeContext } from '../Context';
import InjectImportMenu from './InjectImportMenu';

const useStyles = makeStyles()(() => ({
  container: {
    display: 'flex',
    justifyContent: 'flex-end',
    alignItems: 'center',
    gap: 10,
  },
}));

interface Props {
  setViewMode?: (mode: string) => void;
  availableButtons: string[];
  onImportedInjects?: () => void;
}

const InjectsListButtons: FunctionComponent<Props> = ({
  setViewMode,
  availableButtons,
  onImportedInjects,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const injectContext = useContext(InjectContext);
  const viewModeContext = useContext(ViewModeContext);
  const { permissions } = useContext(PermissionsContext);

  const hasImportModesEnabled = () => !!injectContext.onImportInjectFromXls || !!injectContext.onImportInjectFromJson;

  return (
    <div className={classes.container}>
      {hasImportModesEnabled()
        && permissions.canManage && <InjectImportMenu onImportedInjects={onImportedInjects} />}
      <ButtonGroup
        size="md"
        usecase="isolated"
        style={{ float: 'right' }}
        aria-label="Change view mode"
        value={viewModeContext}
        onValueChange={next => setViewMode?.(next as typeof viewModeContext)}
      >
        {(!!setViewMode && availableButtons.includes('list'))
          && (
            <Tooltip>
              <TooltipTrigger asChild>
                <ButtonGroupItem
                  value="list"
                  aria-label="List view mode"
                  icon={<ReorderOutlined fontSize="small" />}
                />
              </TooltipTrigger>
              <TooltipContent>{t('List view')}</TooltipContent>
            </Tooltip>
          )}
        {(!!setViewMode && availableButtons.includes('chain'))
          && (
            <Tooltip>
              <TooltipTrigger asChild>
                <ButtonGroupItem
                  value="chain"
                  aria-label="Interactive view mode"
                  icon={<ViewTimelineOutlined fontSize="small" />}
                />
              </TooltipTrigger>
              <TooltipContent>{t('Interactive view')}</TooltipContent>
            </Tooltip>
          )}
        {(!!setViewMode && availableButtons.includes('distribution'))
          && (
            <Tooltip>
              <TooltipTrigger asChild>
                <ButtonGroupItem
                  value="distribution"
                  aria-label="Distribution view mode"
                  icon={<BarChartOutlined fontSize="small" />}
                />
              </TooltipTrigger>
              <TooltipContent>{t('Distribution view')}</TooltipContent>
            </Tooltip>
          )}
      </ButtonGroup>
    </div>
  );
};

export default InjectsListButtons;
