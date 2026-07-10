import { MenuItem, Popover, Select, type SelectChangeEvent } from '@mui/material';
import { type FunctionComponent } from 'react';

interface Props {
  handleChangeValue: (event: SelectChangeEvent) => void;
  open: boolean;
  onClose: () => void;
  anchorEl?: HTMLElement;
  availableValues: string[];
  element?: string;
}

const ClickableChipPopover: FunctionComponent<Props> = ({
  handleChangeValue,
  open,
  onClose,
  anchorEl,
  availableValues,
  element,
}) => {
  // Standard hooks

  const displayOperatorAndFilter = () => {
    // Specific field

    return (
      <>
        <Select
          value={element || availableValues[0]}
          label="Values"
          variant="standard"
          fullWidth
          onChange={handleChangeValue}
          style={{ marginBottom: 15 }}
        >
          {availableValues?.map(value => (
            <MenuItem key={value} value={value}>
              {value}
            </MenuItem>
          ))}
        </Select>
      </>
    );
  };

  return (
    <Popover
      open={open}
      anchorEl={anchorEl}
      onClose={onClose}
      anchorOrigin={{
        vertical: 'bottom',
        horizontal: 'left',
      }}
      PaperProps={{
        elevation: 1,
        style: { marginTop: 10 },
      }}
    >
      <div
        style={{
          width: 250,

          padding: 8,
        }}
      >
        {displayOperatorAndFilter()}
      </div>
    </Popover>
  );
};
export default ClickableChipPopover;
