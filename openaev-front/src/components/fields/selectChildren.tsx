import { SelectItem } from '@filigran/design-system';
import { ListItemText } from '@mui/material';
import {
  Children,
  isValidElement,
  type ReactElement,
  type ReactNode,
} from 'react';

/**
 * Radix reserves the empty string for "no value" and therefore forbids
 * `SelectItem value=""` — the library says so in its own source. But the legacy
 * MUI lists express "no choice" exactly that way: `<MenuItem value="">` carrying
 * a label such as "Automatic" on the default kill chain. Rendering one would
 * throw and take the whole form down.
 *
 * So an empty option travels through the list under this sentinel, and the two
 * field wrappers translate it back at their boundary — the form value stays the
 * empty string it has always been.
 */
export const EMPTY_OPTION_VALUE = '__fds_empty_option__';

/** Form value -> item value. */
export const toItemValue = (value: unknown): string =>
  value === '' || value === undefined || value === null ? EMPTY_OPTION_VALUE : String(value);

/** Item value -> form value. */
export const toFieldValue = (value: string): string =>
  value === EMPTY_OPTION_VALUE ? '' : value;

/** Whether these children carry an empty-valued option at all. */
export const hasEmptyOption = (children: ReactNode): boolean =>
  Children.toArray(children).some(
    child => isValidElement<ItemLikeProps>(child) && child.props.value === '',
  );

interface ItemLikeProps {
  value?: string;
  disabled?: boolean;
  children?: ReactNode;
}

/**
 * The two react-form Select wrappers take their options as `<MenuItem>`
 * children, and 25 call sites rely on that. Rather than rewrite every one of
 * them, the wrappers translate their own children here: the MUI item becomes a
 * library `SelectItem`, and a `ListItemText` wrapper — which only ever carried
 * the label — is unwrapped so the item's content is the text itself.
 */
const unwrapLabel = (node: ReactNode): ReactNode => {
  if (isValidElement(node) && node.type === ListItemText) {
    const props = node.props as {
      primary?: ReactNode;
      children?: ReactNode;
    };
    return props.children ?? props.primary ?? null;
  }
  return node;
};

export const toSelectItems = (children: ReactNode): ReactNode =>
  Children.map(children, (child) => {
    if (!isValidElement(child)) {
      return child;
    }
    const { value, disabled, children: inner } = (child as ReactElement<ItemLikeProps>).props;
    // Dropped, as `toOptions` drops it: an option list cannot express a child
    // that is not an option, so rendering one here would make the same children
    // produce two different lists.
    if (value === undefined) {
      return null;
    }
    return (
      <SelectItem value={toItemValue(value)} disabled={disabled}>
        {Children.map(inner, unwrapLabel)}
      </SelectItem>
    );
  });

/** Same children, read as a plain option list for the multiple (Combobox) case. */
export const toOptions = (children: ReactNode): {
  value: string;
  label: string;
}[] => {
  const flatten = (node: ReactNode): string => {
    if (node === null || node === undefined || node === false || node === true) {
      return '';
    }
    if (typeof node === 'string' || typeof node === 'number') {
      return String(node);
    }
    if (Array.isArray(node)) {
      return node.map(flatten).join('');
    }
    if (isValidElement(node)) {
      // Through `unwrapLabel`, so a `ListItemText` carrying only `primary`
      // reads the same here as it does on the other path.
      const unwrapped = unwrapLabel(node);
      return unwrapped === node
        ? flatten((node.props as { children?: ReactNode }).children)
        : flatten(unwrapped);
    }
    return '';
  };
  const out: {
    value: string;
    label: string;
  }[] = [];
  Children.forEach(children, (child) => {
    if (!isValidElement(child)) {
      return;
    }
    const { value, children: inner } = (child as ReactElement<ItemLikeProps>).props;
    if (value === undefined) {
      return;
    }
    out.push({
      value: String(value),
      label: flatten(inner) || String(value),
    });
  });
  return out;
};
