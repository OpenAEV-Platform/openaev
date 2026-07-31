import * as react from 'react';
import { ReactNode, SVGProps } from 'react';
import * as class_variance_authority_types from 'class-variance-authority/types';
import { VariantProps } from 'class-variance-authority';
import * as DialogPrimitive from '@radix-ui/react-dialog';
import * as lucide_react from 'lucide-react';
import * as SwitchPrimitive from '@radix-ui/react-switch';
import * as TabsPrimitive from '@radix-ui/react-tabs';
import * as TooltipPrimitive from '@radix-ui/react-tooltip';

interface ComponentMeta {
    name: string;
    description: string;
    status: "stable" | "beta" | "deprecated" | "planned";
    version: string;
    radixPrimitive: string;
    /** Full Figma URL of the component frame (including the ?node-id=… parameter) */
    figmaLink?: string;
    /**
     * RAW Figma node ID in colon form, e.g. "1234:5678" — never a URL.
     * (Figma URLs display it with a dash; convert to the colon form used by
     * the Figma API. Decided 2026-07-06, audit Q6.)
     */
    figmaNodeId?: string;
    /**
     * Optional custom render function for compound components
     * (Tabs, Accordion, Dialog, etc.) that cannot be demonstrated
     * by rendering each variant in isolation.
     * When present, VariantShowcase calls this instead of the variant loop.
     */
    render?: () => ReactNode;
    /**
     * Copy-paste JSX usage snippets. Agents implementing the component in a
     * product copy from examples first — keep them minimal, idiomatic and
     * token-compliant. Shown on the component's docs page.
     */
    examples?: string[];
    accessibility: {
        wcag: string;
        wcagStatus: "pass" | "fail" | "pending";
        contrastRatios?: {
            [variant: string]: {
                light: number;
                dark: number;
            };
        };
        /**
         * Machine-verifiable contrast declarations. Each pair names the fg/bg
         * TOKENS (e.g. "--text-default-primary"; bg may be a gradient
         * token — every stop is then checked independently) and the WCAG
         * threshold (4.5 for normal text, 3 for large text / UI components).
         * CI resolves the tokens from theme.css in both modes and computes the
         * real ratios (scripts/check-contrast.ts) — declared numbers in
         * `contrastRatios`/`notes` are cross-checked against computed values.
         */
        contrastPairs?: Array<{
            /** Human id; when it matches a `contrastRatios` key, values are cross-checked */
            id: string;
            /** Foreground (text/icon/ring) token name */
            fg: string;
            /** Background token name, or a gradient token */
            bg: string;
            /** Minimum WCAG ratio: 4.5 (normal text) or 3 (large text / UI) */
            minRatio: number;
        }>;
        notes: string;
    };
    variants: string[];
    sizes: string[];
    props: Record<string, string>;
    /**
     * Groups several distinct, independently-exported components onto ONE
     * docs-site page with a Tabs switcher between them (fidelity re-pass #2
     * point 9 — e.g. Navbar/NavbarItem/NavbarSubmenu/ProductSwitcher). Purely
     * a docs-PRESENTATION concern: the components themselves stay separate
     * files/exports; only `docs/app/components/[name]/page.tsx` reads this to
     * decide whether to render a single body or a `Tabs` of every entry
     * sharing the same `id`. Every member of a group must use the same `id`;
     * `label`/`tabLabel` may differ per member (used for that member's own
     * `TabsTrigger` text and — for the entry whose slug equals `id` — the
     * page's canonical/landing tab).
     */
    docsGroup?: {
        /** Shared identifier for the whole group — must match across all members. */
        id: string;
        /** Group-level label, currently unused by the generator but kept for
         * forward compatibility (e.g. a future shared group heading). */
        label: string;
        /** This member's own `TabsTrigger` text (e.g. "Navbar Item"). */
        tabLabel: string;
    };
}

declare const buttonVariants: (props?: ({
    variant?: "default" | "destructive" | "ia" | "highlight" | null | undefined;
    priority?: "primary" | "secondary" | "tertiary" | null | undefined;
    size?: "sm" | "md" | null | undefined;
} & class_variance_authority_types.ClassProp) | undefined) => string;
interface ButtonProps extends react.ComponentPropsWithoutRef<"button">, VariantProps<typeof buttonVariants> {
    /** Radix asChild — merges Button styles onto the child element (e.g. <a>, <Link>). Gradient text (ia/highlight) is not applied automatically; apply text-gradient-* on your child if needed. */
    asChild?: boolean;
    /** Icon before the label (ignored when asChild is true — Slot cannot inject wrapper elements) */
    startIcon?: react.ReactNode;
    /** Icon after the label (ignored when asChild is true — Slot cannot inject wrapper elements) */
    endIcon?: react.ReactNode;
    /** Loading state — disables the control and sets aria-busy (spinner is only rendered when asChild is false) */
    loading?: boolean;
    /** Takes the full width of the parent container */
    fullWidth?: boolean;
}
declare const Button: react.ForwardRefExoticComponent<ButtonProps & react.RefAttributes<HTMLButtonElement>>;

declare const ButtonMeta: ComponentMeta;

