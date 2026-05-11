# Component Library — Copy-Paste Primitives

These are React (JSX) components you can drop into any prototype. They use CSS variables from `colors_and_type.css` and render correctly in both light and dark themes.

All components are inline-styled for prototype portability. In production, these map to filigran-ui components — but for mockups, self-contained is better.

---

## Eyebrow

The load-bearing label above every section, card, or tile.

```jsx
const Eyebrow = ({ children, style }) => (
  <span style={{
    fontSize: '0.6875rem',
    fontWeight: 500,
    letterSpacing: '0.08em',
    textTransform: 'uppercase',
    fontFamily: 'var(--font-mono)',
    color: 'var(--text-tertiary)',
    ...style,
  }}>{children}</span>
);
```

---

## Num

Monospace number with tabular figures. Use for any numeric value, ID, or timestamp.

```jsx
const Num = ({ children, severity, style }) => (
  <span style={{
    fontFamily: 'var(--font-mono)',
    fontVariantNumeric: 'tabular-nums',
    color: severity ? `var(--severity-${severity})` : 'inherit',
    ...style,
  }}>{children}</span>
);
```

Usage: `<Num severity="critical">14</Num> of <Num>287</Num>`

---

## SeverityBadge

Pill badge for severity levels.

```jsx
const SeverityBadge = ({ level, count }) => {
  const colors = {
    critical: { bg: 'var(--severity-critical)', fg: 'white' },
    high: { bg: 'var(--severity-high)', fg: 'white' },
    medium: { bg: 'var(--severity-medium)', fg: 'hsl(var(--gray-900))' },
    low: { bg: 'var(--severity-low)', fg: 'white' },
    info: { bg: 'var(--severity-info)', fg: 'white' },
    none: { bg: 'var(--severity-none)', fg: 'white' },
  };
  const c = colors[level] || colors.none;
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 'var(--spacing-xs)',
      padding: '2px 8px', borderRadius: 'var(--radius-md)',
      background: c.bg, color: c.fg,
      fontSize: '0.75rem', fontFamily: 'var(--font-mono)', fontWeight: 500,
      fontVariantNumeric: 'tabular-nums',
    }}>
      {count !== undefined && <span>{count}</span>}
      <span style={{ textTransform: 'capitalize' }}>{level}</span>
    </span>
  );
};
```

---

## EntityChip

Colored chip identifying an entity type.

```jsx
const EntityChip = ({ type, label }) => (
  <span style={{
    display: 'inline-flex', alignItems: 'center', gap: 'var(--spacing-xs)',
    padding: '2px 8px', borderRadius: 'var(--radius-md)',
    border: `1px solid var(--entity-${type})`,
    color: `var(--entity-${type})`,
    fontSize: '0.75rem', fontWeight: 500,
    fontFamily: 'var(--font-mono)',
  }}>
    <span style={{
      width: 8, height: 8, borderRadius: '50%',
      background: `var(--entity-${type})`,
    }}/>
    {label || type}
  </span>
);
```

Usage: `<EntityChip type="threats" label="APT-28" />`

---

## Card

Standard content container.

```jsx
const Card = ({ eyebrow, children, style }) => (
  <div style={{
    background: 'var(--card-bg)',
    border: '1px solid var(--border-default)',
    borderRadius: 'var(--radius-lg)',
    padding: 'var(--spacing-l)',
    display: 'flex', flexDirection: 'column', gap: 'var(--spacing-s)',
    ...style,
  }}>
    {eyebrow && <Eyebrow>{eyebrow}</Eyebrow>}
    {children}
  </div>
);
```

---

## MetricTile

Compact metric display with eyebrow + large number + optional detail.

```jsx
const MetricTile = ({ eyebrow, value, detail, severity }) => (
  <div style={{
    display: 'flex', flexDirection: 'column', gap: 'var(--spacing-xs)',
    padding: 'var(--spacing-m)',
    background: 'var(--card-bg)',
    border: '1px solid var(--border-default)',
    borderRadius: 'var(--radius-lg)',
    minWidth: 120,
  }}>
    <Eyebrow>{eyebrow}</Eyebrow>
    <Num severity={severity} style={{ fontSize: '1.5rem', fontWeight: 600 }}>
      {value}
    </Num>
    {detail && <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>{detail}</span>}
  </div>
);
```

---

## StatusPill

Small status indicator (compliant, drifting, stale, etc.).

```jsx
const StatusPill = ({ status, label }) => {
  const map = {
    compliant: { color: 'var(--severity-low)', bg: 'transparent' },
    drifting: { color: 'var(--severity-high)', bg: 'transparent' },
    stale: { color: 'var(--severity-medium)', bg: 'transparent' },
    failed: { color: 'var(--severity-critical)', bg: 'transparent' },
    info: { color: 'var(--severity-info)', bg: 'transparent' },
  };
  const s = map[status] || map.info;
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 'var(--spacing-xs)',
      fontSize: '0.75rem', fontFamily: 'var(--font-mono)', fontWeight: 500,
      color: s.color,
    }}>
      <span style={{
        width: 6, height: 6, borderRadius: '50%',
        background: s.color,
      }}/>
      {label || status}
    </span>
  );
};
```

---

## NavItem

Sidebar navigation item.

