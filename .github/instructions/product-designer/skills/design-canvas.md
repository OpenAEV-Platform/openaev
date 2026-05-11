# The Design Canvas — Presenting Multiple Directions

When you have **2 or more contrasting directions** to show, present them on a **design canvas** — a single HTML file with a pannable, zoomable viewport containing multiple artboards.

## When to use a canvas

| Situation | Use canvas? |
|-----------|-------------|
| 2-3 contrasting directions for a feature | ✅ Yes |
| Single direction with variations | ❌ No — use Tweaks panel |
| Iteration on an approved direction | ❌ No — single artboard |
| More than 4 directions | ⚠️ Unusual — are they genuinely contrasting? |

## Structure

```
<DesignCanvas>
  <DCSection title="Direction A — Dense Matrix" description="Compact table-first, analyst persona">
    <DCArtboard label="Overview" width={1440}>
      {/* Full prototype content */}
    </DCArtboard>
    <DCArtboard label="Drill-down modal" width={720}>
      {/* Modal content */}
    </DCArtboard>
  </DCSection>
  <DCSection title="Direction B — Evidence Graph" description="Relationship-first, visual exploration">
    <DCArtboard label="Overview" width={1440}>
      {/* Full prototype content */}
    </DCArtboard>
  </DCSection>
</DesignCanvas>
```

## Rules

### Artboard sizing
- **Desktop full page:** 1440px width
- **Modal/drawer:** 720px width
- **Mobile:** 390px width
- Height: auto (content-sized)

### Labels
- Section title: ≤4 words describing the direction
- Section description: 1 sentence saying what axis it explores
- Artboard label: what the frame shows (not "Screenshot 1")

### Navigation
The canvas provides:
- Pan with mouse drag or scroll
- Zoom with Ctrl+scroll or pinch
- Reset view button
- Minimap (optional, for 3+ directions)

### Content rules
- Each direction is a **fully-realized prototype**, not a sketch or wireframe
- Same mock data across directions (so the user compares design, not data)
- Same scope/features in each direction (unless scope IS the contrast axis)
- Interactive elements work within each artboard (hover, click, state changes)

## Canvas component (inline)

```jsx
const DesignCanvas = ({ children }) => {
  const [transform, setTransform] = React.useState({ x: 0, y: 0, scale: 1 });
  const containerRef = React.useRef(null);
  
  const handleWheel = (e) => {
    if (e.ctrlKey || e.metaKey) {
      e.preventDefault();
      const delta = e.deltaY > 0 ? 0.9 : 1.1;
      setTransform(t => ({ ...t, scale: Math.max(0.25, Math.min(3, t.scale * delta)) }));
    } else {
      setTransform(t => ({ ...t, x: t.x - e.deltaX, y: t.y - e.deltaY }));
    }
  };

  return (
    <div ref={containerRef} onWheel={handleWheel} style={{
      position: 'fixed', inset: 0, overflow: 'hidden',
      background: 'var(--page-background)',
      cursor: 'grab',
    }}>
      <div style={{
        transform: `translate(${transform.x}px, ${transform.y}px) scale(${transform.scale})`,
        transformOrigin: '0 0',
        display: 'flex', flexDirection: 'column', gap: 80,
        padding: 60,
      }}>
        {children}
      </div>
      {/* Reset button */}
      <button onClick={() => setTransform({ x: 0, y: 0, scale: 1 })} style={{
        position: 'fixed', bottom: 16, left: 16,
        padding: '6px 12px', borderRadius: 'var(--radius)',
        background: 'var(--card-bg)', border: '1px solid var(--border-default)',
        color: 'var(--text-secondary)', fontSize: '0.75rem',
        cursor: 'pointer', fontFamily: 'var(--font-mono)',
      }}>Reset view</button>
    </div>
  );
};

const DCSection = ({ title, description, children }) => (
  <div>
    <div style={{ marginBottom: 24 }}>
      <h2 style={{ fontFamily: 'var(--font-title)', fontSize: '1.125rem', fontWeight: 500, color: 'var(--text-primary)' }}>{title}</h2>
      {description && <p style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)', marginTop: 4 }}>{description}</p>}
    </div>
    <div style={{ display: 'flex', gap: 40, alignItems: 'flex-start' }}>
      {children}
    </div>
  </div>
);

const DCArtboard = ({ label, width = 1440, children }) => (
  <div>
    <div style={{
      fontSize: '0.6875rem', fontFamily: 'var(--font-mono)', color: 'var(--text-tertiary)',
      marginBottom: 8, letterSpacing: '0.05em',
    }}>{label} · {width}px</div>
    <div style={{
      width, minHeight: 400,
      background: 'var(--page-background)',
      border: '1px solid var(--border-default)',
      borderRadius: 'var(--radius-lg)',
      overflow: 'hidden',
    }}>
      {children}
    </div>
  </div>
);
```

## Anti-patterns

- ❌ Putting directions in separate HTML files
- ❌ Artboards that scroll internally (content should determine height)
- ❌ Canvas with only 1 direction (just use a normal page)
- ❌ Labeling artboards "Option 1", "Option 2" instead of what they ARE
- ❌ Same direction with different colors presented as "2 directions"
