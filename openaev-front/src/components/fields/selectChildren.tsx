import { SelectItem } from '@filigran/design-system';
import { ListItemText } from '@mui/material';
import {
  Children,
  isValidElement,
  type ReactElement,
  type ReactNode,
} from 'react';

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
    if (value === undefined) {
      return child;
    }
    return (
      <SelectItem value={String(value)} disabled={disabled}>
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
      return flatten((node.props as { children?: ReactNode }).children);
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