declare const Dialog: react.FC<Omit<react.ComponentProps<typeof DialogPrimitive.Root>, "modal">>;
declare const DialogTrigger: react.ForwardRefExoticComponent<DialogPrimitive.DialogTriggerProps & react.RefAttributes<HTMLButtonElement>>;
declare const DialogClose: react.ForwardRefExoticComponent<DialogPrimitive.DialogCloseProps & react.RefAttributes<HTMLButtonElement>>;
declare const dialogContentVariants: (props?: ({
    size?: "sm" | "md" | "lg" | null | undefined;
} & class_variance_authority_types.ClassProp) | undefined) => string;
interface DialogContentProps extends react.ComponentPropsWithoutRef<typeof DialogPrimitive.Content>, VariantProps<typeof dialogContentVariants> {
    /**
     * Hides the built-in corner close icon. Mirrors Figma's own `hasClose`
     * toggle (Dialog.rfc.md §3.1) — visible by default, conforming to Figma
     * over openaev's inverted wrapper convention (arbitration #5).
     */
    hideCloseButton?: boolean;
}
declare const DialogContent: react.ForwardRefExoticComponent<DialogContentProps & react.RefAttributes<HTMLDivElement>>;
declare const DialogTitle: react.ForwardRefExoticComponent<Omit<DialogPrimitive.DialogTitleProps & react.RefAttributes<HTMLHeadingElement>, "ref"> & react.RefAttributes<HTMLHeadingElement>>;
declare const DialogDescription: react.ForwardRefExoticComponent<Omit<DialogPrimitive.DialogDescriptionProps & react.RefAttributes<HTMLParagraphElement>, "ref"> & react.RefAttributes<HTMLParagraphElement>>;
declare const DialogBody: react.ForwardRefExoticComponent<Omit<react.DetailedHTMLProps<react.HTMLAttributes<HTMLDivElement>, HTMLDivElement>, "ref"> & react.RefAttributes<HTMLDivElement>>;
declare const DialogFooter: react.ForwardRefExoticComponent<Omit<react.DetailedHTMLProps<react.HTMLAttributes<HTMLDivElement>, HTMLDivElement>, "ref"> & react.RefAttributes<HTMLDivElement>>;

declare const DialogMeta: ComponentMeta;

declare function AssetGroupsIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function AttackPatternIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function BinocularIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function CaseIncidentIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function CaseRfiIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function CaseRftIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function CityIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function CourseOfActionIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function FiligranIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function FireIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function GlobeLineIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function InfrastructureIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function InjectorsIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function IntrusionSetIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function MalwareIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function OpenaevIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function OpenctiIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function OpengrcIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function RelationshipIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function SecurityPlatformsIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function SlackIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function TargetIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function ThreatActorIndividualIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function XtmhubIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare function XtmoneIcon(props: SVGProps<SVGSVGElement>): react.JSX.Element;
declare const iconRegistry: {
    readonly activity: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "alarm-clock": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "align-justify": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "align-left": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly anchor: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "arrow-down": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "arrow-down-left": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "arrow-left": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "arrow-right": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "arrow-right-to-line": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "arrow-up": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "arrow-up-down": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "arrow-up-right": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly award: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "badge-check": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly bell: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "bell-off": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "bell-plus": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly bolt: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly book: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "book-text": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly bookmark: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "bookmark-check": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly bot: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly box: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "brick-wall": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly briefcase: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "briefcase-business": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly bug: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "building-2": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly calendar: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "calendar-days": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "case-sensitive": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "chart-bar": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "chart-column": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly check: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "chess-knight": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "chess-queen": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "chevron-down": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "chevron-left": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "chevron-right": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "chevron-up": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "chevrons-down-up": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "chevrons-up-down": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly circle: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "circle-alert": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "circle-arrow-down": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "circle-check": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "circle-check-big": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "circle-play": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "circle-plus": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "circle-question-mark": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "circle-user": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "circle-x": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly clapperboard: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "clipboard-list": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly clock: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly cloud: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "cloud-download": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "cloud-upload": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "columns-3": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly compass: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly construction: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly database: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "diamond-plus": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly download: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "drafting-compass": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly earth: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly ellipsis: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "ellipsis-vertical": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "external-link": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly eye: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "eye-off": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly file: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "file-input": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "file-search": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "file-text": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "file-up": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly files: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly flag: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "flask-conical": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly focus: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "folder-open": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly forklift: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly gem: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "graduation-cap": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "grid-3x2": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly grip: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "grip-horizontal": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "grip-vertical": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly hexagon: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly house: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly image: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "image-play": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly inbox: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly info: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly key: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "key-round": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "key-square": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly landmark: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly languages: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "laptop-minimal": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly layers: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "layers-2": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "layout-dashboard": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "layout-grid": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "layout-panel-left": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "link-2": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly list: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "list-checks": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "list-tree": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "list-x": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly locate: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "locate-fixed": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly lock: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "log-out": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly mail: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly map: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "map-pin": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "maximize-2": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly megaphone: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "message-square-more": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "message-square-text": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "messages-square": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly minus: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly monitor: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "monitor-down": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "monitor-smartphone": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly moon: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly network: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly newspaper: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly orbit: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "package-check": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "panel-left-close": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "panel-left-open": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "panel-top": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly paperclip: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly pencil: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly play: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly plus: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly radar: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "refresh-cw": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly repeat: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly rocket: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "rotate-3d": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "rotate-ccw": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly route: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly router: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "satellite-dish": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly save: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly search: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly server: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly settings: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "share-2": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly shield: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "shield-check": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "shopping-basket": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly signpost: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "skip-back": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "skip-forward": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "sliders-horizontal": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "sliders-vertical": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly speaker: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "square-chart-gantt": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "square-terminal": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly star: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "sticky-note": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "sticky-note-plus": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly sun: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "table-properties": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly tag: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly terminal: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly text: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "text-search": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly thermometer: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "thumbs-down": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "thumbs-up": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "trash-2": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "triangle-alert": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly type: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly unlink: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly upload: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly user: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "user-lock": react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly users: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly waypoints: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly wrench: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly x: react.ForwardRefExoticComponent<Omit<lucide_react.LucideProps, "ref"> & react.RefAttributes<SVGSVGElement>>;
    readonly "custom/asset-groups": typeof AssetGroupsIcon;
    readonly "custom/attack-pattern": typeof AttackPatternIcon;
    readonly "custom/binocular": typeof BinocularIcon;
    readonly "custom/case-incident": typeof CaseIncidentIcon;
    readonly "custom/case-rfi": typeof CaseRfiIcon;
    readonly "custom/case-rft": typeof CaseRftIcon;
    readonly "custom/city": typeof CityIcon;
    readonly "custom/course-of-action": typeof CourseOfActionIcon;
    readonly "custom/filigran": typeof FiligranIcon;
    readonly "custom/fire": typeof FireIcon;
    readonly "custom/globe-line": typeof GlobeLineIcon;
    readonly "custom/infrastructure": typeof InfrastructureIcon;
    readonly "custom/injectors": typeof InjectorsIcon;
    readonly "custom/intrusion-set": typeof IntrusionSetIcon;
    readonly "custom/malware": typeof MalwareIcon;
    readonly "custom/openaev": typeof OpenaevIcon;
    readonly "custom/opencti": typeof OpenctiIcon;
    readonly "custom/opengrc": typeof OpengrcIcon;
    readonly "custom/relationship": typeof RelationshipIcon;
    readonly "custom/security-platforms": typeof SecurityPlatformsIcon;
    readonly "custom/slack": typeof SlackIcon;
    readonly "custom/target": typeof TargetIcon;
    readonly "custom/threat-actor-individual": typeof ThreatActorIndividualIcon;
    readonly "custom/xtmhub": typeof XtmhubIcon;
    readonly "custom/xtmone": typeof XtmoneIcon;
};
/** The official icon vocabulary of the design system. */
type IconName = keyof typeof iconRegistry;
declare const ICON_NAMES: IconName[];

