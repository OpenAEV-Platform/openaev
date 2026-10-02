import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@filigran/design-system';
import { Popover } from '@mui/material';
import { type FunctionComponent } from 'react';

interface Props {
  handleChangeValue: (value: string) => void;
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
        <div style={{ marginBottom: 15 }}>
          <Select
            value={element || availableValues[0]}
            onValueChange={handleChangeValue}
          >
            <SelectTrigger className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {(availableValues ?? []).map(value => (
                <SelectItem key={value} value={value}>
                  {value}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
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
        className: 'fds-filter-popover',
        elevation: 1,
        style: { marginTop: 10 },
      }}
    >
      <div
        style={{
          // Figma node 7346:48677: the panel stacks its fields with
          // `--spacing-2` between them inside `--spacing-2` of padding. The
          // node's `--spacing-4` is the FIELD's own left padding, not the
          // panel's.
          display: 'flex',
          flexDirection: 'column',
          gap: 8,
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