```jsx
const NavItem = ({ icon, label, active, count }) => (
  <div style={{
    display: 'flex', alignItems: 'center', gap: 'var(--spacing-s)',
    padding: 'var(--spacing-s) var(--spacing-m)',
    borderRadius: 'var(--radius)',
    background: active ? 'var(--hover-bg)' : 'transparent',
    color: active ? 'var(--text-primary)' : 'var(--text-secondary)',
    cursor: 'pointer', fontSize: '0.8125rem',
    transition: 'background 0.15s',
  }}>
    {icon && <span style={{ opacity: 0.7 }}>{icon}</span>}
    <span style={{ flex: 1 }}>{label}</span>
    {count !== undefined && (
      <Num style={{ fontSize: '0.75rem', color: 'var(--text-tertiary)' }}>{count}</Num>
    )}
  </div>
);
```

---

## TableRow

Data table row with consistent styling.

```jsx
const TableRow = ({ cells, onClick, selected }) => (
  <tr style={{
    borderBottom: '1px solid var(--border-default)',
    background: selected ? 'var(--hover-bg)' : 'transparent',
    cursor: onClick ? 'pointer' : 'default',
    transition: 'background 0.1s',
  }}
    onMouseEnter={e => e.currentTarget.style.background = 'var(--hover-bg)'}
    onMouseLeave={e => e.currentTarget.style.background = selected ? 'var(--hover-bg)' : 'transparent'}
  >
    {cells.map((cell, i) => (
      <td key={i} style={{
        padding: 'var(--spacing-s) var(--spacing-m)',
        fontSize: '0.8125rem',
        fontFamily: typeof cell === 'number' || /^[A-Z0-9\-_.]+$/i.test(String(cell))
          ? 'var(--font-mono)' : 'var(--font-body)',
        fontVariantNumeric: 'tabular-nums',
      }}>{cell}</td>
    ))}
  </tr>
);
```

---

## SegmentedControl

Tab-style selector for views/filters.

```jsx
const SegmentedControl = ({ options, active, onChange }) => (
  <div style={{
    display: 'inline-flex', gap: 1,
    background: 'var(--border-default)',
    borderRadius: 'var(--radius)',
    padding: 1,
  }}>
    {options.map(opt => (
      <button key={opt} onClick={() => onChange?.(opt)} style={{
        padding: 'var(--spacing-xs) var(--spacing-m)',
        borderRadius: 'var(--radius-md)',
        border: 'none',
        background: active === opt ? 'var(--card-bg)' : 'transparent',
        color: active === opt ? 'var(--text-primary)' : 'var(--text-secondary)',
        fontSize: '0.8125rem', fontFamily: 'var(--font-body)',
        cursor: 'pointer', fontWeight: active === opt ? 500 : 400,
      }}>{opt}</button>
    ))}
  </div>
);
```

---

## Placeholder

Honest placeholder for charts, images, or complex visualizations you can't fake.

```jsx
const Placeholder = ({ label, aspect = '16/9', style }) => (
  <div style={{
    aspectRatio: aspect,
    background: 'var(--ds-bg-1)',
    border: '1px dashed var(--border-medium-light)',
    borderRadius: 'var(--radius)',
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    color: 'var(--text-tertiary)',
    fontSize: '0.75rem', fontFamily: 'var(--font-mono)',
    ...style,
  }}>{label}</placeholder>
);
```

Usage: `<Placeholder label="chart · evidence timeline" aspect="21/9" />`

---

## Usage pattern

In your prototype's `<script type="text/babel">`:

```jsx
// The AppShell is already included in the template.
// Set activeNav to the relevant nav item and breadcrumbs to match the page location.

function App() {
  return (
    <AppShell activeNav="simulations" breadcrumbs={['Simulations', 'My simulation', 'Attack path']}>
      <h1 className="txt-title" style={{ marginBottom: 'var(--spacing-l)' }}>
        Attack path
      </h1>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 'var(--spacing-m)', marginTop: 'var(--spacing-l)' }}>
        <MetricTile eyebrow="critical" value="14" detail="of 287" severity="critical" />
        <MetricTile eyebrow="high" value="43" detail="of 287" severity="high" />
        <MetricTile eyebrow="medium" value="89" detail="of 287" severity="medium" />
        <MetricTile eyebrow="covered" value="141" detail="of 287" severity="low" />
      </div>
    </AppShell>
  );
}
```

---

## AppShell

The platform chrome wrapper. **Every prototype must be wrapped in AppShell** — it provides the top bar, left sidebar, and content area matching the real OpenAEV layout.

The AppShell is already defined in `templates/prototype.html`. You don't need to redefine it — just use it.

```jsx
// Already in the template — just wrap your content:
<AppShell
  activeNav="simulations"                              // highlights the matching sidebar item
  breadcrumbs={['Simulations', 'Campaign Alpha']}     // breadcrumb trail
>
  {/* Your page content goes here */}
</AppShell>
```

**Props:**
- `activeNav` — ID of the active sidebar item. Valid IDs: `home`, `dashboards`, `findings`, `scenarios`, `simulations`, `atomic`, `arsenals`, `assets`, `people`, `components`, `integrations`, `settings`
- `breadcrumbs` — Array of strings for the breadcrumb trail (last item = current page)

**Rules:**
- Always set `activeNav` to the most relevant section
- Always provide breadcrumbs matching the page hierarchy
- Never remove or hide the shell — the user must see the prototype in context
- For design canvas (multi-direction), the canvas itself replaces AppShell (canvas has its own viewport)
