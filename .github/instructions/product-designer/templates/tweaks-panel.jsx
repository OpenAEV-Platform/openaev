// Tweaks Panel — reusable floating panel for toggleable variations
// Include via: <script type="text/babel" src="./tweaks-panel.jsx"></script>
// Depends on React (already loaded in prototype.html)

const { useState, useEffect, useCallback } = React;

// --- useTweaks hook (with localStorage persistence) ---
const useTweaks = (defaults) => {
  const storageKey = 'openaev-tweaks';
  const [tweaks, setTweaks] = useState(() => {
    try {
      const saved = localStorage.getItem(storageKey);
      return saved ? { ...defaults, ...JSON.parse(saved) } : defaults;
    } catch { return defaults; }
  });

  useEffect(() => {
    try { localStorage.setItem(storageKey, JSON.stringify(tweaks)); } catch {}
  }, [tweaks]);

  const update = useCallback((key, value) => {
    setTweaks(t => ({ ...t, [key]: value }));
  }, []);

  return [tweaks, update, setTweaks];
};

// --- Panel container ---
const TweaksPanel = ({ children, tweaks, onChange }) => {
  const [open, setOpen] = useState(false);

  return (
    <div style={{
      position: 'fixed', bottom: 16, right: 16, zIndex: 9999,
      fontFamily: 'var(--font-body)', fontSize: '0.8125rem',
    }}>
      {open && (
        <div style={{
          width: 260, maxHeight: '80vh', overflowY: 'auto',
          background: 'var(--card-bg)', border: '1px solid var(--border-default)',
          borderRadius: 'var(--radius-lg)', padding: 'var(--spacing-m)',
          marginBottom: 'var(--spacing-s)',
          display: 'flex', flexDirection: 'column', gap: 'var(--spacing-m)',
        }}>
          <div style={{
            fontSize: '0.6875rem', fontWeight: 500, letterSpacing: '0.08em',
            textTransform: 'uppercase', fontFamily: 'var(--font-mono)',
            color: 'var(--text-tertiary)', marginBottom: 'var(--spacing-xs)',
          }}>tweaks</div>
          {React.Children.map(children, child =>
            React.cloneElement(child, { tweaks, onChange })
          )}
        </div>
      )}
      <button onClick={() => setOpen(!open)} style={{
        width: 40, height: 40, borderRadius: 'var(--radius)',
        background: 'var(--card-bg)', border: '1px solid var(--border-default)',
        color: 'var(--text-secondary)', cursor: 'pointer',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontSize: '1.25rem', marginLeft: 'auto',
      }}>⚙</button>
    </div>
  );
};

// --- Tweak controls ---

const TweakSection = ({ label, children, tweaks, onChange }) => (
  <div>
    <div style={{
      fontSize: '0.6875rem', fontWeight: 500, color: 'var(--text-tertiary)',
      textTransform: 'uppercase', letterSpacing: '0.05em',
      marginBottom: 'var(--spacing-xs)', fontFamily: 'var(--font-mono)',
    }}>{label}</div>
    {React.Children.map(children, child =>
      React.isValidElement(child) ? React.cloneElement(child, { tweaks, onChange }) : child
    )}
  </div>
);

const TweakRadio = ({ name, options, tweaks, onChange }) => {
  const value = tweaks?.[name];
  return (
    <div style={{ display: 'flex', gap: 1, background: 'var(--border-default)', borderRadius: 'var(--radius)', padding: 1 }}>
      {options.map(opt => (
        <button key={opt} onClick={() => onChange?.(name, opt)} style={{
          flex: 1, padding: '4px 8px', borderRadius: 'var(--radius-md)',
          border: 'none', cursor: 'pointer',
          background: value === opt ? 'var(--primary)' : 'transparent',
          color: value === opt ? 'var(--primary-fg)' : 'var(--text-secondary)',
          fontSize: '0.6875rem', fontFamily: 'var(--font-mono)',
          textTransform: 'capitalize',
        }}>{opt}</button>
      ))}
    </div>
  );
};

const TweakToggle = ({ name, label, tweaks, onChange }) => {
  const value = tweaks?.[name];
  return (
    <label style={{ display: 'flex', alignItems: 'center', gap: 'var(--spacing-s)', cursor: 'pointer' }}>
      <div onClick={() => onChange?.(name, !value)} style={{
        width: 32, height: 18, borderRadius: 9, padding: 2,
        background: value ? 'var(--primary)' : 'var(--border-medium-light)',
        transition: 'background 0.15s', cursor: 'pointer',
      }}>
        <div style={{
          width: 14, height: 14, borderRadius: '50%',
          background: 'white',
          transform: value ? 'translateX(14px)' : 'translateX(0)',
          transition: 'transform 0.15s',
        }}/>
      </div>
      <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>{label || name}</span>
    </label>
  );
};

const TweakSelect = ({ name, options, tweaks, onChange }) => {
  const value = tweaks?.[name];
  return (
    <select value={value} onChange={e => onChange?.(name, e.target.value)} style={{
      width: '100%', padding: '4px 8px',
      background: 'var(--card-bg)', border: '1px solid var(--border-default)',
      borderRadius: 'var(--radius)', color: 'var(--text-primary)',
      fontSize: '0.75rem', fontFamily: 'var(--font-mono)',
    }}>
      {options.map(opt => <option key={opt} value={opt}>{opt}</option>)}
    </select>
  );
};

// Expose to global scope
Object.assign(window, {
  useTweaks, TweaksPanel, TweakSection, TweakRadio, TweakToggle, TweakSelect,
});
