import { Menu, MenuContent, MenuTrigger, TabsTrigger } from '@filigran/design-system';
import { ExpandMore } from '@mui/icons-material';
import { type FunctionComponent, type ReactNode } from 'react';
import { Link } from 'react-router';

interface Props {
  /** Tab value, as in TabsTrigger. */
  value: string;
  label: string;
  /** Where the name leads: a real link, so ⌘-click, a new browser tab and "Copy link" work. */
  to: string;
  active: boolean;
  /** Accessible name of the arrow, e.g. "Options of the view NJ". */
  menuLabel: string;
  /** The arrow's menu: MenuItem elements. */
  menu: ReactNode;
}

/**
 * A tab in two parts, like the view tabs of GitHub Projects: the name is a routed tab and a real link
 * (one click opens it, ⌘-click opens a new browser tab), the arrow beside it is its own button that
 * opens the tab's menu. Both share the tab's height, hover and active underline, so they read as one
 * tab with two targets (Fitts's Law, Law of Common Region).
 *
 * FDS-WORKAROUND: built from TabsTrigger + Menu because the library has no split tab (TabsMenuTrigger
 * is one button, and TabsList moves it after the tabs) — remove when the library ships a tab with its
 * own menu — see ~/Design_Repositories/ds-proposal-split-tab.md (Tabs RFC amendment).
 * Known prototype debt: the arrow button sits inside role="tablist", which Tabs RULE-11 forbids
 * (aria-required-children); the library version has to place it outside the tablist.
 */
const SplitTab: FunctionComponent<Props> = ({ value, label, to, active, menuLabel, menu }) => {
  // Only classes the library stylesheet ships (an unknown utility fails silently): the active ones; the
  // inactive arrow sets its own transparent underline inline.
  const activeClasses = active ? ' text-filigran-brand-primary border-filigran-brand-primary' : ' text-default-secondary';
  return (
    <span style={{
      display: 'inline-flex',
      alignItems: 'stretch',
    }}
    >
      <TabsTrigger value={value} asChild style={{ paddingRight: 4 }}>
        <Link to={to} aria-current={active ? 'page' : undefined}>{label}</Link>
      </TabsTrigger>
      <Menu>
        <MenuTrigger asChild>
          <button
            type="button"
            aria-label={menuLabel}
            className={`bg-transparent hover:bg-filigran-brand-primary-transparency-10 data-[state=open]:text-filigran-brand-primary${activeClasses}`}
            style={{
              boxSizing: 'border-box',
              height: 44,
              padding: '0 8px 0 2px',
              margin: 0,
              border: 0,
              borderBottomWidth: 2,
              borderBottomStyle: 'solid',
              ...(active ? {} : { borderBottomColor: 'transparent' }),
              borderTopLeftRadius: 0,
              borderTopRightRadius: 4,
              cursor: 'pointer',
              display: 'inline-flex',
              alignItems: 'center',
            }}
          >
            <ExpandMore fontSize="small" />
          </button>
        </MenuTrigger>
        <MenuContent align="start">{menu}</MenuContent>
      </Menu>
    </span>
  );
};

export default SplitTab;
