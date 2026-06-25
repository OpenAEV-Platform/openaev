export const HEADING_LABELS: Record<string, string> = {
  0: 'Paragraph',
  1: 'Heading 1',
  2: 'Heading 2',
  3: 'Heading 3',
  4: 'Heading 4',
  5: 'Heading 5',
  6: 'Heading 6',
};

/** Font families available in the toolbar. */
export const FONT_FAMILIES: {
  label: string;
  value: string;
}[] = [
  {
    label: 'Default',
    value: '',
  },
  {
    label: 'Arial',
    value: 'Arial, sans-serif',
  },
  {
    label: 'Georgia',
    value: 'Georgia, serif',
  },
  {
    label: 'Courier New',
    value: '"Courier New", monospace',
  },
  {
    label: 'Times New Roman',
    value: '"Times New Roman", serif',
  },
  {
    label: 'Trebuchet MS',
    value: '"Trebuchet MS", sans-serif',
  },
  {
    label: 'Verdana',
    value: 'Verdana, sans-serif',
  },
  {
    label: 'Tahoma',
    value: 'Tahoma, sans-serif',
  },
  {
    label: 'Lucida Sans Unicode',
    value: '"Lucida Sans Unicode", "Lucida Grande", sans-serif',
  },
];

/** Predefined highlight (marker) colors available in the toolbar. */
export const HIGHLIGHT_PALETTE: {
  label: string;
  value: string;
}[] = [
  {
    label: 'Yellow',
    value: '#FFFF00',
  },
  {
    label: 'Green',
    value: '#00FF7F',
  },
  {
    label: 'Cyan',
    value: '#00FFFF',
  },
  {
    label: 'Pink',
    value: '#FF69B4',
  },
  {
    label: 'Orange',
    value: '#FFA500',
  },
  {
    label: 'Purple',
    value: '#DA70D6',
  },
  {
    label: 'Blue',
    value: '#87CEEB',
  },
  {
    label: 'Red',
    value: '#FF6347',
  },
];

/** Predefined font colors available in the toolbar. */
export const COLOR_PALETTE: {
  label: string;
  value: string;
}[] = [
  {
    label: 'Default',
    value: '',
  },
  {
    label: 'Black',
    value: '#000000',
  },
  {
    label: 'Dark Gray',
    value: '#4D4D4D',
  },
  {
    label: 'Gray',
    value: '#9B9B9B',
  },
  {
    label: 'White',
    value: '#FFFFFF',
  },
  {
    label: 'Red',
    value: '#E53935',
  },
  {
    label: 'Orange',
    value: '#FB8C00',
  },
  {
    label: 'Yellow',
    value: '#FDD835',
  },
  {
    label: 'Green',
    value: '#43A047',
  },
  {
    label: 'Teal',
    value: '#00897B',
  },
  {
    label: 'Blue',
    value: '#1E88E5',
  },
  {
    label: 'Indigo',
    value: '#3949AB',
  },
  {
    label: 'Purple',
    value: '#8E24AA',
  },
  {
    label: 'Pink',
    value: '#D81B60',
  },
];

/** Text alignment options available in the toolbar. */
export const TEXT_ALIGNMENTS: {
  label: string;
  value: string;
}[] = [
  {
    label: 'Align Left',
    value: 'left',
  },
  {
    label: 'Align Center',
    value: 'center',
  },
  {
    label: 'Align Right',
    value: 'right',
  },
  {
    label: 'Justify',
    value: 'justify',
  },
];
export const FONT_SIZES: {
  label: string;
  value: string;
}[] = [
  {
    label: 'Tiny',
    value: '10px',
  },
  {
    label: 'Small',
    value: '13px',
  },
  {
    label: 'Default',
    value: '',
  },
  {
    label: 'Big',
    value: '20px',
  },
  {
    label: 'Huge',
    value: '30px',
  },
];

