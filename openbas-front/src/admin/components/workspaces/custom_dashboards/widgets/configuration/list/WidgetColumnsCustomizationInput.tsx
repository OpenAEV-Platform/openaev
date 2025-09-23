import { DragDropContext, Draggable, Droppable, type DropResult } from '@hello-pangea/dnd';
import { Close, DragIndicatorOutlined } from '@mui/icons-material';
import { AccordionDetails, Box, Button, Checkbox, IconButton, List, ListItem, ListItemIcon, ListItemText, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect } from 'react';

import { Accordion, AccordionSummary } from '../../../../../../../components/common/Accordion';
import { useFormatter } from '../../../../../../../components/i18n';

type ColumnMeta = {
  attribute: string;
  label: string;
};

type WidgetConfigColumnsCustomizationProps = {
  availableColumns: ColumnMeta[];
  defaultColumns: ColumnMeta[];
  value?: ColumnMeta[];
  onChange: (columns: ColumnMeta[]) => void;
};

const WidgetColumnsCustomizationInput: FunctionComponent<WidgetConfigColumnsCustomizationProps> = ({
  availableColumns,
  defaultColumns,
  value = [],
  onChange,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  // Ensure columns only include available columns
  useEffect(() => {
    const filteredColumns = value.filter(col => availableColumns.some(availableCol => availableCol.attribute === col.attribute));
    if (filteredColumns.length !== value.length) {
      onChange(filteredColumns);
    }
  }, [availableColumns, value]);

  const handleDragEnd = (result: DropResult) => {
    if (!result.destination) return;

    const reorderedColumns = Array.from(value);
    const [movedColumn] = reorderedColumns.splice(result.source.index, 1);
    reorderedColumns.splice(result.destination.index, 0, movedColumn);

    onChange(reorderedColumns);
  };

  const handleToggleColumn = (attribute?: string | null) => {
    const columnExists = value.some(col => col.attribute === attribute);
    if (columnExists) {
      onChange(value.filter(col => col.attribute !== attribute));
    } else {
      const columnToAdd = availableColumns.find(col => col.attribute === attribute);
      if (columnToAdd) {
        onChange([...value, columnToAdd]);
      }
    }
  };

  const formatColumnName = ({ attribute, label }: ColumnMeta) => (label ? t(label) : t(attribute ?? ''));

  return (
    <Accordion
      sx={{
        width: '100%',
        marginTop: theme.spacing(2),
      }}
      defaultExpanded
    >
      <AccordionSummary>
        <Typography>{t('Customize columns')}</Typography>
      </AccordionSummary>

      <AccordionDetails sx={{
        background: 'none',
        paddingBlock: theme.spacing(2),
      }}
      >
        <Box sx={{
          display: 'flex',
          width: '100%',
          gap: theme.spacing(2),
        }}
        >
          {/* Available Columns */}
          <Box sx={{ flex: 1 }}>
            <Typography variant="h4">{`${t('Available columns')} (${availableColumns.length})`}</Typography>
            <List sx={{
              border: `1px solid ${theme.palette.common.white}`,
              borderRadius: `${theme.borderRadius}px`,
            }}
            >
              {availableColumns.map(column => (
                <ListItem
                  disablePadding
                  key={column.attribute}
                  sx={{ height: 42 }}
                >
                  <Checkbox
                    checked={value.some(col => col.attribute === column.attribute)}
                    onChange={() => handleToggleColumn(column.attribute)}
                  />
                  <ListItemText primary={formatColumnName(column)} />
                </ListItem>
              ))}
            </List>
          </Box>

          {/* Selected Columns */}
          <Box sx={{
            flex: 1,
            height: '100%',
          }}
          >
            <Typography variant="h4">{`${t('Selected columns')} (${value.length})`}</Typography>
            <DragDropContext onDragEnd={handleDragEnd}>
              <Droppable droppableId="selected_columns">
                {providedDrop => (
                  <List
                    ref={providedDrop.innerRef}
                    {...providedDrop.droppableProps}
                    sx={{
                      border: `1px solid ${theme.palette.common.white}`,
                      borderRadius: `${theme.borderRadius}px`,
                      paddingBlock: theme.spacing(1),
                    }}
                  >
                    {value.map((column, index) => (
                      <Draggable key={column.attribute} draggableId={column.attribute ?? ''} index={index}>
                        {(providedDrag, snapshotDrag) => (
                          <ListItem
                            ref={providedDrag.innerRef}
                            {...providedDrag.draggableProps}
                            divider={index < value.length - 1}
                            sx={{
                              ...providedDrag.draggableProps.style,
                              background: snapshotDrag.isDragging ? 'rgba(0, 0, 0, 0.05)' : 'inherit',
                              height: 42,
                            }}
                            secondaryAction={(
                              <IconButton onClick={() => handleToggleColumn(column.attribute)}>
                                <Close />
                              </IconButton>
                            )}
                          >
                            <ListItemIcon {...providedDrag.dragHandleProps}>
                              <DragIndicatorOutlined />
                            </ListItemIcon>

                            <ListItemText primary={formatColumnName(column)} />
                          </ListItem>
                        )}
                      </Draggable>
                    ))}
                    {providedDrop.placeholder}
                  </List>
                )}
              </Droppable>
            </DragDropContext>
          </Box>
        </Box>

        <Box sx={{
          display: 'flex',
          marginTop: 2,
          justifyContent: 'flex-end',
        }}
        >
          <Button variant="outlined" onClick={() => onChange(defaultColumns)}>
            {t('Reset')}
          </Button>
        </Box>
      </AccordionDetails>
    </Accordion>
  );
};

export default WidgetColumnsCustomizationInput;