/** Allowed rendered sizes (px) — the only sizes designed in Figma. */
declare const ICON_SIZES: readonly [16, 20, 24];
type IconSize = (typeof ICON_SIZES)[number];
/** Decorative gradient fills (AI / focus accents). */
type IconGradient = "ia" | "focus";
interface IconBaseProps {
    /** Icon identifier from the generated design-system vocabulary (ICON_NAMES). */
    name: IconName;
    /** Rendered size in px (default 24). */
    size?: IconSize;
    /**
     * Decorative gradient fill. Gradient icons are visual accents: their
     * contrast against arbitrary backgrounds is NOT guaranteed (WCAG 1.4.11),
     * so never rely on a gradient icon alone to convey meaning.
     */
    gradient?: IconGradient;
    className?: string;
}
interface DecorativeIconProps extends IconBaseProps {
    /** Decorative is the default: hidden from assistive technologies. */
    "aria-label"?: never;
    "aria-hidden"?: true;
}
interface SemanticIconProps extends IconBaseProps {
    /** Standalone semantic icon: accessible name, rendered with role="img". */
    "aria-label": string;
    "aria-hidden"?: false;
}
type IconProps = DecorativeIconProps | SemanticIconProps;
/**
 * Single entry point for icon rendering: Lucide glyphs and Filigran custom
 * glyphs behind one string-name API. Color is inherited from the CSS text
 * color (currentColor) — style it with text-* token classes on the icon or
 * a parent, never with hardcoded values.
 */
declare function Icon(props: IconProps): react.JSX.Element;

declare const IconMeta: ComponentMeta;

declare const iconButtonVariants: (props?: ({
    variant?: "default" | "destructive" | "ia" | null | undefined;
    priority?: "primary" | "secondary" | "tertiary" | null | undefined;
    size?: "sm" | "md" | null | undefined;
} & class_variance_authority_types.ClassProp) | undefined) => string;
interface IconButtonProps extends Omit<react.ComponentPropsWithoutRef<"button">, "children" | "aria-label">, VariantProps<typeof iconButtonVariants> {
    /** The icon element to render — must be aria-hidden (the button itself carries the accessible label) */
    icon: react.ReactNode;
    /** Required accessible label — icon-only buttons have no visible text */
    "aria-label": string;
    /** Active state (e.g. toggle is on) */
    active?: boolean;
}
declare const IconButton: react.ForwardRefExoticComponent<IconButtonProps & react.RefAttributes<HTMLButtonElement>>;

declare const IconButtonMeta: ComponentMeta;

declare const inputVariants: (props?: ({
    error?: boolean | null | undefined;
    hasStartIcon?: boolean | null | undefined;
    hasEndSlot?: boolean | null | undefined;
} & class_variance_authority_types.ClassProp) | undefined) => string;
interface InputProps extends Omit<react.InputHTMLAttributes<HTMLInputElement>, "size" | "type"> {
    /** Input type — text field variants only (multiline → Textarea, file → separate) */
    type?: "text" | "password" | "number" | "email";
    /** Controlled value */
    value?: string;
    /** Uncontrolled default value */
    defaultValue?: string;
    /** Change handler (native event passthrough) */
    onChange?: react.ChangeEventHandler<HTMLInputElement>;
    /** Visible label above the field (Figma `hasLabel`). Omit → aria-label or
        aria-labelledby required */
    label?: string;
    /** Marks the field required — renders `*` and sets aria-required (Figma `required`).
        The `*` is label-row anatomy (Figma, RULE-01) — only renders when `label` is
        also set; `aria-required` on the input itself is unaffected either way
        (review: PR #54 round 2) */
    required?: boolean;
    /** Info tooltip trigger shown next to the label (Figma `infosIcon`) */
    infoTooltip?: react.ReactNode;
    /** Placeholder shown when empty (Figma `placeholder`) */
    placeholder?: string;
    /** Helper / description text below the field (Figma `helperText` + `helperContent`) */
    helperText?: react.ReactNode;
    /** Error state — message replaces helperText and switches error tokens.
        String only (arbitration #2 — no boolean; RULE-02) */
    error?: string;
    /** Success state — shows the check-circle indicator (Figma `Success`) */
    success?: boolean;
    /** Character-limit counter (Figma `caraLimit`). When set, shows "N/max" in the
        helper row (RULE-09) and enforces maxLength */
    maxLength?: number;
    /** Single leading decorative icon (arbitration #3 — no Figma precedent, added
        by human arbitration, symmetric to endIcon's decorative branch; RULE-12).
        Always aria-hidden, no interactive variant */
    startIcon?: react.ReactNode;
    /** Single trailing icon slot (Figma `hasEndIcon`). Masked by the state icon in
        error/success (RULE-06). `iconButton` = interactive (password toggle, askAI,
        attach, clear) and composes the FDS <IconButton> (`label` is its required
        accessible name); `icon` = decorative only, always aria-hidden — no `label`
        field, it would never be read (review: PR #54 round 1) */
    endIcon?: {
        type: "iconButton";
        icon: react.ReactNode;
        onClick: react.MouseEventHandler<HTMLButtonElement>;
        label: string;
    } | {
        type: "icon";
        icon: react.ReactNode;
    };
    /** Disables the field (Figma `Disabled`) */
    disabled?: boolean;
    className?: string;
}
declare const Input: react.ForwardRefExoticComponent<InputProps & react.RefAttributes<HTMLInputElement>>;