/** Programming languages available in the code block toolbar. */
export const CODE_LANGUAGES: {
  label: string;
  value: string;
}[] = [{
  label: 'Plain Text',
  value: '',
},
{
  label: 'Bash / Shell',
  value: 'bash',
},
{
  label: 'C',
  value: 'c',
},
{
  label: 'C++',
  value: 'cpp',
},
{
  label: 'C#',
  value: 'csharp',
},
{
  label: 'CSS',
  value: 'css',
},
{
  label: 'Go',
  value: 'go',
},
{
  label: 'HTML',
  value: 'html',
},
{
  label: 'Java',
  value: 'java',
},
{
  label: 'JavaScript',
  value: 'javascript',
},
{
  label: 'JSON',
  value: 'json',
},
{
  label: 'Kotlin',
  value: 'kotlin',
},
{
  label: 'PHP',
  value: 'php',
},
{
  label: 'Python',
  value: 'python',
},
{
  label: 'Ruby',
  value: 'ruby',
},
{
  label: 'Rust',
  value: 'rust',
},
{
  label: 'SQL',
  value: 'sql',
},
{
  label: 'Swift',
  value: 'swift',
},
{
  label: 'TypeScript',
  value: 'typescript',
},
{
  label: 'XML',
  value: 'xml',
},
{
  label: 'YAML',
  value: 'yaml',
},
];

