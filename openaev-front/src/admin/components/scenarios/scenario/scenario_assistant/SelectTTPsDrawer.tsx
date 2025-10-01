import { Button, Paper } from '@mui/material';
import { makeStyles } from 'tss-react/mui';

import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import type { Filter } from '../../../../../utils/api-types';
import MitreFilter from '../../../common/filters/MitreFilter';

const useStyles = makeStyles()(theme => ({
  TTPMitreContainer: {
    padding: theme.spacing(2),
    overflow: 'scroll',
    height: 'calc(100vh - 120px)', // 120px equal to the header and footer height
  },
  TTPWidgetToolbar: {
    display: 'flex',
    justifyContent: 'flex-end',
    padding: theme.spacing(1),
    height: '55px',
  },
}));

interface Props {
  open: boolean;
  onClose: () => void;
  initialSelectedTTPIds: string[];
  onUpdateAttackPattern: (selectedAttackPatternIds: Set<string>) => void;
}

const SelectTTPsDrawer = ({ open, onClose, initialSelectedTTPIds, onUpdateAttackPattern }: Props) => {
  const { t } = useFormatter();
  const { classes } = useStyles();

  const selectedAttackPatternIds: Set<string> = new Set(initialSelectedTTPIds);

  const onClickAttackPattern = (attackPatternId: string) => {
    // Toggle the attack pattern selection
    if (selectedAttackPatternIds.has(attackPatternId)) {
      selectedAttackPatternIds.delete(attackPatternId);
    } else {
      selectedAttackPatternIds.add(attackPatternId);
    }
  };

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={t('ATT&CK Matrix')}
      variant="full"
      containerStyle={{
        padding: 0,
        maxHeight: '100%',
      }}
    >
      <>
        <MitreFilter
          className={classes.TTPMitreContainer}
          helpers={{
            handleSwitchMode: () => {},
            handleAddFilterWithEmptyValue: (_: Filter) => { },
            handleAddSingleValueFilter: () => { },
            handleAddMultipleValueFilter: () => { },
            handleChangeOperatorFilters: () => { },
            handleClearAllFilters: () => { },
            handleRemoveFilterByKey: () => { },
          }}
          onClick={data => onClickAttackPattern(data)}
          defaultSelectedAttackPatternIds={initialSelectedTTPIds}
        />
        <Paper elevation={1} className={classes.TTPWidgetToolbar}>
          <Button
            variant="contained"
            color="primary"
            onClick={() => onUpdateAttackPattern(selectedAttackPatternIds)}
          >
            {t('Update')}
          </Button>
        </Paper>
      </>
    </Drawer>
  );
};

export default SelectTTPsDrawer;