declare const InputMeta: ComponentMeta;

interface NavbarProps extends react.ComponentPropsWithoutRef<"nav"> {
    /**
     * Accessible landmark name (required, not defaulted — Navbar.rfc.md §5.3).
     * Every product renders more than one `<nav>` on the page, so a
     * distinguishing name is mandatory, and the exact wording is
     * product/locale-specific.
     */
    "aria-label": string;
    /** Controlled collapsed state. Omit for uncontrolled (see `defaultCollapsed`). */
    collapsed?: boolean;
    /** Initial collapsed state for uncontrolled usage. Default: `false`. */
    defaultCollapsed?: boolean;
    /** Notifies on user-driven collapse/expand (footer toggle click). */
    onCollapsedChange?: (collapsed: boolean) => void;
    /** Header slot — typically a `<ProductSwitcher>` (§3.2 `productLogo`). */
    header?: react.ReactNode;
    /** Main content — `NavbarItem` / `NavbarSubmenu` / `NavbarSeparator` children. */
    children: react.ReactNode;
    /**
     * Footer slot rendered above the built-in collapse toggle (e.g. a
     * help/settings `NavbarItem`). The toggle itself is always rendered by
     * `Navbar` and is not user-supplied — its icon/label are state-driven
     * (§4.2).
     */
    footer?: react.ReactNode;
}
/**
 * The primary structural navigation container (Navbar.rfc.md) — a thin
 * composition root, not a monolith: it owns the collapsed/expanded state,
 * the `<nav>` landmark, the width transition, and the footer collapse
 * toggle, sharing `collapsed` with its descendants (`NavbarItem`,
 * `NavbarSubmenu`, `ProductSwitcher`) via `NavbarContext` rather than
 * rendering their content itself (§1/§4.3).
 */
declare const Navbar: react.ForwardRefExoticComponent<NavbarProps & react.RefAttributes<HTMLElement>>;
interface NavbarSeparatorProps extends react.ComponentPropsWithoutRef<"hr"> {
}
/**
 * Divider between item groups (§3.2, §4.3 — 4 separators split ~19 items
 * into 5 groups in the Figma frame). Internal to `Navbar`, no dedicated RFC
 * section or ROADMAP entry per @sandy's explicit arbitration. A native
 * `<hr>` already computes an implicit `role="separator"` — no explicit
 * `role` attribute needed (and `eslint-plugin-jsx-a11y`'s
 * `no-redundant-roles` rule disallows adding one).
 */
declare const NavbarSeparator: react.ForwardRefExoticComponent<NavbarSeparatorProps & react.RefAttributes<HTMLHRElement>>;

declare const NavbarMeta: ComponentMeta;

/**
 * Shared state a Navbar exposes to its descendants (NavbarItem,
 * NavbarSubmenu, ProductSwitcher). Kept in its own leaf module (no
 * dependents) rather than in Navbar.tsx / the navbar barrel: Navbar.tsx
 * renders NavbarItem for its built-in collapse-toggle row, and NavbarItem
 * needs to read this context — routing both through `navbar/index.ts` would
 * create a circular barrel import (navbar ↔ navbar-item). Consumers in other
 * component folders should import from this file directly
 * (`../navbar/NavbarContext`), not through the navbar barrel.
 *
 * "use client" is required here: `React.createContext()` below runs at
 * module scope, and any Server Component that transitively imports this
 * file (e.g. the docs site's registry, read for `.meta` only) would
 * otherwise crash with "createContext only works in Client Components" —
 * this is the exact, sole root cause of the original docs-build regression.
 * The registry itself (docs/lib/component-registry.ts) must NOT be marked
 * "use client" to fix this — that band-aid breaks Server Components' plain
 * data reads of the registry instead (see its file header for details).
 * The boundary belongs on the leaf component that actually needs it.
 */
interface NavbarContextValue {
    /** True when the Navbar is rendering in its narrow, icon-only state. */
    collapsed: boolean;
}
declare const NavbarContext: react.Context<NavbarContextValue>;
/**
 * Reads the ambient collapsed state. Defaults to `false` (expanded) when
 * used outside a Navbar — e.g. in isolated docs/demo rendering of NavbarItem
 * or NavbarSubmenuItem — so those components remain usable standalone.
 */
declare function useNavbarCollapsed(): boolean;

