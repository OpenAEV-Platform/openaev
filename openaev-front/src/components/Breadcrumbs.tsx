import { Breadcrumbs, type BreadcrumbsItem } from '@filigran/design-system';
import { type CSSProperties, type FunctionComponent } from 'react';
import { Link } from 'react-router';

export const BACK_LABEL = 'backlabel';
export const BACK_URI = 'backuri';

export interface BreadcrumbsElement {
  label: string;
  link?: string;
  current?: boolean;
}

interface BreadcrumbsProps {
  variant: 'standard' | 'list' | 'object';
  elements: BreadcrumbsElement[];
  style?: CSSProperties;
}

// Thin adapter over the design system's breadcrumb: the call sites keep the
// `elements` / `variant` shape they have always used, and the component owns
// the markup (nav > ol > li), the separators, the truncation with its title
// attribute and `aria-current`.
const ProductBreadcrumbs: FunctionComponent<BreadcrumbsProps> = ({ elements, variant, style = {} }) => {
  const items: BreadcrumbsItem[] = elements.map(({ label, link, current }) => ({
    label,
    // A destination on the current entry is ignored by the component, with a
    // dev warning: only an ancestor carries one.
    ...(link && !current ? { to: link } : {}),
    ...(current ? { current } : {}),
  }));

  return (
    <Breadcrumbs
      items={items}
      linkComponent={Link}
      style={{
        marginTop: -5,
        // A standard breadcrumb is followed by its own page header; the list
        // and object variants sit directly above content and keep their gap.
        ...(variant === 'standard' ? {} : { marginBottom: 16 }),
        ...style,
      }}
    />
  );
};

export default ProductBreadcrumbs;