/** Special characters grouped by category for the character picker. */
export const SPECIAL_CHARS: {
  category: string;
  chars: {
    char: string;
    label: string;
  }[];
}[] = [
  {
    category: 'Punctuation & Typography',
    chars: [
      {
        char: '©',
        label: 'Copyright',
      },
      {
        char: '®',
        label: 'Registered',
      },
      {
        char: '™',
        label: 'Trade Mark',
      },
      {
        char: '§',
        label: 'Section',
      },
      {
        char: '¶',
        label: 'Pilcrow',
      },
      {
        char: '†',
        label: 'Dagger',
      },
      {
        char: '‡',
        label: 'Double Dagger',
      },
      {
        char: '•',
        label: 'Bullet',
      },
      {
        char: '…',
        label: 'Ellipsis',
      },
      {
        char: '—',
        label: 'Em Dash',
      },
      {
        char: '–',
        label: 'En Dash',
      },
      {
        char: '°',
        label: 'Degree',
      },
      {
        char: '′',
        label: 'Prime',
      },
      {
        char: '″',
        label: 'Double Prime',
      },
      {
        char: '«',
        label: 'Left Guillemet',
      },
      {
        char: '»',
        label: 'Right Guillemet',
      },
      {
        char: '\u2018',
        label: 'Left Single Quote',
      },
      {
        char: '\u2019',
        label: 'Right Single Quote',
      },
      {
        char: '\u201C',
        label: 'Left Double Quote',
      },
      {
        char: '\u201D',
        label: 'Right Double Quote',
      },
    ],
  },
  {
    category: 'Currency',
    chars: [
      {
        char: '€',
        label: 'Euro',
      },
      {
        char: '£',
        label: 'Pound',
      },
      {
        char: '¥',
        label: 'Yen',
      },
      {
        char: '¢',
        label: 'Cent',
      },
      {
        char: '₹',
        label: 'Rupee',
      },
      {
        char: '₽',
        label: 'Ruble',
      },
      {
        char: '₩',
        label: 'Won',
      },
      {
        char: '₿',
        label: 'Bitcoin',
      },
      {
        char: '₺',
        label: 'Lira',
      },
      {
        char: '₫',
        label: 'Dong',
      },
    ],
  },
  {
    category: 'Math',
    chars: [
      {
        char: '±',
        label: 'Plus-Minus',
      },
      {
        char: '×',
        label: 'Multiplication',
      },
      {
        char: '÷',
        label: 'Division',
      },
      {
        char: '∞',
        label: 'Infinity',
      },
      {
        char: '√',
        label: 'Square Root',
      },
      {
        char: '∑',
        label: 'Summation',
      },
      {
        char: '∏',
        label: 'Product',
      },
      {
        char: '∂',
        label: 'Partial Derivative',
      },
      {
        char: '∫',
        label: 'Integral',
      },
      {
        char: '≈',
        label: 'Almost Equal',
      },
      {
        char: '≠',
        label: 'Not Equal',
      },
      {
        char: '≤',
        label: 'Less or Equal',
      },
      {
        char: '≥',
        label: 'Greater or Equal',
      },
      {
        char: 'π',
        label: 'Pi',
      },
      {
        char: 'μ',
        label: 'Mu',
      },
      {
        char: 'Ω',
        label: 'Omega',
      },
      {
        char: '∆',
        label: 'Delta',
      },
      {
        char: '∇',
        label: 'Nabla',
      },
      {
        char: '∈',
        label: 'Element Of',
      },
      {
        char: '∉',
        label: 'Not Element Of',
      },
    ],
  },
  {
    category: 'Arrows',
    chars: [
      {
        char: '←',
        label: 'Left Arrow',
      },
      {
        char: '→',
        label: 'Right Arrow',
      },
      {
        char: '↑',
        label: 'Up Arrow',
      },
      {
        char: '↓',
        label: 'Down Arrow',
      },
      {
        char: '↔',
        label: 'Left-Right Arrow',
      },
      {
        char: '↕',
        label: 'Up-Down Arrow',
      },
      {
        char: '⇐',
        label: 'Left Double Arrow',
      },
      {
        char: '⇒',
        label: 'Right Double Arrow',
      },
      {
        char: '⇑',
        label: 'Up Double Arrow',
      },
      {
        char: '⇓',
        label: 'Down Double Arrow',
      },
      {
        char: '⇔',
        label: 'Left-Right Double Arrow',
      },
      {
        char: '↩',
        label: 'Return Arrow',
      },
      {
        char: '↪',
        label: 'Arrow Right Hook',
      },
      {
        char: '↗',
        label: 'North-East Arrow',
      },
      {
        char: '↘',
        label: 'South-East Arrow',
      },
    ],
  },
  {
    category: 'Greek',
    chars: [
      {
        char: 'α',
        label: 'Alpha',
      },
      {
        char: 'β',
        label: 'Beta',
      },
      {
        char: 'γ',
        label: 'Gamma',
      },
      {
        char: 'δ',
        label: 'Delta',
      },
      {
        char: 'ε',
        label: 'Epsilon',
      },
      {
        char: 'ζ',
        label: 'Zeta',
      },
      {
        char: 'η',
        label: 'Eta',
      },
      {
        char: 'θ',
        label: 'Theta',
      },
      {
        char: 'λ',
        label: 'Lambda',
      },
      {
        char: 'ξ',
        label: 'Xi',
      },
      {
        char: 'ρ',
        label: 'Rho',
      },
      {
        char: 'σ',
        label: 'Sigma',
      },
      {
        char: 'τ',
        label: 'Tau',
      },
      {
        char: 'φ',
        label: 'Phi',
      },
      {
        char: 'χ',
        label: 'Chi',
      },
      {
        char: 'ψ',
        label: 'Psi',
      },
      {
        char: 'ω',
        label: 'Omega (lower)',
      },
    ],
  },
  {
    category: 'Misc',
    chars: [
      {
        char: '★',
        label: 'Black Star',
      },
      {
        char: '☆',
        label: 'White Star',
      },
      {
        char: '✓',
        label: 'Check Mark',
      },
      {
        char: '✗',
        label: 'Cross Mark',
      },
      {
        char: '♠',
        label: 'Spade',
      },
      {
        char: '♣',
        label: 'Club',
      },
      {
        char: '♥',
        label: 'Heart',
      },
      {
        char: '♦',
        label: 'Diamond',
      },
      {
        char: '♪',
        label: 'Musical Note',
      },
      {
        char: '♫',
        label: 'Beamed Notes',
      },
      {
        char: '☀',
        label: 'Sun',
      },
      {
        char: '☁',
        label: 'Cloud',
      },
      {
        char: '☂',
        label: 'Umbrella',
      },
      {
        char: '☎',
        label: 'Phone',
      },
      {
        char: '✉',
        label: 'Envelope',
      },
    ],
  },
];