interface NavbarItemProps extends react.ComponentPropsWithoutRef<"button"> {
    /**
     * Radix asChild — merges NavbarItem styles onto the child element
     * (typically a router `Link`), same pattern as `Button`. `icon`/`showIcon`/
     * `chevron` are ignored in this mode — Slot cannot inject wrapper elements
     * around/inside an arbitrary child, so compose them into your own child
     * if you need them alongside a custom link.
     */
    asChild?: boolean;
    /**
     * Leading icon. Hidden entirely (no fallback/initials) when `showIcon` is
     * `false` — the label then takes the full row width, no layout shift.
     */
    icon?: react.ReactNode;
    /**
     * Whether the leading icon renders. Default `true`. Mirrors opencti's real
     * `submenu_show_icons` preference (RFC §2.3). Forced to effectively `true`
     * while the ancestor Navbar is collapsed, so the row is never rendered
     * fully content-less (RFC §3.6).
     */
    showIcon?: boolean;
    /**
     * Shows the trailing submenu-indicator chevron, fully independent of
     * `icon`/`showIcon` (Figma-confirmed, RFC §3.2). Set by `NavbarSubmenu`
     * when composing `NavbarItem` as its trigger row — rarely set directly.
     * Rotates automatically via `data-state` if an ancestor Radix trigger
     * (e.g. Accordion.Trigger) merges it onto this row through `asChild`.
     */
    chevron?: boolean;
    /** Row label. */
    children: react.ReactNode;
    /**
     * Accessible label duplicated into the collapsed-mode Tooltip's content
     * (§5.3). Only needed with `asChild`: in that mode `children` is the
     * slotted element itself (e.g. a `<Link>` composing its own icon/label
     * spans), not plain label text, so reusing it verbatim inside
     * `TooltipContent` would nest a focusable element inside the tooltip
     * bubble — invalid per the WAI-ARIA tooltip pattern (non-interactive
     * content only) as well as visually broken. Ignored when `asChild` is
     * `false`, where `children` already is the plain label and needs no
     * duplicate.
     *
     * Prop-contract violation if omitted while `asChild` is set on a
     * collapsed, non-`chevron` row (review comment, PR #43): rather than
     * mounting an empty tooltip bubble, the Tooltip is skipped entirely and a
     * dev-only `console.warn` fires (never throws) — same precedent as
     * `ProductSwitcher`'s `href`/`to` (AGENTS.md "Prop contract violations").
     */
    tooltipLabel?: react.ReactNode;
}
/**
 * The single interactive row used throughout `Navbar`: a plain navigation
 * link (leaf item), a `NavbarSubmenu` trigger row, and the `Navbar` footer's
 * built-in collapse toggle all render this same component — only icon,
 * chevron, and label content differ per usage.
 *
 * "Selected" styling is derived from the native `aria-current="page"`
 * attribute rather than a redundant boolean prop (NavbarItem.rfc.md §4.2).
 * While the ancestor `Navbar` is collapsed, the row hides its label (kept in
 * the DOM as visually-hidden text, so the accessible name never depends on
 * it disappearing) and wraps itself in the library's `Tooltip`.
 */
declare const NavbarItem: react.ForwardRefExoticComponent<NavbarItemProps & react.RefAttributes<HTMLButtonElement>>;

declare const NavbarItemMeta: ComponentMeta;

interface NavbarSubmenuItemProps extends react.ComponentPropsWithoutRef<"a"> {
    /** Radix asChild — same router-Link polymorphism as NavbarItem/Button. */
    asChild?: boolean;
    /**
     * Same verified opencti mechanism as NavbarItem (`submenu_show_icons`) —
     * icon omitted with no fallback when false, label unaffected. Submenu
     * labels never hide, regardless of ancestor collapse state (NavbarItem's
     * *own* row is the only one that hides its label when collapsed) —
     * NavbarSubmenuItem.rfc.md §2.3/§4.2. Default: `true`.
     */
    showIcon?: boolean;
    icon?: react.ReactNode;
    children: react.ReactNode;
}
/**
 * A single row inside a `NavbarSubmenu`. Documented and shipped only as part
 * of `NavbarSubmenu`'s compound API (mirrors the `Tabs`/`TabsTrigger`
 * precedent) — not intended for standalone use (NavbarSubmenu.rfc.md §4.2).
 */
declare const NavbarSubmenuItem: react.ForwardRefExoticComponent<NavbarSubmenuItemProps & react.RefAttributes<HTMLAnchorElement>>;
interface NavbarSubmenuProps {
    /** Accessible name for the trigger row (rendered via NavbarItem). */
    label: react.ReactNode;
    /** Optional leading icon on the trigger row (passed through to NavbarItem). */
    icon?: react.ReactNode;
    /** Controlled open state. Omit for uncontrolled. */
    open?: boolean;
    defaultOpen?: boolean;
    onOpenChange?: (open: boolean) => void;
    /**
     * Destination for the trigger row, applied ONLY while the ancestor Navbar
     * is collapsed (flyout mode) — mirrors openaev's real `MenuItemGroup`
     * pattern (`component={Link} to={item.path}` when collapsed, `onClick`
     * toggle when expanded; confirmed against opencti's own equivalent
     * `handleParentClick`/`handleGoToPage`, `LeftBarItem.tsx`/`LeftBar.jsx`).
     * While collapsed, the trigger becomes a real, directly-navigable link
     * (`asChild`/Slot onto a plain `<a>`, same mechanism as
     * NavbarItem/NavbarSubmenuItem) IN ADDITION to still opening the flyout on
     * hover/focus — clicking or pressing Enter navigates without also
     * toggling the flyout open/closed (§4.3/§5.2). Ignored entirely in
     * expanded mode: the trigger stays a pure accordion toggle there, exactly
     * as before this feature — a submenu *group* heading is not itself a
     * distinct page when there's room to show its children inline (openaev's
     * own onClick-toggles-only expanded behavior). Mutually exclusive with
     * `to`: if both are set, `href` takes priority and `to` is silently
     * ignored at runtime, with a dev-only `console.warn` (never thrown) — same
     * precedent as `ProductSwitcher`'s `href`/`to` (AGENTS.md "Prop contract
     * violations"). Unlike `ProductSwitcher.href` (external, `target="_blank"`),
     * both props here render an identical plain same-tab anchor — no
     * external-link semantics apply to a submenu group, matching openaev's own
     * purely-internal `to={item.path}` usage; `href`/`to` exist as two names
     * for the same behavior so consumers can match whichever their own
     * router's convention prefers (e.g. Next.js `Link href` vs React Router
     * `Link to`).
     */
    href?: string;
    /**
     * Alias for `href` — see its doc for full behavior, priority, and the
     * dev-warning when both are set.
     */
    to?: string;
    /** `NavbarSubmenuItem` children. */
    children: react.ReactNode;
}
/**
 * Dual-mode submenu: renders as an inline WAI-ARIA disclosure (Radix
 * `Accordion`) while the ancestor `Navbar` is expanded, and as a `role="menu"`
 * flyout (Radix `DropdownMenu`, opened by click, keyboard, or hover) while
 * it's collapsed. The trigger in both modes IS a `NavbarItem` (composed via
 * `asChild`, not reimplemented) — see NavbarItem.rfc.md §4.3.
 *
 * Mode is always derived from `NavbarContext`; there is deliberately no prop
 * to force one mode manually (NavbarSubmenu.rfc.md §4.2) — a submenu can
 * never disagree with its own Navbar's collapse state.
 *
 * When `href`/`to` is set, the collapsed-mode trigger additionally becomes a
 * real, directly-navigable link (mirrors openaev's `MenuItemGroup` pattern,
 * §4.3/§5.2) — hover/focus still opens the flyout independently, and
 * clicking or pressing Enter navigates without also toggling it.
 * Expanded-mode behavior is entirely unaffected: `href`/`to` are ignored
 * there, a pure accordion toggle as before this feature.
 */
