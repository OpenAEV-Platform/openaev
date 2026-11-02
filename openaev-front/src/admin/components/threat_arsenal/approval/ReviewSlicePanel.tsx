import { Select, SelectContent, SelectItem, SelectLabel, SelectTrigger, SelectValue, Text } from '@filigran/design-system';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type SliceField } from './reviewViews';

export interface SliceValue {
  value: string;
  label: string;
  count: number;
}

interface Props {
  /** Fields offered in the picker; authors get no "Submitted by". */
  fields: SliceField[];
  field: SliceField;
  values: SliceValue[];
  total: number;
  selected: string | null;
  onFieldChange: (field: SliceField) => void;
  onSelect: (value: string | null) => void;
}

/**
 * The left rail of the review pages, after GitHub Projects' "Slice by": pick a field (who submitted,
 * domain, where it comes from, what kind of change), then one of its values to narrow the view, each with
 * its count. Same shell as the Active tab's filter sidebar; the slice is part of the view, so it is
 * saved with it.
 */
const ReviewSlicePanel: FunctionComponent<Props> = ({ fields, field, values, total, selected, onFieldChange, onSelect }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const fieldLabels: Record<SliceField, string> = {
    author: t('Submitted by'),
    domain: t('Domain'),
    source: t('Source'),
    kind: t('Type'),
  };

  const row = (value: string | null, label: string, count: number) => {
    const active = value === selected;
    return (
      <li
        key={value ?? 'all'}
        style={{
          borderRadius: theme.shape.borderRadius,
          backgroundColor: active ? alpha(theme.palette.primary.main, 0.12) : undefined,
          boxShadow: active ? `inset 3px 0 0 ${theme.palette.primary.main}` : undefined,
        }}
      >
        <button
          type="button"
          aria-pressed={active}
          onClick={() => onSelect(value)}
          style={{
            all: 'unset',
            boxSizing: 'border-box',
            cursor: 'pointer',
            width: '100%',
            display: 'flex',
            alignItems: 'center',
            gap: theme.spacing(1),
            padding: theme.spacing(0.75, 1),
          }}
        >
          <Text
            variant={active ? 'content-base-bold' : 'content-base'}
            style={{
              flex: 1,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
            title={label}
          >
            {label}
          </Text>
          <Text variant="content-caption" className="text-default-secondary">{String(count)}</Text>
        </button>
      </li>
    );
  };

  return (
    <aside style={{
      width: 250,
      flexShrink: 0,
      position: 'sticky',
      top: theme.spacing(2),
      alignSelf: 'flex-start',
    }}
    >
      <nav
        aria-label={t('Slice by')}
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(1.5),
          padding: theme.spacing(2),
          borderRadius: theme.shape.borderRadius,
          border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
          backgroundColor: theme.palette.background.paper,
        }}
      >
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(0.5),
        }}
        >
          <Select value={field} onValueChange={value => onFieldChange(value as SliceField)}>
            <SelectLabel>{t('Slice by')}</SelectLabel>
            <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
            <SelectContent>
              {fields.map(option => <SelectItem key={option} value={option}>{fieldLabels[option]}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
        <ul style={{
          listStyle: 'none',
          margin: 0,
          padding: 0,
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
        }}
        >
          {row(null, t('All'), total)}
          {values.map(value => row(value.value, value.label, value.count))}
        </ul>
      </nav>
    </aside>
  );
};

export default ReviewSlicePanel;
