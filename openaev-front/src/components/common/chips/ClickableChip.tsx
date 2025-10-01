import { Box, Chip, type SelectChangeEvent, Tooltip } from '@mui/material';
import { type FunctionComponent, useRef, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../i18n';
import convertOperatorToIcon from './ChipUtils';
import ClickableChipPopover from './ClickableChipPopover';

const useStyles = makeStyles()(theme => ({
  mode: {
    display: 'inline-block',
    height: '100%',
    backgroundColor: theme.palette.action?.selected,
    margin: '0 4px',
    padding: '0 4px',
  },
  modeTooltip: { margin: '0 4px' },
  container: {
    gap: '4px',
    display: 'flex',
    overflow: 'hidden',
    maxWidth: '400px',
    alignItems: 'center',
    lineHeight: '32px',
    whiteSpace: 'nowrap',
    textOverflow: 'ellipsis',
  },
  interactive: {
    'cursor': 'pointer',
    '&:hover': { textDecorationLine: 'underline' },
  },
}));

export interface Element {
  key: string;
  operator?: string;
  value?: string;
}

interface Props {
  onChange: (newElement: Element) => void;
  pristine: boolean;
  selectedElement: Element;
  availableKeys: string[];
  availableOperators: string[];
  availableValues: string[];
  onDelete?: () => void;
}

const ClickableChip: FunctionComponent<Props> = ({
  onChange,
  pristine,
  selectedElement,
  availableKeys,
  availableOperators,
  availableValues,
  onDelete,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const { classes, cx } = useStyles();

  const chipRef = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(!pristine);
  const [availableOptions, setAvailableOptions] = useState<string[]>([]);
  const [selectedValue, setSelectedValue] = useState<string>();
  const [propertyToChange, setPropertyToChange] = useState<string>('');
  const handleOpen = () => setOpen(true);
  const handleClose = () => setOpen(false);

  const handleRemoveFilter = () => {
    if (onDelete) onDelete();
  };

  const handleChange = (event: SelectChangeEvent) => {
    const newValue = selectedElement;
    switch (propertyToChange) {
      case 'key': {
        newValue.key = event.target.value;
        break;
      }
      case 'operator': {
        newValue.operator = event.target.value;
        break;
      }
      case 'value': {
        newValue.value = event.target.value;
        break;
      }
      default:
        break;
    }
    onChange(newValue);
    setOpen(false);
  };

  const handleClickOpen = (options: string[], property: string, optionValue?: string) => {
    setAvailableOptions(options);
    if (optionValue) setSelectedValue(optionValue);
    if (options.length > 1) handleOpen();
    setPropertyToChange(property);
  };

  const toValues = (opts: string[] | undefined, isTooltip: boolean) => {
    if (opts !== undefined) {
      return opts.map((o, idx) => {
        let or = <></>;
        if (idx > 0) {
          or = (
            <div className={cx({
              [classes.mode]: !isTooltip,
              [classes.modeTooltip]: isTooltip,
            })}
            >
              {t('OR')}
            </div>
          );
        }
        return (
          <div key={o}>
            {or}
            <span>
              {' '}
              {o}
            </span>
          </div>
        );
      });
    }
    return (
      <span key="undefined">
        {' '}
        {t('undefined')}
      </span>
    );
  };

  const filterValues = (isTooltip: boolean) => {
    return (
      <span className={classes.container}>
        <strong
          className={availableKeys.length > 1 ? classes.interactive : undefined}
          onClick={() => handleClickOpen(availableKeys, 'key', selectedElement.key)}
        >
          {t(selectedElement.key)}
        </strong>
        <Box
          sx={{
            display: 'flex',
            flexDirection: 'row',
            overflow: 'hidden',
          }}
          className={availableOperators.length > 1 ? classes.interactive : undefined}
          onClick={() => handleClickOpen(availableOperators, 'operator', selectedElement.operator)}
        >
          {convertOperatorToIcon(t, selectedElement.operator)}
        </Box>
        <Box
          sx={{
            display: 'flex',
            flexDirection: 'row',
            overflow: 'hidden',
          }}
          className={availableValues.length > 1 ? classes.interactive : undefined}
          onClick={() => handleClickOpen(availableValues, 'value', selectedElement.value)}
        >
          {toValues(selectedElement.value ? [selectedElement.value] : [], isTooltip)}
        </Box>
      </span>
    );
  };

  const chipVariant = 'filled';

  return (
    <>
      <Tooltip
        title={filterValues(true)}
      >
        <Chip
          variant={chipVariant}
          label={filterValues(false)}
          onDelete={onDelete ? handleRemoveFilter : undefined}
          sx={{ borderRadius: 1 }}
          ref={chipRef}
        />
      </Tooltip>
      {chipRef?.current
        && (
          <ClickableChipPopover
            handleChangeValue={handleChange}
            open={open}
            onClose={handleClose}
            anchorEl={chipRef.current}
            availableValues={availableOptions}
            element={selectedValue}
          />
        )}
    </>
  );
};
export default ClickableChip;