declare function NavbarSubmenu({ label, icon, open, defaultOpen, onOpenChange, href, to, children, }: NavbarSubmenuProps): react.JSX.Element;
declare namespace NavbarSubmenu {
    var displayName: string;
}

declare const NavbarSubmenuMeta: ComponentMeta;

interface ProductSwitcherOption {
    id: string;
    /**
     * Accessible name for this link. Never rendered as visible text — `logo`
     * is the item's sole visible content (RFC §3.2/§4.1). Used verbatim as the
     * link's accessible name, and as the base for the visually-hidden
     * "(opens in a new tab)" suffix when `href` is set (RFC §5.3).
     */
    label: string;
    /** Product wordmark/logo — the item's sole visible content. Rendered
     * `aria-hidden` (the accessible name comes from `label`, not from
     * whatever alt text a consumer's logo asset may or may not carry). */
    logo: react.ReactNode;
    /** Optional supplementary tooltip (e.g. connection status). Copy is the
     * consuming product's responsibility (RFC §4.1/§6.3) — not populated here. */
    tooltip?: string;
    /**
     * External destination. Renders `target="_blank"` + `rel="noopener
     * noreferrer"`, a visible "opens in a new tab" icon (`aria-hidden`), and
     * appends the AT-only "(opens in a new tab)" suffix to the accessible
     * name — corrects a real WCAG gap in opencti's current icon-only
     * implementation (RFC §2.3/§2.4/§5.3). Mutually exclusive with `to`: if
     * both are set, `href` takes priority and `to` is silently ignored at
     * runtime, but a dev-only `console.warn` fires to flag the contract
     * violation (see `AGENTS.md` "Prop contract violations", never thrown,
     * silent in production).
     */
    href?: string;
    /**
     * Internal destination. Mutually exclusive with `href` — see `href`'s doc
     * for the effective priority and dev-warning behavior when both are set.
     * Real precedent: opencti's XTM Hub entry links internally to a
     * connection/settings page when not yet connected (RFC §2.3). Rendered as
     * a same-tab `<a href>` — the design system has no dependency on any
     * specific router, so true zero-reload client-side navigation depends on
     * the consuming app's router intercepting anchor clicks (documented gap,
     * not a silent guess — see Final Summary).
     */
    to?: string;
}
interface ProductSwitcherProps extends react.ComponentPropsWithoutRef<"button"> {
    /** Current product's logo, always-visible trigger content in both
     * `Navbar` collapse states (RFC §3.6) — the trigger itself never becomes
     * non-interactive. Rendered `aria-hidden`; `label` carries the a11y name. */
    logo: react.ReactNode;
    /** Accessible name for the trigger button. Not rendered as visible text
     * (RFC §4.1) — deliberately distinct from "Collapse"/"Expand" so it never
     * clashes with `Navbar`'s own, unrelated collapse toggle (RFC §5.3). */
    label: string;
    /** Other Filigran products/destinations. */
    options: ProductSwitcherOption[];
}
/**
 * The `Navbar` header control exposing links to sibling Filigran products —
 * a static menu of destination links (most external, at least one
 * conditionally internal per real opencti usage), never a stateful
 * "current product" selector (no selected/active option, RFC §1/§2.3). Built
 * on `@radix-ui/react-dropdown-menu` since this is an actions/links menu, not
 * a form control — default `modal` behavior is kept (unlike `NavbarSubmenu`,
 * this trigger opens only on click/keyboard, never on hover, so there is no
 * hover/click race to guard against, RFC §5.2).
 */
declare const ProductSwitcher: react.ForwardRefExoticComponent<ProductSwitcherProps & react.RefAttributes<HTMLButtonElement>>;

declare const ProductSwitcherMeta: ComponentMeta;

declare const searchFieldVariants: (props?: ({
    size?: "sm" | "md" | null | undefined;
} & class_variance_authority_types.ClassProp) | undefined) => string;
interface SearchFieldProps extends Omit<react.ComponentPropsWithoutRef<"input">, "size" | "type" | "onSubmit">, VariantProps<typeof searchFieldVariants> {
    /** Controlled value */
    value?: string;
    /** Default value (uncontrolled) */
    defaultValue?: string;
    /** Callback fired on every keystroke */
    onChange?: (event: react.ChangeEvent<HTMLInputElement>) => void;
    /** Callback fired when the user presses Enter */
    onSubmit?: (value: string) => void;
    /** Callback fired when the clear action is triggered */
    onClear?: () => void;
    /** Trailing action slot (Figma: searchOption) — typically IconButton(s) */
    searchOption?: react.ReactNode;
    /** Expand to fill container width */
    fullWidth?: boolean;
    /** Placeholder text */
    placeholder?: string;
    /** Disabled state */
    disabled?: boolean;
    /** Additional CSS classes */
    className?: string;
}
declare const SearchField: react.ForwardRefExoticComponent<SearchFieldProps & react.RefAttributes<HTMLInputElement>>;

declare const SearchFieldMeta: ComponentMeta;

interface SwitchProps extends Omit<react.ComponentPropsWithoutRef<typeof SwitchPrimitive.Root>, "children"> {
    /**
     * Label rendered next to the control (Figma: label integrated into the
     * component — RFC arbitration Q4, option a). The whole label is part of
     * the native <label>/<button> association, so clicking the text toggles
     * the switch. When omitted, the consumer MUST supply an accessible name
     * via aria-label or aria-labelledby directly on Switch.
     */
    label?: react.ReactNode;
    /** Additional classes for the outer <label> wrapper. Only applies when `label` is set — has no effect otherwise. */
    wrapperClassName?: string;
}
/**
 * Boolean toggle built on @radix-ui/react-switch. Radix supplies the
 * role="switch"/aria-checked semantics, controlled/uncontrolled value
 * (checked/defaultChecked + onCheckedChange), and native keyboard behavior
 * (Space/Enter, inherited for free from the real <button> element Root
 * renders as). Root's own `asChild` is inherited from Radix (RFC-arbitrated,
 * no current product usage — see Switch.rfc.md §4.3); Thumb's structure is
 * entirely internal/fixed and does not accept `asChild` or any other prop
 * — SwitchProps only extends Root's props.
 *
 * Size and color are intentionally NOT configurable in this version: the
 * Figma spec defines exactly one size and one color for Switch (RFC
 * arbitration Q1) — see Switch.rfc.md for the product evidence behind
 * that exclusion.
 */
declare const Switch: react.ForwardRefExoticComponent<SwitchProps & react.RefAttributes<HTMLButtonElement>>;

declare const SwitchMeta: ComponentMeta;

interface TabsProps extends react.ComponentPropsWithoutRef<typeof TabsPrimitive.Root> {
}
/**
 * Defaults `activationMode` to "manual" (arrow keys move focus only; the
 * focused tab activates on Enter/Space or click) instead of Radix's own
 * "automatic" default (arrow-key focus alone activates the tab). Aligns with
 * this design system's actual shipped behavior (MUI Tabs, OpenCTI) — still
 * overridable via an explicit `activationMode` prop for call sites that want
 * automatic activation.
 */
declare const Tabs: react.ForwardRefExoticComponent<TabsProps & react.RefAttributes<HTMLDivElement>>;
interface TabsListProps extends react.ComponentPropsWithoutRef<typeof TabsPrimitive.List> {
    /**
     * Optional content aligned to the right of the tab bar (secondary actions,
     * status text…). Rendered as a DOM **sibling** of the tablist, never as a
     * child — see the ARIA note in Tabs.meta.ts (`aria-required-children`).
     */
    actions?: react.ReactNode;
}
declare const TabsList: react.ForwardRefExoticComponent<TabsListProps & react.RefAttributes<HTMLDivElement>>;
/**
 * Semantic color variants for TabsTrigger.badge. Purely decorative — never
 * implies interactivity or clickability (see TabsTriggerProps.badge).
 */
type TabsTriggerBadgeColor = "brand" | "error";
interface TabsTriggerProps extends react.ComponentPropsWithoutRef<typeof TabsPrimitive.Trigger> {
    /** Optional leading icon — pass a 16×16 icon (rendered inside a 16×16 container) */
    icon?: react.ReactNode;
    /** Optional badge count displayed next to the label */
    badge?: number | string;
    /**
     * Semantic color variant for the badge. Purely decorative — does not imply
     * interactivity or clickability; the badge never renders a click handler
     * regardless of color. Default: "brand" (existing tinted brand color,
     * unchanged). Ignored when asChild is true or badge is undefined.
     */
    badgeColor?: TabsTriggerBadgeColor;
    /**
     * Caps a numeric badge: when `badge` is a number greater than `badgeMax`,
     * renders `${badgeMax}+` instead of the raw value (e.g. badge={150}
     * badgeMax={99} → "99+"). Ignored for string badges and uncapped by
     * default (no default value) — non-breaking for any existing numeric
     * badge usage. A negative or non-finite `badgeMax` (e.g. -1, NaN,
     * Infinity) is treated as uncapped rather than applied literally, so it
     * can never render a nonsensical value like "-1+". Ignored when asChild
     * is true.
     */
    badgeMax?: number;
    /**
     * Hides the badge without any other change to the trigger — equivalent to
     * omitting `badge`. Provided as an explicit prop for ergonomic conditional
     * usage (e.g. badgeInvisible={count === 0}) and for parity with MUI
     * Badge's `invisible` prop. The badge element is not rendered at all when
     * true (no CSS-only hiding, no residual DOM/accessibility-tree node).
     * Ignored when asChild is true.
     */
    badgeInvisible?: boolean;
    /**
     * Merge trigger props onto a single child element instead of rendering a
     * <button>. Use with React Router <Link> for route-based tabs:
     *   <TabsTrigger value="overview" asChild>
     *     <Link to="/overview">Overview</Link>
     *   </TabsTrigger>
     * When asChild is true, icon and badge props (including badgeColor,
     * badgeMax, badgeInvisible) are ignored — compose them inside the child
     * element manually if needed. This is also the supported way to build a
     * clickable badge/chip (e.g. OpenCTI's EEChip pattern): TabsTrigger's own
     * badge stays purely decorative and is never clickable.
     */
    asChild?: boolean;
}
declare const TabsTrigger: react.ForwardRefExoticComponent<TabsTriggerProps & react.RefAttributes<HTMLButtonElement>>;
declare const TabsContent: react.ForwardRefExoticComponent<Omit<TabsPrimitive.TabsContentProps & react.RefAttributes<HTMLDivElement>, "ref"> & react.RefAttributes<HTMLDivElement>>;

declare const TabsMeta: ComponentMeta;

declare const textVariants: (props?: ({
    variant?: "title-2xl" | "title-jumbo" | "title-xl" | "title-lg" | "title-md" | "title-sm" | "title-xs" | "content-base" | "content-base-bold" | "content-base-medium" | "content-base-link" | "content-compact" | "content-compact-bold" | "content-compact-medium" | "content-compact-link" | "content-caption" | "content-highlight" | "content-button" | "content-code" | null | undefined;
} & class_variance_authority_types.ClassProp) | undefined) => string;
type TextVariant = NonNullable<VariantProps<typeof textVariants>["variant"]>;
/**
 * Native HTML element rendered by default when `as` is omitted, per variant.
 * Deliberately NOT derived from `variant` at runtime — `as` and `variant`
 * never merge (RFC "already-settled arbitration"). Full per-row rationale:
 * Text.rfc.md §4.1.
 */
declare const TEXT_DEFAULT_TAG: {
    readonly "title-2xl": "h1";
    readonly "title-xl": "h2";
    readonly "title-lg": "h3";
    readonly "title-md": "h4";
    readonly "title-sm": "h5";
    readonly "title-xs": "h6";
    readonly "title-jumbo": "p";
    readonly "content-base": "p";
    readonly "content-base-bold": "p";
    readonly "content-base-medium": "p";
    readonly "content-base-link": "a";
    readonly "content-compact": "p";
    readonly "content-compact-bold": "p";
    readonly "content-compact-medium": "p";
    readonly "content-compact-link": "a";
    readonly "content-caption": "span";
    readonly "content-highlight": "p";
    readonly "content-button": "span";
    readonly "content-code": "code";
};
type PolymorphicRef<C extends react.ElementType> = react.ComponentPropsWithRef<C>["ref"];
interface TextOwnProps<C extends react.ElementType = react.ElementType> {
    /**
     * The semantic element to render — never affects the visual style, which
     * comes exclusively from `variant`. Defaults to a sensible native tag per
     * `variant` when omitted (see TEXT_DEFAULT_TAG).
     *
     * Accepts an intrinsic tag (`"h1"`, `"span"`, ...) or a custom component
     * (`React.ElementType`, settled RFC §4.2/§4.3 — real precedent: 5/494
     * openaev usages already hand-roll this via MUI's `component` prop). A
     * custom component supplied here is responsible for forwarding
     * `className` and `ref` onto its own rendered root itself — same
     * requirement Radix's own `asChild`/Slot pattern places on its child.
     * Text does not verify this at compile-time or runtime (2026-07-23 fix,
     * PR #44 review: the prior wording here said "native HTML element",
     * which undersold the real, deliberately-typed scope and could mislead
     * consumers about the forwarding contract).
     */
    as?: C;
    /**
     * The Named Style to apply (theme.css / process/TYPO-MAPPING.md) — the
     * visual choice. Required: no silent default (RFC §2.4/§4.2 — MUI's own
     * implicit `body1` default was found to cause ambiguity in 11% of real
     * usages).
     */
    variant: TextVariant;
    className?: string;
    children: react.ReactNode;
}
type TextProps<C extends react.ElementType> = TextOwnProps<C> & Omit<react.ComponentPropsWithoutRef<C>, keyof TextOwnProps<C>>;
type TextComponent = <V extends TextVariant, C extends react.ElementType = (typeof TEXT_DEFAULT_TAG)[V]>(props: TextProps<C> & {
    variant: V;
    ref?: PolymorphicRef<C>;
}) => react.ReactElement | null;
declare const Text: TextComponent & {
    displayName?: string;
};

declare const TextMeta: ComponentMeta;

declare const TooltipProvider: react.FC<TooltipPrimitive.TooltipProviderProps>;
declare const Tooltip: react.FC<TooltipPrimitive.TooltipProps>;
declare const TooltipTrigger: react.ForwardRefExoticComponent<TooltipPrimitive.TooltipTriggerProps & react.RefAttributes<HTMLButtonElement>>;
declare const TooltipContent: react.ForwardRefExoticComponent<Omit<TooltipPrimitive.TooltipContentProps & react.RefAttributes<HTMLDivElement>, "ref"> & react.RefAttributes<HTMLDivElement>>;

declare const TooltipMeta: ComponentMeta;

export { Button, ButtonMeta, type ButtonProps, type ComponentMeta, Dialog, DialogBody, DialogClose, DialogContent, type DialogContentProps, DialogDescription, DialogFooter, DialogMeta, DialogTitle, DialogTrigger, ICON_NAMES, ICON_SIZES, Icon, IconButton, IconButtonMeta, type IconButtonProps, type IconGradient, IconMeta, type IconName, type IconProps, type IconSize, Input, InputMeta, type InputProps, Navbar, NavbarContext, type NavbarContextValue, NavbarItem, NavbarItemMeta, type NavbarItemProps, NavbarMeta, type NavbarProps, NavbarSeparator, type NavbarSeparatorProps, NavbarSubmenu, NavbarSubmenuItem, type NavbarSubmenuItemProps, NavbarSubmenuMeta, type NavbarSubmenuProps, ProductSwitcher, ProductSwitcherMeta, type ProductSwitcherOption, type ProductSwitcherProps, SearchField, SearchFieldMeta, type SearchFieldProps, Switch, SwitchMeta, type SwitchProps, TEXT_DEFAULT_TAG, Tabs, TabsContent, TabsList, TabsMeta, TabsTrigger, type TabsTriggerProps, Text, TextMeta, type TextProps, type TextVariant, Tooltip, TooltipContent, TooltipMeta, TooltipProvider, TooltipTrigger, buttonVariants, iconButtonVariants, inputVariants, searchFieldVariants, useNavbarCollapsed };
