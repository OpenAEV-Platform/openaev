"use strict";
var __create = Object.create;
var __defProp = Object.defineProperty;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __getProtoOf = Object.getPrototypeOf;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __export = (target, all) => {
  for (var name in all)
    __defProp(target, name, { get: all[name], enumerable: true });
};
var __copyProps = (to, from, except, desc) => {
  if (from && typeof from === "object" || typeof from === "function") {
    for (let key of __getOwnPropNames(from))
      if (!__hasOwnProp.call(to, key) && key !== except)
        __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
  }
  return to;
};
var __toESM = (mod, isNodeMode, target) => (target = mod != null ? __create(__getProtoOf(mod)) : {}, __copyProps(
  // If the importer is in node compatibility mode or this is not an ESM
  // file that has been converted to a CommonJS file using a Babel-
  // compatible transform (i.e. "__esModule" has not been set), then set
  // "default" to the CommonJS "module.exports" for node compatibility.
  isNodeMode || !mod || !mod.__esModule ? __defProp(target, "default", { value: mod, enumerable: true }) : target,
  mod
));
var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);

// src/index.ts
var index_exports = {};
__export(index_exports, {
  Button: () => Button,
  ButtonMeta: () => ButtonMeta,
  Dialog: () => Dialog,
  DialogBody: () => DialogBody,
  DialogClose: () => DialogClose,
  DialogContent: () => DialogContent,
  DialogDescription: () => DialogDescription,
  DialogFooter: () => DialogFooter,
  DialogMeta: () => DialogMeta,
  DialogTitle: () => DialogTitle,
  DialogTrigger: () => DialogTrigger,
  ICON_NAMES: () => ICON_NAMES,
  ICON_SIZES: () => ICON_SIZES,
  Icon: () => Icon,
  IconButton: () => IconButton,
  IconButtonMeta: () => IconButtonMeta,
  IconMeta: () => IconMeta,
  Input: () => Input,
  InputMeta: () => InputMeta,
  Navbar: () => Navbar,
  NavbarContext: () => NavbarContext,
  NavbarItem: () => NavbarItem,
  NavbarItemMeta: () => NavbarItemMeta,
  NavbarMeta: () => NavbarMeta,
  NavbarSeparator: () => NavbarSeparator,
  NavbarSubmenu: () => NavbarSubmenu,
  NavbarSubmenuItem: () => NavbarSubmenuItem,
  NavbarSubmenuMeta: () => NavbarSubmenuMeta,
  ProductSwitcher: () => ProductSwitcher,
  ProductSwitcherMeta: () => ProductSwitcherMeta,
  SearchField: () => SearchField,
  SearchFieldMeta: () => SearchFieldMeta,
  Switch: () => Switch,
  SwitchMeta: () => SwitchMeta,
  TEXT_DEFAULT_TAG: () => TEXT_DEFAULT_TAG,
  Tabs: () => Tabs,
  TabsContent: () => TabsContent,
  TabsList: () => TabsList,
  TabsMeta: () => TabsMeta,
  TabsTrigger: () => TabsTrigger,
  Text: () => Text2,
  TextMeta: () => TextMeta,
  Tooltip: () => Tooltip,
  TooltipContent: () => TooltipContent,
  TooltipMeta: () => TooltipMeta,
  TooltipProvider: () => TooltipProvider,
  TooltipTrigger: () => TooltipTrigger,
  buttonVariants: () => buttonVariants,
  iconButtonVariants: () => iconButtonVariants,
  inputVariants: () => inputVariants,
  searchFieldVariants: () => searchFieldVariants,
  useNavbarCollapsed: () => useNavbarCollapsed
});
module.exports = __toCommonJS(index_exports);

// src/components/button/Button.tsx
var React = __toESM(require("react"));
var import_react_slot = require("@radix-ui/react-slot");
var import_class_variance_authority = require("class-variance-authority");

// src/lib/cn.ts
var import_clsx = require("clsx");
var import_tailwind_merge = require("tailwind-merge");
var twMerge = (0, import_tailwind_merge.extendTailwindMerge)({
  extend: {
    classGroups: {
      "font-size": [
        {
          text: [
            "1",
            "2",
            "3",
            "4",
            "5",
            "6",
            "7",
            "8",
            "9",
            "10",
            "11",
            "12",
            "title-2xl",
            "title-jumbo",
            "title-xl",
            "title-lg",
            "title-md",
            "title-sm",
            "title-xs",
            "content-base",
            "content-base-bold",
            "content-base-medium",
            "content-base-link",
            "content-compact",
            "content-compact-bold",
            "content-compact-medium",
            "content-compact-link",
            "content-caption",
            "content-highlight",
            "content-button",
            "content-code"
          ]
        }
      ],
      "text-color": [
        {
          text: [
            "default-primary",
            "default-secondary",
            "default-disabled",
            "negative-primary",
            "negative-secondary",
            "negative-disabled",
            "input-placeholder",
            "input-label",
            "input-helper",
            "input-disabled",
            "input-error",
            "input-required",
            "alert",
            // The 4 `text-gradient-*` @utility blocks (theme.css) set
            // `color: transparent` + a background-clip gradient — a
            // mutually-exclusive alternative to a solid semantic color, not
            // a separate concern. Left unregistered, they fall through to
            // tailwind-merge's own default color-arbitrary heuristic, which
            // happens to bucket them with `text-color` today but isn't a
            // guarantee — same unregistered-namespace risk this file was
            // already fixed for once (see the file-level comment above).
            // Registering them explicitly makes "last color wins" a
            // deliberate, documented outcome instead of an accident of
            // tailwind-merge's internals.
            "gradient-focus",
            "gradient-warning",
            "gradient-ia",
            "gradient-default"
          ]
        }
      ]
    }
  }
});
function cn(...inputs) {
  return twMerge((0, import_clsx.clsx)(inputs));
}

// src/components/button/Button.tsx
var import_jsx_runtime = require("react/jsx-runtime");
var buttonVariants = (0, import_class_variance_authority.cva)(
  [
    "inline-flex items-center justify-center whitespace-nowrap gap-2",
    // font-semibold: content-button's Named Style is intended semibold (600)
    // per theme.css, but font-content-button only resolves font-family (see
    // Text.tsx's file-level comment for the full explanation) — the
    // decomposed classes alone never applied the weight. Fixed 2026-07-23
    // (PR #44 review finding), same gap as Tabs.tsx's active-tab state.
    "rounded-sm text-content-button font-content-button font-semibold leading-content-button tracking-content-button",
    "transition",
    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-focus",
    "disabled:pointer-events-none data-[disabled]:pointer-events-none"
  ],
  {
    variants: {
      variant: {
        default: "focus-visible:ring-filigran-brand-primary",
        destructive: "focus-visible:ring-feedback-error-primary",
        ia: "focus-visible:ring-filigran-ia-secondary",
        highlight: "focus-visible:ring-filigran-tonic-primary"
      },
      priority: {
        primary: "disabled:bg-elevation-disabled disabled:text-default-disabled disabled:border-transparent data-[disabled]:bg-elevation-disabled data-[disabled]:text-default-disabled data-[disabled]:border-transparent",
        secondary: [
          "border",
          "disabled:text-default-disabled disabled:border-current data-[disabled]:text-default-disabled data-[disabled]:border-current"
        ],
        tertiary: "disabled:text-default-disabled data-[disabled]:text-default-disabled"
      },
      size: {
        sm: "h-6 px-2",
        md: "h-9 px-4"
      }
    },
    compoundVariants: [
      // default × primary
      {
        variant: "default",
        priority: "primary",
        className: "bg-filigran-brand-primary text-negative-primary hover:bg-filigran-brand-tertiary active:bg-filigran-brand-tertiary"
      },
      // default × secondary
      {
        variant: "default",
        priority: "secondary",
        className: "border-elevation-default text-filigran-brand-primary hover:bg-filigran-brand-primary-transparency active:bg-filigran-brand-primary-transparency"
      },
      // default × tertiary
      {
        variant: "default",
        priority: "tertiary",
        className: "text-filigran-brand-primary hover:bg-filigran-brand-primary-transparency active:bg-filigran-brand-primary-transparency"
      },
      // destructive × primary
      {
        variant: "destructive",
        priority: "primary",
        className: "bg-feedback-error-primary text-negative-primary hover:bg-feedback-error-secondary active:bg-feedback-error-secondary"
      },
      // destructive × secondary
      {
        variant: "destructive",
        priority: "secondary",
        className: "border-feedback-error-secondary text-feedback-error-primary hover:bg-feedback-error-secondary-transparency active:bg-feedback-error-secondary-transparency"
      },
      // destructive × tertiary
      {
        variant: "destructive",
        priority: "tertiary",
        className: "text-feedback-error-primary hover:bg-feedback-error-secondary-transparency active:bg-feedback-error-secondary-transparency"
      },
      // ia × secondary (no primary per RULE-04) — gradient border + text handled in component
      {
        variant: "ia",
        priority: "secondary",
        className: "border-transparent hover:bg-filigran-ia-secondary-transparency active:bg-filigran-ia-secondary-transparency"
      },
      // ia × tertiary
      {
        variant: "ia",
        priority: "tertiary",
        className: "hover:bg-filigran-ia-secondary-transparency active:bg-filigran-ia-secondary-transparency"
      },
      // highlight × secondary (no primary per RULE-04) — gradient border + text handled in component
      {
        variant: "highlight",
        priority: "secondary",
        className: "border-transparent hover:bg-filigran-brand-primary-transparency active:bg-filigran-brand-primary-transparency"
      },
      // highlight × tertiary
      {
        variant: "highlight",
        priority: "tertiary",
        className: "hover:bg-filigran-brand-primary-transparency active:bg-filigran-brand-primary-transparency"
      }
    ],
    defaultVariants: {
      variant: "default",
      priority: "primary",
      size: "md"
    }
  }
);
function Spinner({ className }) {
  return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(
    "svg",
    {
      className: cn("animate-spin h-4 w-4", className),
      xmlns: "http://www.w3.org/2000/svg",
      fill: "none",
      viewBox: "0 0 24 24",
      "aria-hidden": "true",
      children: [
        /* @__PURE__ */ (0, import_jsx_runtime.jsx)("circle", { className: "opacity-25", cx: "12", cy: "12", r: "10", stroke: "currentColor", strokeWidth: "4" }),
        /* @__PURE__ */ (0, import_jsx_runtime.jsx)(
          "path",
          {
            className: "opacity-75",
            fill: "currentColor",
            d: "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
          }
        )
      ]
    }
  );
}
var Button = React.forwardRef(
  ({
    className,
    variant,
    priority,
    size,
    asChild = false,
    startIcon,
    endIcon,
    loading = false,
    fullWidth = false,
    disabled,
    children,
    ...props
  }, ref) => {
    const isDisabled = disabled || loading;
    const resolvedVariant = variant ?? "default";
    const resolvedPriority = priority ?? "primary";
    const enforcedPriority = (resolvedVariant === "ia" || resolvedVariant === "highlight") && resolvedPriority === "primary" ? "secondary" : resolvedPriority;
    const isGradientType = resolvedVariant === "ia" || resolvedVariant === "highlight";
    const isGradientText = isGradientType && (enforcedPriority === "secondary" || enforcedPriority === "tertiary") && !isDisabled;
    const isGradientBorder = isGradientType && enforcedPriority === "secondary" && !isDisabled;
    const gradientBorderClass = isGradientBorder ? resolvedVariant === "highlight" ? "gradient-border-focus" : "gradient-border-ia" : void 0;
    const labelGradientClass = isGradientText ? resolvedVariant === "highlight" ? "text-gradient-focus" : "text-gradient-ia" : void 0;
    if (asChild) {
      return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(
        import_react_slot.Slot,
        {
          className: cn(
            buttonVariants({ variant, priority: enforcedPriority, size }),
            gradientBorderClass,
            fullWidth && "w-full",
            className
          ),
          ref,
          "data-disabled": isDisabled ? "" : void 0,
          "aria-disabled": isDisabled ? true : void 0,
          "aria-busy": loading || void 0,
          ...props,
          ...isDisabled ? {
            onClick: (e) => e.preventDefault(),
            onKeyDown: (e) => {
              if (e.key === "Enter" || e.key === " ") e.preventDefault();
            }
          } : {},
          children
        }
      );
    }
    return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(
      "button",
      {
        className: cn(
          buttonVariants({ variant, priority: enforcedPriority, size }),
          gradientBorderClass,
          fullWidth && "w-full",
          className
        ),
        ref,
        disabled: isDisabled,
        "data-disabled": isDisabled ? "" : void 0,
        "aria-busy": loading || void 0,
        ...props,
        children: loading ? /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(import_jsx_runtime.Fragment, { children: [
          /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Spinner, {}),
          /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { className: labelGradientClass, children }),
          /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { role: "status", className: "sr-only", children: "Loading\u2026" })
        ] }) : /* @__PURE__ */ (0, import_jsx_runtime.jsxs)(import_jsx_runtime.Fragment, { children: [
          startIcon && /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { className: "inline-flex shrink-0", "aria-hidden": "true", children: startIcon }),
          /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { className: labelGradientClass, children }),
          endIcon && /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { className: "inline-flex shrink-0", "aria-hidden": "true", children: endIcon })
        ] })
      }
    );
  }
);
Button.displayName = "Button";

// src/components/button/Button.meta.ts
var import_react2 = require("react");

// src/components/icon/Icon.tsx
var React2 = __toESM(require("react"));
var import_class_variance_authority2 = require("class-variance-authority");

// src/components/icon/icon-registry.generated.tsx
var import_lucide_react = require("lucide-react");
var import_jsx_runtime2 = require("react/jsx-runtime");
function AssetGroupsIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsxs)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: [
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M5 19V21C4.45 21 3.97917 20.8042 3.5875 20.4125C3.19583 20.0208 3 19.55 3 19H5ZM3 17V15H5V17H3ZM3 13V11H5V13H3ZM3 9V7H5V9H3ZM5 5H3C3 4.45 3.19583 3.97917 3.5875 3.5875C3.97917 3.19583 4.45 3 5 3V5ZM7 21V19H9V21H7ZM7 5V3H9V5H7ZM11 21V19H13V21H11ZM11 5V3H13V5H11ZM15 21V19H17V21H15ZM15 5V3H17V5H15ZM19 21V19H21C21 19.55 20.8042 20.0208 20.4125 20.4125C20.0208 20.8042 19.55 21 19 21ZM19 17V15H21V17H19ZM19 13V11H21V13H19ZM19 9V7H21V9H19ZM19 5V3C19.55 3 20.0208 3.19583 20.4125 3.5875C20.8042 3.97917 21 4.45 21 5H19Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M7.00002 7H11V11H7.00002V7Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M13 7H17V11H13V7Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M13 13H17V17H13V13Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M7.00002 13H11V17H7.00002V13Z" })
  ] });
}
function AttackPatternIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { fillRule: "evenodd", clipRule: "evenodd", d: "M9.82843 4.17157C9.07828 3.42143 8.06087 3 7 3C5.93913 3 4.92172 3.42143 4.17157 4.17157C3.42143 4.92172 3 5.93913 3 7C3 8.86 4.27 10.43 6 10.87V13.13C4.27 13.57 3 15.14 3 17C3 18.0609 3.42143 19.0783 4.17157 19.8284C4.92172 20.5786 5.93913 21 7 21C8.06087 21 9.07828 20.5786 9.82843 19.8284C10.5786 19.0783 11 18.0609 11 17C11 16.26 10.8 15.57 10.45 15L15 10.45C15.57 10.8 16.26 11 17 11C18.0609 11 19.0783 10.5786 19.8284 9.82843C20.5786 9.07828 21 8.06087 21 7C21 5.93913 20.5786 4.92172 19.8284 4.17157C19.0783 3.42143 18.0609 3 17 3C15.9391 3 14.9217 3.42143 14.1716 4.17157C13.4214 4.92172 13 5.93913 13 7C13 7.75 13.2 8.44 13.56 9.04L9.04 13.56C8.72 13.37 8.37 13.22 8 13.13V10.87C9.73 10.43 11 8.86 11 7C11 5.93913 10.5786 4.92172 9.82843 4.17157ZM19.8284 14.1716C19.0783 13.4214 18.0609 13 17 13C15.9391 13 14.9217 13.4214 14.1716 14.1716C13.4214 14.9217 13 15.9391 13 17C13 18.0609 13.4214 19.0783 14.1716 19.8284C14.9217 20.5786 15.9391 21 17 21C18.0609 21 19.0783 20.5786 19.8284 19.8284C20.5786 19.0783 21 18.0609 21 17C21 15.9391 20.5786 14.9217 19.8284 14.1716ZM15.5858 15.5858C15.9609 15.2107 16.4696 15 17 15C17.5304 15 18.0391 15.2107 18.4142 15.5858C18.7893 15.9609 19 16.4696 19 17C19 17.5304 18.7893 18.0391 18.4142 18.4142C18.0391 18.7893 17.5304 19 17 19C16.4696 19 15.9609 18.7893 15.5858 18.4142C15.2107 18.0391 15 17.5304 15 17C15 16.4696 15.2107 15.9609 15.5858 15.5858ZM7 15C6.46957 15 5.96086 15.2107 5.58579 15.5858C5.21071 15.9609 5 16.4696 5 17C5 17.5304 5.21071 18.0391 5.58579 18.4142C5.96086 18.7893 6.46957 19 7 19C7.53043 19 8.03914 18.7893 8.41421 18.4142C8.78929 18.0391 9 17.5304 9 17C9 16.4696 8.78929 15.9609 8.41421 15.5858C8.03914 15.2107 7.53043 15 7 15ZM7 5C6.46957 5 5.96086 5.21071 5.58579 5.58579C5.21071 5.96086 5 6.46957 5 7C5 7.53043 5.21071 8.03914 5.58579 8.41421C5.96086 8.78929 6.46957 9 7 9C7.53043 9 8.03914 8.78929 8.41421 8.41421C8.78929 8.03914 9 7.53043 9 7C9 6.46957 8.78929 5.96086 8.41421 5.58579C8.03914 5.21071 7.53043 5 7 5ZM15.5858 5.58579C15.9609 5.21071 16.4696 5 17 5C17.5304 5 18.0391 5.21071 18.4142 5.58579C18.7893 5.96086 19 6.46957 19 7C19 7.53043 18.7893 8.03914 18.4142 8.41421C18.0391 8.78929 17.5304 9 17 9C16.4696 9 15.9609 8.78929 15.5858 8.41421C15.2107 8.03914 15 7.53043 15 7C15 6.46957 15.2107 5.96086 15.5858 5.58579Z" }) });
}
function BinocularIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { fillRule: "evenodd", clipRule: "evenodd", d: "M10.875 5.1428H13.125V13.1428H10.875V5.1428ZM8.625 21.1428C8.625 21.4459 8.50647 21.7366 8.29549 21.9509C8.08452 22.1653 7.79837 22.2857 7.5 22.2857H4.125C3.82663 22.2857 3.54048 22.1653 3.32951 21.9509C3.11853 21.7366 3 21.4459 3 21.1428V15.4285L5.25 5.1428H9.75V13.1428C9.75 13.4459 9.63147 13.7366 9.42049 13.9509C9.20952 14.1653 8.92337 14.2857 8.625 14.2857V21.1428ZM9.75 3.99995H6.375V1.71423H9.75V3.99995ZM15.375 21.1428V14.2857C15.0766 14.2857 14.7905 14.1653 14.5795 13.9509C14.3685 13.7366 14.25 13.4459 14.25 13.1428V5.1428H18.75L21 15.4285V21.1428C21 21.4459 20.8815 21.7366 20.6705 21.9509C20.4595 22.1653 20.1734 22.2857 19.875 22.2857H16.5C16.2016 22.2857 15.9155 22.1653 15.7045 21.9509C15.4935 21.7366 15.375 21.4459 15.375 21.1428ZM14.25 3.99995V1.71423H17.625V3.99995H14.25ZM6.375 20.3571H4.92857V15.4285L6.85714 6.85709H8.14286V11.9999C7.5 11.9999 6.375 12.6428 6.375 13.9509V17.154V20.3571ZM19.0714 20.3571H17.6178V17.154V13.9509C17.6178 12.6428 16.4871 11.9999 15.8411 11.9999V6.85709H17.1332L19.0714 15.4285V20.3571Z" }) });
}
function CaseIncidentIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M17 18C17.6 18 18 18.4 18 19C18 19.6 17.6 20 17 20C16.4 20 16 19.6 16 19C16 18.4 16.4 18 17 18ZM17 15C14.3 15 11.9 16.7 11 19C11.9 21.3 14.3 23 17 23C19.7 23 22.1 21.3 23 19C22.1 16.7 19.7 15 17 15ZM17 21.5C15.6 21.5 14.5 20.4 14.5 19C14.5 17.6 15.6 16.5 17 16.5C18.4 16.5 19.5 17.6 19.5 19C19.5 20.4 18.4 21.5 17 21.5ZM9.1 19.7L8.8 19H4V8H20V13.6C20.7 13.9 21.4 14.2 22 14.7V8C22 7.5 21.8 7 21.4 6.6C21 6.2 20.6 6 20 6H16V4C16 3.4 15.8 3 15.4 2.6C15 2.2 14.6 2 14 2H10C9.4 2 9 2.2 8.6 2.6C8.2 3 8 3.4 8 4V6H4C3.4 6 3 6.2 2.6 6.6C2.2 7 2 7.5 2 8V19C2 19.5 2.2 20 2.6 20.4C3 20.8 3.4 21 4 21H9.8C9.5 20.6 9.3 20.2 9.1 19.7ZM10 4H14V6H10V4Z" }) });
}
function CaseRfiIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M10 2H14C14.5304 2 15.0391 2.21071 15.4142 2.58579C15.7893 2.96086 16 3.46957 16 4V6H20C20.5304 6 21.0391 6.21071 21.4142 6.58579C21.7893 6.96086 22 7.46957 22 8V13.03C21.5 12.23 20.8 11.54 20 11V8H4V19H10.5C10.81 19.75 11.26 20.42 11.81 21H4C2.89 21 2 20.1 2 19V8C2 6.89 2.89 6 4 6H8V4C8 2.89 8.89 2 10 2ZM14 6V4H10V6H14ZM20.31 18.9L23.39 22L22 23.39L18.88 20.32C18.19 20.75 17.37 21 16.5 21C14 21 12 19 12 16.5C12 14 14 12 16.5 12C19 12 21 14 21 16.5C21 17.38 20.75 18.21 20.31 18.9ZM16.5 19C17.163 19 17.7989 18.7366 18.2678 18.2678C18.7366 17.7989 19 17.163 19 16.5C19 15.837 18.7366 15.2011 18.2678 14.7322C17.7989 14.2634 17.163 14 16.5 14C15.837 14 15.2011 14.2634 14.7322 14.7322C14.2634 15.2011 14 15.837 14 16.5C14 17.163 14.2634 17.7989 14.7322 18.2678C15.2011 18.7366 15.837 19 16.5 19Z" }) });
}
function CaseRftIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M10 2H14C14.5304 2 15.0391 2.21071 15.4142 2.58579C15.7893 2.96086 16 3.46957 16 4V6H20C20.5304 6 21.0391 6.21071 21.4142 6.58579C21.7893 6.96086 22 7.46957 22 8V13.53C21.42 13 20.75 12.6 20 12.34V8H4V19H12.08C12.2 19.72 12.45 20.39 12.8 21H4C3.46957 21 2.96086 20.7893 2.58579 20.4142C2.21071 20.0391 2 19.5304 2 19V8C2 7.46957 2.21071 6.96086 2.58579 6.58579C2.96086 6.21071 3.46957 6 4 6H8V4C8 3.46957 8.21071 2.96086 8.58579 2.58579C8.96086 2.21071 9.46957 2 10 2ZM14 6V4H10V6H14ZM14.46 15.88L15.88 14.46L18 16.59L20.12 14.46L21.54 15.88L19.41 18L21.54 20.12L20.12 21.54L18 19.41L15.88 21.54L14.46 20.12L16.59 18L14.46 15.88Z" }) });
}
function CityIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M15 23H13V21H15V23ZM19 21H17V23H19V21ZM15 17H13V19H15V17ZM7 21H5V23H7V21ZM7 17H5V19H7V17ZM19 17H17V19H19V17ZM15 13H13V15H15V13ZM19 13H17V15H19V13ZM21 9C21.5304 9 22.0391 9.21071 22.4142 9.58579C22.7893 9.96086 23 10.4696 23 11V23H21V11H11V23H9V15H3V23H1V15C1 14.4696 1.21071 13.9609 1.58579 13.5858C1.96086 13.2107 2.46957 13 3 13H9V11C9 10.4696 9.21071 9.96086 9.58579 9.58579C9.96086 9.21071 10.4696 9 11 9V7C11 6.46957 11.2107 5.96086 11.5858 5.58579C11.9609 5.21071 12.4696 5 13 5H15V1H17V5H19C19.5304 5 20.0391 5.21071 20.4142 5.58579C20.7893 5.96086 21 6.46957 21 7V9ZM19 9V7H13V9H19Z" }) });
}
function CourseOfActionIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M13.0002 2.03003V2.05003V4.05003C17.3902 4.59003 20.5002 8.58003 19.9602 12.97C19.5002 16.61 16.6402 19.5 13.0002 19.93V21.93C18.5002 21.38 22.5002 16.5 21.9502 11C21.5002 6.25003 17.7302 2.50003 13.0002 2.03003ZM11.0002 2.06003C9.05017 2.25003 7.19017 3.00003 5.67017 4.26003L7.10017 5.74003C8.22017 4.84003 9.57017 4.26003 11.0002 4.06003V2.06003ZM4.26017 5.67003C3.00017 7.19003 2.25017 9.04003 2.05017 11H4.05017C4.24017 9.58003 4.80017 8.23003 5.69017 7.10003L4.26017 5.67003ZM2.06017 13C2.26017 14.96 3.03017 16.81 4.27017 18.33L5.69017 16.9C4.81017 15.77 4.24017 14.42 4.06017 13H2.06017ZM7.10017 18.37L5.67017 19.74C7.18017 21 9.04017 21.79 11.0002 22V20C9.58017 19.82 8.23017 19.25 7.10017 18.37ZM16.8202 15.19L12.7102 11.08C13.1202 10.04 12.8902 8.82003 12.0302 7.97003C11.1302 7.06003 9.78017 6.88003 8.69017 7.38003L10.6302 9.32003L9.28017 10.68L7.29017 8.73003C6.75017 9.82003 7.00017 11.17 7.88017 12.08C8.74017 12.94 9.96017 13.16 11.0002 12.76L15.1102 16.86C15.2902 17.05 15.5602 17.05 15.7402 16.86L16.7802 15.83C17.0002 15.65 17.0002 15.33 16.8202 15.19Z" }) });
}
function FiligranIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { fillRule: "evenodd", clipRule: "evenodd", d: "M5.15177 6.51415C3.94649 8.01649 3.22573 9.92419 3.22573 11.9998C3.22573 14.0759 3.94649 15.9836 5.15177 17.4858V6.51415ZM6.3775 5.26365V18.7364C6.95065 19.2153 7.58586 19.6224 8.2696 19.9439V4.05607C7.58586 4.37763 6.95065 4.78476 6.3775 5.26365ZM9.49533 3.58841V20.4116C10.1009 20.5917 10.7343 20.7082 11.3872 20.7531V3.24693C10.7343 3.29188 10.1009 3.40834 9.49533 3.58841ZM12.6128 3.24693V5.16737H17.5053C16.1485 4.07292 14.4593 3.37412 12.6128 3.24693ZM18.7494 6.39309H12.6128V8.28621H19.9519C19.6318 7.60246 19.2265 6.967 18.7494 6.39309ZM20.4165 9.51195H12.6128V11.404H20.7544C20.7104 10.7512 20.5953 10.118 20.4165 9.51195ZM20.7521 12.6298H12.6128V20.7531C13.2974 20.7058 13.9605 20.5802 14.5931 20.3848V14.598H20.3833C20.578 13.9692 20.7038 13.3101 20.7521 12.6298ZM19.8995 15.8236H15.8188V16.9541H17.0445V18.1799H15.8188V19.9019C17.5955 19.0416 19.0383 17.5998 19.8995 15.8236ZM2 11.9998C2 6.47712 6.47712 2 11.9999 2C17.5229 2 22 6.47712 22 11.9998C22 17.5229 17.5229 22 11.9999 22C6.47712 22 2 17.5229 2 11.9998Z" }) });
}
function FireIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M17.66 11.2C17.43 10.9 17.15 10.64 16.89 10.38C16.22 9.78 15.46 9.35 14.82 8.72C13.33 7.26 13 4.85 13.95 3C13 3.23 12.17 3.75 11.46 4.32C8.86999 6.4 7.84999 10.07 9.06999 13.22C9.10999 13.32 9.14999 13.42 9.14999 13.55C9.14999 13.77 8.99999 13.97 8.79999 14.05C8.56999 14.15 8.32999 14.09 8.13999 13.93C8.07999 13.88 8.03999 13.83 7.99999 13.76C6.86999 12.33 6.68999 10.28 7.44999 8.64C5.77999 10 4.86999 12.3 4.99999 14.47C5.05999 14.97 5.11999 15.47 5.28999 15.97C5.42999 16.57 5.69999 17.17 5.99999 17.7C7.07999 19.43 8.94999 20.67 10.96 20.92C13.1 21.19 15.39 20.8 17.03 19.32C18.86 17.66 19.5 15 18.56 12.72L18.43 12.46C18.22 12 17.66 11.2 17.66 11.2ZM14.5 17.5C14.22 17.74 13.76 18 13.4 18.1C12.28 18.5 11.16 17.94 10.5 17.28C11.69 17 12.4 16.12 12.61 15.23C12.78 14.43 12.46 13.77 12.33 13C12.21 12.26 12.23 11.63 12.5 10.94C12.69 11.32 12.89 11.7 13.13 12C13.9 13 15.11 13.44 15.37 14.8C15.41 14.94 15.43 15.08 15.43 15.23C15.46 16.05 15.1 16.95 14.5 17.5Z" }) });
}
function GlobeLineIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M13 21H18V23H6.00001V21H11V19.95C9.41449 19.7908 7.88995 19.2551 6.55337 18.3875C5.21679 17.5199 4.10686 16.3454 3.31601 14.962L5.05301 13.97C5.66379 15.0373 6.51238 15.9494 7.53286 16.6355C8.55334 17.3215 9.71825 17.7631 10.9371 17.926C12.1559 18.0888 13.3959 17.9684 14.5607 17.5743C15.7255 17.1801 16.7837 16.5227 17.6532 15.6532C18.5227 14.7837 19.1801 13.7255 19.5742 12.5607C19.9684 11.3959 20.0887 10.156 19.9259 8.93712C19.7631 7.71828 19.3215 6.55337 18.6354 5.53289C17.9494 4.51241 17.0373 3.66382 15.97 3.05304L16.963 1.31604C18.4939 2.19071 19.7661 3.45473 20.6507 4.97985C21.5354 6.50497 22.0009 8.23693 22 10C22 15.185 18.054 19.449 13 19.95V21ZM12 17C11.0808 17 10.1705 16.819 9.32123 16.4672C8.47195 16.1154 7.70027 15.5998 7.05026 14.9498C6.40025 14.2998 5.88464 13.5281 5.53285 12.6788C5.18107 11.8295 5.00001 10.9193 5.00001 10C5.00001 9.08079 5.18107 8.17054 5.53285 7.32126C5.88464 6.47198 6.40025 5.7003 7.05026 5.05029C7.70027 4.40028 8.47195 3.88467 9.32123 3.53288C10.1705 3.1811 11.0808 3.00004 12 3.00004C13.8565 3.00004 15.637 3.73754 16.9498 5.05029C18.2625 6.36305 19 8.14352 19 10C19 11.8566 18.2625 13.637 16.9498 14.9498C15.637 16.2625 13.8565 17 12 17ZM12 15C13.3261 15 14.5979 14.4733 15.5355 13.5356C16.4732 12.5979 17 11.3261 17 10C17 8.67396 16.4732 7.40219 15.5355 6.46451C14.5979 5.52682 13.3261 5.00004 12 5.00004C10.6739 5.00004 9.40216 5.52682 8.46448 6.46451C7.52679 7.40219 7.00001 8.67396 7.00001 10C7.00001 11.3261 7.52679 12.5979 8.46448 13.5356C9.40216 14.4733 10.6739 15 12 15Z" }) });
}
function InfrastructureIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M13 19H14C14.2652 19 14.5196 19.1054 14.7071 19.2929C14.8946 19.4804 15 19.7348 15 20H22V22H15C15 22.2652 14.8946 22.5196 14.7071 22.7071C14.5196 22.8946 14.2652 23 14 23H10C9.73478 23 9.48043 22.8946 9.29289 22.7071C9.10536 22.5196 9 22.2652 9 22H2V20H9C9 19.7348 9.10536 19.4804 9.29289 19.2929C9.48043 19.1054 9.73478 19 10 19H11V17H4C3.73478 17 3.48043 16.8946 3.29289 16.7071C3.10536 16.5196 3 16.2652 3 16V12C3 11.7348 3.10536 11.4804 3.29289 11.2929C3.48043 11.1054 3.73478 11 4 11H20C20.2652 11 20.5196 11.1054 20.7071 11.2929C20.8946 11.4804 21 11.7348 21 12V16C21 16.2652 20.8946 16.5196 20.7071 16.7071C20.5196 16.8946 20.2652 17 20 17H13V19ZM4 3H20C20.2652 3 20.5196 3.10536 20.7071 3.29289C20.8946 3.48043 21 3.73478 21 4V8C21 8.26522 20.8946 8.51957 20.7071 8.70711C20.5196 8.89464 20.2652 9 20 9H4C3.73478 9 3.48043 8.89464 3.29289 8.70711C3.10536 8.51957 3 8.26522 3 8V4C3 3.73478 3.10536 3.48043 3.29289 3.29289C3.48043 3.10536 3.73478 3 4 3ZM9 7H10V5H9V7ZM9 15H10V13H9V15ZM5 5V7H7V5H5ZM5 13V15H7V13H5Z" }) });
}
function InjectorsIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsxs)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: [
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M1.5 8.42857H22.5V15.5714H21V17H22.5C23.3284 17 24 16.3604 24 15.5714V8.42857C24 7.63959 23.3284 7 22.5 7H1.5C0.671573 7 0 7.63959 0 8.42857V15.5714C0 16.3604 0.671573 17 1.5 17H13.5V15.5714H1.5V8.42857Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M16.65 13.1429C16.65 13.7143 16.05 14.2857 15.45 14.2857C14.85 14.2857 14.85 15.4286 15.45 15.4286C16.05 15.4286 16.65 16 16.65 16.5714C16.65 17.1429 17.85 17.1429 17.85 16.5714C17.85 16 18.45 15.4286 19.05 15.4286C19.65 15.4286 19.65 14.2857 19.05 14.2857C18.45 14.2857 17.85 13.7143 17.85 13.1429C17.85 12.5714 16.65 12.5714 16.65 13.1429Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M20.05 11.4286C20.05 11.619 19.85 11.8095 19.65 11.8095C19.45 11.8095 19.45 12.1905 19.65 12.1905C19.85 12.1905 20.05 12.381 20.05 12.5714C20.05 12.7619 20.45 12.7619 20.45 12.5714C20.45 12.381 20.65 12.1905 20.85 12.1905C21.05 12.1905 21.05 11.8095 20.85 11.8095C20.65 11.8095 20.45 11.619 20.45 11.4286C20.45 11.2381 20.05 11.2381 20.05 11.4286Z" })
  ] });
}
function IntrusionSetIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M18 2H5.99995L1.99995 8L12 22L22 8L18 2ZM4.42995 8L7.06995 4H16.93L19.57 8L12 18.56L4.42995 8Z" }) });
}
function MalwareIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M23 16.06C23 16.29 23 16.5 22.96 16.7C22.78 14.14 20.64 12.11 18 12.11C17.63 12.11 17.27 12.16 16.92 12.23C16.96 12.5 17 12.73 17 13C17 15.35 15.31 17.32 13.07 17.81C13.42 20.05 15.31 21.79 17.65 21.96C17.43 22 17.22 22 17 22C14.92 22 13.07 20.94 12 19.34C10.93 20.94 9.09 22 7 22C6.78 22 6.57 22 6.35 21.96C8.69 21.79 10.57 20.06 10.93 17.81C8.68 17.32 7 15.35 7 13C7 12.73 7.04 12.5 7.07 12.23C6.73 12.16 6.37 12.11 6 12.11C3.36 12.11 1.22 14.14 1.03 16.7C1 16.5 1 16.29 1 16.06C1 12.85 3.59 10.24 6.81 10.14C6.3 9.27 6 8.25 6 7.17C6 4.94 7.23 3 9.06 2C7.81 2.9 7 4.34 7 6C7 7.35 7.56 8.59 8.47 9.5C9.38 8.59 10.62 8.04 12 8.04C13.37 8.04 14.62 8.59 15.5 9.5C16.43 8.59 17 7.35 17 6C17 4.34 16.18 2.9 14.94 2C16.77 3 18 4.94 18 7.17C18 8.25 17.7 9.27 17.19 10.14C20.42 10.24 23 12.85 23 16.06ZM9.27 10.11C10.05 10.62 11 10.92 12 10.92C13 10.92 13.95 10.62 14.73 10.11C14 9.45 13.06 9.03 12 9.03C10.94 9.03 10 9.45 9.27 10.11ZM12 14.47C12.82 14.47 13.5 13.8 13.5 13C13.5 12.6022 13.342 12.2206 13.0607 11.9393C12.7794 11.658 12.3978 11.5 12 11.5C11.6022 11.5 11.2206 11.658 10.9393 11.9393C10.658 12.2206 10.5 12.6022 10.5 13C10.5 13.8 11.17 14.47 12 14.47ZM10.97 16.79C10.87 14.9 9.71 13.29 8.05 12.55C8.03 12.7 8 12.84 8 13C8 14.82 9.27 16.34 10.97 16.79ZM15.96 12.55C14.29 13.29 13.12 14.9 13 16.79C14.73 16.34 16 14.82 16 13C16 12.84 15.97 12.7 15.96 12.55Z" }) });
}
function OpenaevIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsxs)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: [
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { fillRule: "evenodd", clipRule: "evenodd", d: "M11.3544 22V2H12.6447V22H11.3544Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { fillRule: "evenodd", clipRule: "evenodd", d: "M2 11.3544H22V12.6447H2V11.3544Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { fillRule: "evenodd", clipRule: "evenodd", d: "M8.16389 11.9977H9.45422V14.5449H12.0347V15.8352H8.16389V11.9977Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { fillRule: "evenodd", clipRule: "evenodd", d: "M4.97142 11.9976L6.26174 11.9978L6.26093 17.738H11.9687V19.0284H4.97043L4.97142 11.9976Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { fillRule: "evenodd", clipRule: "evenodd", d: "M11.9572 5.02834H18.9554L18.9545 12.0582L17.6641 12.058L17.6649 6.31867H11.9572V5.02834Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { fillRule: "evenodd", clipRule: "evenodd", d: "M11.9079 8.23688L15.7792 8.23903L15.7783 12.0754L14.488 12.075L14.4887 9.52865L11.9072 9.5272L11.9079 8.23688Z" })
  ] });
}
function OpenctiIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsxs)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: [
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { fillRule: "evenodd", clipRule: "evenodd", d: "M21.0806 21.9171L2 2.91568L2.91939 2.00011L22 21.0015L21.0806 21.9171Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { fillRule: "evenodd", clipRule: "evenodd", d: "M2 21.0015L21.0806 2.00011L22 2.91568L2.91939 21.9171L2 21.0015Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { fillRule: "evenodd", clipRule: "evenodd", d: "M17.5154 2.91573L11.9633 8.44279L6.41573 2.91553L7.33534 2.0002L11.9636 6.61169L16.5962 2L17.5154 2.91573Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { fillRule: "evenodd", clipRule: "evenodd", d: "M11.9867 15.4813L17.5315 21.0013L16.6123 21.917L11.9868 17.3125L7.36397 21.9169L6.44451 21.0014L11.9867 15.4813Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M12.8988 3.09855L11.9048 2.10866L10.9108 3.09855L11.9048 4.08845L12.8988 3.09855Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M12.8997 21.0101L11.9056 20.0202L10.9116 21.0101L11.9056 22L12.8997 21.0101Z" })
  ] });
}
function OpengrcIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsxs)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: [
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { fillRule: "evenodd", clipRule: "evenodd", d: "M12 2L22 12L12 22L2 12L12 2ZM12 3.66087L3.66088 12L12 20.3391L20.3392 12L12 3.66087Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { fillRule: "evenodd", clipRule: "evenodd", d: "M6.53793 12.0002L12.0014 6.53681L17.4647 12.0002L12.0014 17.4636L6.53793 12.0002ZM8.19882 12.0002L12.0014 15.8027L15.804 12.0002L12.0014 8.19768L8.19882 12.0002Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M12.8468 11.9492L11.949 11.0514L11.0511 11.9492L11.949 12.8471L12.8468 11.9492Z" })
  ] });
}
function RelationshipIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsxs)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: [
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { fillRule: "evenodd", clipRule: "evenodd", d: "M19.2999 14.0918V16.2988H22.0001V18.5059H19.2999V20.7129H12.6788V14.0918H19.2999ZM14.8858 16.2988V18.5059H17.0928V16.2988H14.8858Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M11.2784 18.5059H2.00006V16.2988H11.2784V18.5059Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { fillRule: "evenodd", clipRule: "evenodd", d: "M11.3214 9.62109H4.70026V7.41406H2.00006V5.20703H4.70026V3H11.3214V9.62109ZM6.90729 7.41406H9.11432V5.20703H6.90729V7.41406Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M22.0001 7.41406H12.7217V5.20703H22.0001V7.41406Z" })
  ] });
}
function SecurityPlatformsIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsxs)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: [
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M11.8333 17.7501C11.4708 17.6588 11.1208 17.5443 10.7833 17.4066C9.42289 16.8515 8.26681 15.9192 7.31506 14.6099C6.12725 12.9758 5.53334 11.1613 5.53334 9.16629V4.36251L11.8333 2L18.1333 4.36251V9.16629C18.1333 11.1613 17.5394 12.9758 16.3516 14.6099C15.3999 15.9193 14.2438 16.8515 12.8833 17.4066C12.5459 17.5443 12.1959 17.6588 11.8333 17.7501ZM11.8333 16.0963C13.1065 15.7026 14.1696 14.9249 15.0227 13.7634C15.8758 12.6018 16.3746 11.3057 16.519 9.87505H11.8333V3.67345L7.10834 5.44533V9.52067C7.10834 9.61255 7.12147 9.73067 7.14772 9.87505H11.8333V16.0963Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { fillRule: "evenodd", clipRule: "evenodd", d: "M12.8833 18.8H13.9333C14.2118 18.8 14.4789 18.9106 14.6758 19.1075C14.8727 19.3044 14.9833 19.5715 14.9833 19.85H22.3333V21.95H14.9833C14.9833 22.2285 14.8727 22.4955 14.6758 22.6925C14.4789 22.8894 14.2118 23 13.9333 23H9.73331C9.45483 23 9.18776 22.8894 8.99085 22.6925C8.79394 22.4955 8.68331 22.2285 8.68331 21.95H1.33331V19.85H8.68331C8.68331 19.5715 8.79394 19.3044 8.99085 19.1075C9.18776 18.9106 9.45483 18.8 9.73331 18.8H10.7833V17.4066C11.1208 17.5443 11.4708 17.6588 11.8333 17.7501C12.1959 17.6588 12.5459 17.5443 12.8833 17.4066V18.8Z" })
  ] });
}
function SlackIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsxs)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: [
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M6.21639 14.6433C6.21639 15.8057 5.27765 16.7452 4.11615 16.7452C2.95465 16.7452 2.01591 15.8057 2.01591 14.6433C2.01591 13.4809 2.95465 12.5414 4.11615 12.5414H6.21639V14.6433ZM7.26651 14.6433C7.26651 13.4809 8.20525 12.5414 9.36675 12.5414C10.5282 12.5414 11.467 13.4809 11.467 14.6433V19.8981C11.467 21.0605 10.5282 22 9.36675 22C8.20525 22 7.26651 21.0605 7.26651 19.8981V14.6433Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M9.36675 6.20382C8.20525 6.20382 7.26651 5.26433 7.26651 4.10191C7.26651 2.93949 8.20525 2 9.36675 2C10.5282 2 11.467 2.93949 11.467 4.10191V6.20382H9.36675ZM9.36675 7.2707C10.5282 7.2707 11.467 8.21019 11.467 9.37261C11.467 10.535 10.5282 11.4745 9.36675 11.4745H4.10024C2.93874 11.4745 2 10.535 2 9.37261C2 8.21019 2.93874 7.2707 4.10024 7.2707H9.36675Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M17.7836 9.37261C17.7836 8.21019 18.7224 7.2707 19.8839 7.2707C21.0453 7.2707 21.9841 8.21019 21.9841 9.37261C21.9841 10.535 21.0453 11.4745 19.8839 11.4745H17.7836V9.37261ZM16.7335 9.37261C16.7335 10.535 15.7947 11.4745 14.6333 11.4745C13.4718 11.4745 12.533 10.535 12.533 9.37261V4.10191C12.533 2.93949 13.4718 2 14.6333 2C15.7947 2 16.7335 2.93949 16.7335 4.10191V9.37261Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M14.6333 17.7962C15.7947 17.7962 16.7335 18.7357 16.7335 19.8981C16.7335 21.0605 15.7947 22 14.6333 22C13.4718 22 12.533 21.0605 12.533 19.8981V17.7962H14.6333ZM14.6333 16.7452C13.4718 16.7452 12.533 15.8057 12.533 14.6433C12.533 13.4809 13.4718 12.5414 14.6333 12.5414H19.8998C21.0613 12.5414 22 13.4809 22 14.6433C22 15.8057 21.0613 16.7452 19.8998 16.7452H14.6333Z" })
  ] });
}
function TargetIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsxs)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: [
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M10.96 0H13.04V6.5H10.96V0Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M10.96 17.5H13.04V24H10.96V17.5Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M24 10.9602V13.0402H17.5V10.9602H24Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M6.49999 10.9602V13.0402H0L9.09196e-08 10.9602H6.49999Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M20.0244 12C20.0244 7.56791 16.4321 3.97559 12 3.97559C7.56794 3.97559 3.97561 7.56791 3.97561 12C3.97561 16.4321 7.56794 20.0244 12 20.0244C16.4321 20.0244 20.0244 16.4321 20.0244 12ZM21.9756 12C21.9756 17.509 17.509 21.9756 12 21.9756C6.49098 21.9756 2.02444 17.509 2.02444 12C2.02444 6.49096 6.49098 2.02441 12 2.02441C17.509 2.02441 21.9756 6.49096 21.9756 12Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M13.5 12C13.5 12.8284 12.8284 13.5 12 13.5C11.1716 13.5 10.5 12.8284 10.5 12C10.5 11.1716 11.1716 10.5 12 10.5C12.8284 10.5 13.5 11.1716 13.5 12Z" })
  ] });
}
function ThreatActorIndividualIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M19.3333 4.83337C20.3417 4.83337 21.1667 5.66775 21.1667 6.70837V16.0834C21.1667 17.124 20.3508 17.9584 19.3333 17.9584H23V19.8334H1V17.9584H4.66667C3.65833 17.9584 2.83333 17.124 2.83333 16.0834V6.70837C2.83333 5.66775 3.64917 4.83337 4.66667 4.83337H19.3333ZM19.3333 6.70837H4.66667V16.0834H19.3333V6.70837ZM12 12.3334C14.0258 12.3334 15.6667 13.1771 15.6667 14.2084V15.1459H8.33333V14.2084C8.33333 13.1771 9.97417 12.3334 12 12.3334ZM12 7.64587C13.0175 7.64587 13.8333 8.48962 13.8333 9.52087C13.8333 10.5521 13.0175 11.3959 12 11.3959C10.9825 11.3959 10.1667 10.5615 10.1667 9.52087C10.1667 8.48025 10.9917 7.64587 12 7.64587Z" }) });
}
function XtmhubIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { fillRule: "evenodd", clipRule: "evenodd", d: "M5.15177 6.5141C3.94665 8.01656 3.22574 9.9241 3.22574 12C3.22574 14.0759 3.94665 15.9834 5.15177 17.4859V6.5141ZM6.37751 5.2636V18.7364C6.95073 19.2153 7.58599 19.6225 8.2695 19.944V4.05599C7.58599 4.3775 6.95073 4.78466 6.37751 5.2636ZM9.49524 3.58844V20.4116C10.101 20.5917 10.7342 20.7081 11.3871 20.7532V3.24681C10.7342 3.29186 10.101 3.40832 9.49524 3.58844ZM12.6128 3.24681V5.16732H17.5052C16.1486 4.07287 14.4593 3.3742 12.6128 3.24681ZM18.7494 6.39306H12.6128V8.28624H19.9519C19.632 7.60255 19.2266 6.96689 18.7494 6.39306ZM20.4165 9.51198H12.6128V11.4039H20.7543C20.7105 10.7511 20.5953 10.1179 20.4165 9.51198ZM20.752 12.6297H12.6128V20.7532C13.2975 20.7059 13.9605 20.5802 14.593 20.3848V14.5979H20.3833C20.5779 13.9691 20.7038 13.3101 20.752 12.6297ZM19.8995 15.8236H15.8187V16.954H17.0445V18.1798H15.8187V19.9019C17.5955 19.0416 19.0382 17.5998 19.8995 15.8236ZM2 12C2 6.47715 6.47715 2 12 2C17.5228 2 22 6.47715 22 12C22 17.5228 17.5228 22 12 22C6.47715 22 2 17.5228 2 12Z" }) });
}
function XtmoneIcon(props) {
  return /* @__PURE__ */ (0, import_jsx_runtime2.jsxs)("svg", { ...props, xmlns: "http://www.w3.org/2000/svg", viewBox: "0 0 24 24", fill: "currentColor", children: [
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M16.2718 11.0797L20.7524 6.38966L19.7609 5.35103L13.5855 11.8139L17.1927 15.5891L18.1851 14.5505L16.2718 12.5481H22V11.0797H16.2718Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M15.595 7.03032L14.6026 5.9925L12.6902 7.99405V2H11.2863V7.99405L6.41528 2.89618L5.42287 3.93399L11.9879 10.8055L15.595 7.03032Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M6.80728 8.41095L5.81487 9.44957L7.72815 11.4519H2V12.9203H7.72815L2.92164 17.9507L3.91405 18.9884L10.4145 12.1861L6.80728 8.41095Z" }),
    /* @__PURE__ */ (0, import_jsx_runtime2.jsx)("path", { d: "M8.4048 16.9689L9.39721 18.0076L11.3097 16.0052V22H12.7135V16.0052L17.6391 21.1609L18.6315 20.1222L12.012 13.1946L8.4048 16.9689Z" })
  ] });
}
var iconRegistry = {
  "activity": import_lucide_react.Activity,
  "alarm-clock": import_lucide_react.AlarmClock,
  "align-justify": import_lucide_react.AlignJustify,
  "align-left": import_lucide_react.AlignLeft,
  "anchor": import_lucide_react.Anchor,
  "arrow-down": import_lucide_react.ArrowDown,
  "arrow-down-left": import_lucide_react.ArrowDownLeft,
  "arrow-left": import_lucide_react.ArrowLeft,
  "arrow-right": import_lucide_react.ArrowRight,
  "arrow-right-to-line": import_lucide_react.ArrowRightToLine,
  "arrow-up": import_lucide_react.ArrowUp,
  "arrow-up-down": import_lucide_react.ArrowUpDown,
  "arrow-up-right": import_lucide_react.ArrowUpRight,
  "award": import_lucide_react.Award,
  "badge-check": import_lucide_react.BadgeCheck,
  "bell": import_lucide_react.Bell,
  "bell-off": import_lucide_react.BellOff,
  "bell-plus": import_lucide_react.BellPlus,
  "bolt": import_lucide_react.Bolt,
  "book": import_lucide_react.Book,
  "book-text": import_lucide_react.BookText,
  "bookmark": import_lucide_react.Bookmark,
  "bookmark-check": import_lucide_react.BookmarkCheck,
  "bot": import_lucide_react.Bot,
  "box": import_lucide_react.Box,
  "brick-wall": import_lucide_react.BrickWall,
  "briefcase": import_lucide_react.Briefcase,
  "briefcase-business": import_lucide_react.BriefcaseBusiness,
  "bug": import_lucide_react.Bug,
  "building-2": import_lucide_react.Building2,
  "calendar": import_lucide_react.Calendar,
  "calendar-days": import_lucide_react.CalendarDays,
  "case-sensitive": import_lucide_react.CaseSensitive,
  "chart-bar": import_lucide_react.ChartBar,
  "chart-column": import_lucide_react.ChartColumn,
  "check": import_lucide_react.Check,
  "chess-knight": import_lucide_react.ChessKnight,
  "chess-queen": import_lucide_react.ChessQueen,
  "chevron-down": import_lucide_react.ChevronDown,
  "chevron-left": import_lucide_react.ChevronLeft,
  "chevron-right": import_lucide_react.ChevronRight,
  "chevron-up": import_lucide_react.ChevronUp,
  "chevrons-down-up": import_lucide_react.ChevronsDownUp,
  "chevrons-up-down": import_lucide_react.ChevronsUpDown,
  "circle": import_lucide_react.Circle,
  "circle-alert": import_lucide_react.CircleAlert,
  "circle-arrow-down": import_lucide_react.CircleArrowDown,
  "circle-check": import_lucide_react.CircleCheck,
  "circle-check-big": import_lucide_react.CircleCheckBig,
  "circle-play": import_lucide_react.CirclePlay,
  "circle-plus": import_lucide_react.CirclePlus,
  "circle-question-mark": import_lucide_react.CircleQuestionMark,
  "circle-user": import_lucide_react.CircleUser,
  "circle-x": import_lucide_react.CircleX,
  "clapperboard": import_lucide_react.Clapperboard,
  "clipboard-list": import_lucide_react.ClipboardList,
  "clock": import_lucide_react.Clock,
  "cloud": import_lucide_react.Cloud,
  "cloud-download": import_lucide_react.CloudDownload,
  "cloud-upload": import_lucide_react.CloudUpload,
  "columns-3": import_lucide_react.Columns3,
  "compass": import_lucide_react.Compass,
  "construction": import_lucide_react.Construction,
  "database": import_lucide_react.Database,
  "diamond-plus": import_lucide_react.DiamondPlus,
  "download": import_lucide_react.Download,
  "drafting-compass": import_lucide_react.DraftingCompass,
  "earth": import_lucide_react.Earth,
  "ellipsis": import_lucide_react.Ellipsis,
  "ellipsis-vertical": import_lucide_react.EllipsisVertical,
  "external-link": import_lucide_react.ExternalLink,
  "eye": import_lucide_react.Eye,
  "eye-off": import_lucide_react.EyeOff,
  "file": import_lucide_react.File,
  "file-input": import_lucide_react.FileInput,
  "file-search": import_lucide_react.FileSearch,
  "file-text": import_lucide_react.FileText,
  "file-up": import_lucide_react.FileUp,
  "files": import_lucide_react.Files,
  "flag": import_lucide_react.Flag,
  "flask-conical": import_lucide_react.FlaskConical,
  "focus": import_lucide_react.Focus,
  "folder-open": import_lucide_react.FolderOpen,
  "forklift": import_lucide_react.Forklift,
  "gem": import_lucide_react.Gem,
  "graduation-cap": import_lucide_react.GraduationCap,
  "grid-3x2": import_lucide_react.Grid3x2,
  "grip": import_lucide_react.Grip,
  "grip-horizontal": import_lucide_react.GripHorizontal,
  "grip-vertical": import_lucide_react.GripVertical,
  "hexagon": import_lucide_react.Hexagon,
  "house": import_lucide_react.House,
  "image": import_lucide_react.Image,
  "image-play": import_lucide_react.ImagePlay,
  "inbox": import_lucide_react.Inbox,
  "info": import_lucide_react.Info,
  "key": import_lucide_react.Key,
  "key-round": import_lucide_react.KeyRound,
  "key-square": import_lucide_react.KeySquare,
  "landmark": import_lucide_react.Landmark,
  "languages": import_lucide_react.Languages,
  "laptop-minimal": import_lucide_react.LaptopMinimal,
  "layers": import_lucide_react.Layers,
  "layers-2": import_lucide_react.Layers2,
  "layout-dashboard": import_lucide_react.LayoutDashboard,
  "layout-grid": import_lucide_react.LayoutGrid,
  "layout-panel-left": import_lucide_react.LayoutPanelLeft,
  "link-2": import_lucide_react.Link2,
  "list": import_lucide_react.List,
  "list-checks": import_lucide_react.ListChecks,
  "list-tree": import_lucide_react.ListTree,
  "list-x": import_lucide_react.ListX,
  "locate": import_lucide_react.Locate,
  "locate-fixed": import_lucide_react.LocateFixed,
  "lock": import_lucide_react.Lock,
  "log-out": import_lucide_react.LogOut,
  "mail": import_lucide_react.Mail,
  "map": import_lucide_react.Map,
  "map-pin": import_lucide_react.MapPin,
  "maximize-2": import_lucide_react.Maximize2,
  "megaphone": import_lucide_react.Megaphone,
  "message-square-more": import_lucide_react.MessageSquareMore,
  "message-square-text": import_lucide_react.MessageSquareText,
  "messages-square": import_lucide_react.MessagesSquare,
  "minus": import_lucide_react.Minus,
  "monitor": import_lucide_react.Monitor,
  "monitor-down": import_lucide_react.MonitorDown,
  "monitor-smartphone": import_lucide_react.MonitorSmartphone,
  "moon": import_lucide_react.Moon,
  "network": import_lucide_react.Network,
  "newspaper": import_lucide_react.Newspaper,
  "orbit": import_lucide_react.Orbit,
  "package-check": import_lucide_react.PackageCheck,
  "panel-left-close": import_lucide_react.PanelLeftClose,
  "panel-left-open": import_lucide_react.PanelLeftOpen,
  "panel-top": import_lucide_react.PanelTop,
  "paperclip": import_lucide_react.Paperclip,
  "pencil": import_lucide_react.Pencil,
  "play": import_lucide_react.Play,
  "plus": import_lucide_react.Plus,
  "radar": import_lucide_react.Radar,
  "refresh-cw": import_lucide_react.RefreshCw,
  "repeat": import_lucide_react.Repeat,
  "rocket": import_lucide_react.Rocket,
  "rotate-3d": import_lucide_react.Rotate3d,
  "rotate-ccw": import_lucide_react.RotateCcw,
  "route": import_lucide_react.Route,
  "router": import_lucide_react.Router,
  "satellite-dish": import_lucide_react.SatelliteDish,
  "save": import_lucide_react.Save,
  "search": import_lucide_react.Search,
  "server": import_lucide_react.Server,
  "settings": import_lucide_react.Settings,
  "share-2": import_lucide_react.Share2,
  "shield": import_lucide_react.Shield,
  "shield-check": import_lucide_react.ShieldCheck,
  "shopping-basket": import_lucide_react.ShoppingBasket,
  "signpost": import_lucide_react.Signpost,
  "skip-back": import_lucide_react.SkipBack,
  "skip-forward": import_lucide_react.SkipForward,
  "sliders-horizontal": import_lucide_react.SlidersHorizontal,
  "sliders-vertical": import_lucide_react.SlidersVertical,
  "speaker": import_lucide_react.Speaker,
  "square-chart-gantt": import_lucide_react.SquareChartGantt,
  "square-terminal": import_lucide_react.SquareTerminal,
  "star": import_lucide_react.Star,
  "sticky-note": import_lucide_react.StickyNote,
  "sticky-note-plus": import_lucide_react.StickyNotePlus,
  "sun": import_lucide_react.Sun,
  "table-properties": import_lucide_react.TableProperties,
  "tag": import_lucide_react.Tag,
  "terminal": import_lucide_react.Terminal,
  "text": import_lucide_react.Text,
  "text-search": import_lucide_react.TextSearch,
  "thermometer": import_lucide_react.Thermometer,
  "thumbs-down": import_lucide_react.ThumbsDown,
  "thumbs-up": import_lucide_react.ThumbsUp,
  "trash-2": import_lucide_react.Trash2,
  "triangle-alert": import_lucide_react.TriangleAlert,
  "type": import_lucide_react.Type,
  "unlink": import_lucide_react.Unlink,
  "upload": import_lucide_react.Upload,
  "user": import_lucide_react.User,
  "user-lock": import_lucide_react.UserLock,
  "users": import_lucide_react.Users,
  "waypoints": import_lucide_react.Waypoints,
  "wrench": import_lucide_react.Wrench,
  "x": import_lucide_react.X,
  "custom/asset-groups": AssetGroupsIcon,
  "custom/attack-pattern": AttackPatternIcon,
  "custom/binocular": BinocularIcon,
  "custom/case-incident": CaseIncidentIcon,
  "custom/case-rfi": CaseRfiIcon,
  "custom/case-rft": CaseRftIcon,
  "custom/city": CityIcon,
  "custom/course-of-action": CourseOfActionIcon,
  "custom/filigran": FiligranIcon,
  "custom/fire": FireIcon,
  "custom/globe-line": GlobeLineIcon,
  "custom/infrastructure": InfrastructureIcon,
  "custom/injectors": InjectorsIcon,
  "custom/intrusion-set": IntrusionSetIcon,
  "custom/malware": MalwareIcon,
  "custom/openaev": OpenaevIcon,
  "custom/opencti": OpenctiIcon,
  "custom/opengrc": OpengrcIcon,
  "custom/relationship": RelationshipIcon,
  "custom/security-platforms": SecurityPlatformsIcon,
  "custom/slack": SlackIcon,
  "custom/target": TargetIcon,
  "custom/threat-actor-individual": ThreatActorIndividualIcon,
  "custom/xtmhub": XtmhubIcon,
  "custom/xtmone": XtmoneIcon
};
var ICON_NAMES = Object.keys(iconRegistry);

// src/components/icon/Icon.tsx
var import_jsx_runtime3 = require("react/jsx-runtime");
var ICON_SIZES = [16, 20, 24];
var iconVariants = (0, import_class_variance_authority2.cva)("inline-block shrink-0", {
  variants: {
    size: {
      16: "size-4",
      20: "size-5",
      24: "size-6"
    }
  },
  defaultVariants: {
    size: 24
  }
});
function Icon(props) {
  const { name, size = 24, gradient, className } = props;
  const ariaLabel = "aria-label" in props ? props["aria-label"] : void 0;
  const maskId = `icon-gradient-${React2.useId().replace(/[^a-zA-Z0-9_-]/g, "")}`;
  const Glyph = iconRegistry[name];
  if (!Glyph) {
    throw new Error(
      `[Icon] Unknown icon name "${String(name)}". The vocabulary is the ICON_NAMES export from "@filigran/design-system" (generated \u2014 includes every valid IconName).`
    );
  }
  const a11yProps = ariaLabel ? { role: "img", "aria-label": ariaLabel } : { "aria-hidden": true };
  if (gradient) {
    return /* @__PURE__ */ (0, import_jsx_runtime3.jsxs)("span", { ...a11yProps, className: cn(iconVariants({ size }), "relative", className), children: [
      /* @__PURE__ */ (0, import_jsx_runtime3.jsx)("svg", { width: "0", height: "0", "aria-hidden": "true", className: "absolute", children: /* @__PURE__ */ (0, import_jsx_runtime3.jsx)("defs", { children: /* @__PURE__ */ (0, import_jsx_runtime3.jsx)("mask", { id: maskId, maskUnits: "userSpaceOnUse", x: "0", y: "0", width: size, height: size, children: /* @__PURE__ */ (0, import_jsx_runtime3.jsx)(Glyph, { width: size, height: size, color: "white" }) }) }) }),
      /* @__PURE__ */ (0, import_jsx_runtime3.jsx)(
        "span",
        {
          className: cn(
            "absolute inset-0",
            gradient === "ia" ? "bg-gradient-ia" : "bg-gradient-focus"
          ),
          style: { mask: `url("#${maskId}")`, WebkitMask: `url("#${maskId}")` }
        }
      )
    ] });
  }
  return /* @__PURE__ */ (0, import_jsx_runtime3.jsx)(
    Glyph,
    {
      width: size,
      height: size,
      ...a11yProps,
      className: cn(iconVariants({ size }), className)
    }
  );
}

// src/components/icon/Icon.meta.ts
var import_react = require("react");
var customCount = ICON_NAMES.filter((name) => name.startsWith("custom/")).length;
var lucideCount = ICON_NAMES.length - customCount;
var IconMeta = {
  name: "Icon",
  description: `Single entry point for icon rendering in the design system. Resolves a string name against the generated icon registry: ${lucideCount} Lucide glyphs (named imports \u2014 tree-shakeable) plus ${customCount} Filigran custom glyphs (custom/* prefix, currentColor). Decorative by default; semantic with aria-label.`,
  status: "stable",
  version: "0.1.0",
  radixPrimitive: "none",
  figmaLink: "https://www.figma.com/design/0PmhuZzF9XcaIEfMMW2a51/Design_System_2026?node-id=5642-192",
  figmaNodeId: "5642:192",
  variants: ["default", "gradient-ia", "gradient-focus"],
  sizes: ["16", "20", "24"],
  examples: [
    '// Decorative (default): hidden from assistive technologies\n<Icon name="search" />',
    '// Standalone semantic icon: accessible name + role="img"\n<Icon name="circle-alert" aria-label="Warning" />',
    '// Custom Filigran glyph \u2014 recolorable through the text color (currentColor)\n<span className="text-default-secondary">\n  <Icon name="custom/malware" size={16} />\n</span>',
    '// Decorative gradient accent (AI feature affordance)\n<Icon name="bolt" gradient="ia" />'
  ],
  render() {
    const label = (text) => (0, import_react.createElement)(
      "p",
      {
        className: "text-content-caption font-content-caption leading-content-caption tracking-content-caption text-default-secondary"
      },
      text
    );
    const row = (children) => (0, import_react.createElement)("div", { className: "flex items-center gap-4 text-default-primary" }, ...children);
    return (0, import_react.createElement)(
      "div",
      { className: "flex flex-col gap-6" },
      (0, import_react.createElement)(
        "div",
        { className: "flex flex-col gap-2" },
        label("Sizes \u2014 16 / 20 / 24 (token classes size-4/5/6)"),
        row([
          (0, import_react.createElement)(Icon, { name: "search", size: 16 }),
          (0, import_react.createElement)(Icon, { name: "search", size: 20 }),
          (0, import_react.createElement)(Icon, { name: "search", size: 24 })
        ])
      ),
      (0, import_react.createElement)(
        "div",
        { className: "flex flex-col gap-2" },
        label("Lucide set (sample) \u2014 color inherited via currentColor"),
        row([
          (0, import_react.createElement)(Icon, { name: "bell" }),
          (0, import_react.createElement)(Icon, { name: "calendar" }),
          (0, import_react.createElement)(Icon, { name: "chart-column" }),
          (0, import_react.createElement)(Icon, { name: "shield-check" }),
          (0, import_react.createElement)(Icon, { name: "settings" }),
          (0, import_react.createElement)(Icon, { name: "trash-2" })
        ])
      ),
      (0, import_react.createElement)(
        "div",
        { className: "flex flex-col gap-2" },
        label("Custom Filigran glyphs (sample) \u2014 custom/* names"),
        row([
          (0, import_react.createElement)(Icon, { name: "custom/filigran" }),
          (0, import_react.createElement)(Icon, { name: "custom/malware" }),
          (0, import_react.createElement)(Icon, { name: "custom/intrusion-set" }),
          (0, import_react.createElement)(Icon, { name: "custom/opencti" }),
          (0, import_react.createElement)(Icon, { name: "custom/xtmone" }),
          (0, import_react.createElement)(Icon, { name: "custom/slack" })
        ])
      ),
      (0, import_react.createElement)(
        "div",
        { className: "flex flex-col gap-2" },
        label('Semantic \u2014 aria-label + role="img" (inspect the DOM)'),
        row([(0, import_react.createElement)(Icon, { name: "circle-alert", "aria-label": "Warning" })])
      ),
      (0, import_react.createElement)(
        "div",
        { className: "flex flex-col gap-2" },
        label("Gradient accents (decorative only) \u2014 ia / focus"),
        row([
          (0, import_react.createElement)(Icon, { name: "bolt", gradient: "ia" }),
          (0, import_react.createElement)(Icon, { name: "custom/filigran", gradient: "ia" }),
          (0, import_react.createElement)(Icon, { name: "bolt", gradient: "focus" }),
          (0, import_react.createElement)(Icon, { name: "custom/filigran", gradient: "focus" })
        ])
      )
    );
  },
  props: {
    name: 'IconName (required) \u2014 icon identifier from the generated vocabulary (ICON_NAMES export). Lucide glyphs use their kebab-case Lucide name (e.g. "chevron-down"); Filigran glyphs use the custom/ prefix (e.g. "custom/malware"). Unknown names throw.',
    size: "16 | 20 | 24 \u2014 rendered size in px, mapped to token classes size-4/5/6 (default: 24).",
    gradient: '"ia" | "focus" \u2014 decorative gradient fill via the bg-gradient-ia / bg-gradient-focus token utilities and an SVG luminance mask. Gradient icons are visual accents: never the sole carrier of meaning.',
    "aria-label": 'string \u2014 accessible name for a STANDALONE semantic icon; adds role="img" and removes aria-hidden. Omit it (decorative default) when the icon accompanies visible text.',
    "aria-hidden": "boolean \u2014 true by default (decorative). Typing forbids aria-hidden={false} without an aria-label.",
    className: "string \u2014 extra token classes (e.g. text-feedback-error-primary to color the icon). Color always flows through currentColor."
  },
  accessibility: {
    wcag: "2.1 AA",
    wcagStatus: "pass",
    contrastPairs: [
      {
        id: "semantic icon (primary text color)",
        fg: "--text-default-primary",
        bg: "--bg-elevation-default",
        minRatio: 3
      },
      {
        id: "semantic icon (secondary text color)",
        fg: "--text-default-secondary",
        bg: "--bg-elevation-default",
        minRatio: 3
      }
    ],
    notes: "Criteria: 1.1.1 Non-text Content \u2014 decorative by default: aria-hidden='true' unless an aria-label is provided; the props type forbids a semantic icon without an accessible name. 4.1.2 Name, Role, Value \u2014 semantic icons render role='img' + aria-label on the SVG (or on the gradient wrapper). 1.4.11 Non-text Contrast \u2014 icons inherit currentColor; the declared pairs prove the documented text-color tokens reach \u2265 3:1 on the page background in both modes (computed in CI by check-contrast). Gradient icons (gradient='ia'|'focus') are decorative accents: gradient stops are NOT verified against arbitrary backgrounds, so a gradient icon must never be the sole carrier of meaning \u2014 pair it with text or a semantic sibling. 2.1.1 Keyboard \u2014 not applicable: Icon renders static, non-interactive SVG (no focusable content, no keyboard test needed); interactive behaviour belongs to the wrapping component (e.g. IconButton). Custom glyphs are generated with fill='currentColor' (hardcoded fills stripped at generation), so light/dark rendering follows the text color tokens. wcagStatus 'pass' basis: axe-core conformity suite reports 0 violations in both modes on the full render() surface (S1), and both declared contrast pairs are computed from tokens in CI (Q2). Automated checks cover ~half of WCAG \u2014 human/screen-reader audit cadence still applies per release."
  }
};

// src/components/button/Button.meta.ts
var ButtonMeta = {
  name: "Button",
  description: "Primary interaction control for actions such as submit, confirm, cancel, and destructive operations. Structured around three axes: variant (default, destructive, ia, highlight), priority (primary, secondary, tertiary), and size (sm, md). IA and Highlight variants have no primary priority (RULE-04). Supports startIcon, endIcon, loading, and fullWidth props.",
  status: "beta",
  version: "0.2.0",
  radixPrimitive: "@radix-ui/react-slot",
  figmaLink: "https://www.figma.com/design/0PmhuZzF9XcaIEfMMW2a51/Design_System_2026?node-id=2782-6320",
  figmaNodeId: "2782:6320",
  accessibility: {
    wcag: "2.1 AA",
    wcagStatus: "fail",
    contrastPairs: [
      {
        id: "default primary",
        fg: "--text-negative-primary",
        bg: "--color-filigran-brand-primary",
        minRatio: 4.5
      },
      {
        id: "default secondary",
        fg: "--color-filigran-brand-primary",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: "default tertiary",
        fg: "--color-filigran-brand-primary",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: "destructive primary",
        fg: "--text-negative-primary",
        bg: "--color-feedback-error-primary",
        minRatio: 4.5
      },
      {
        id: "destructive secondary",
        fg: "--color-feedback-error-primary",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: "destructive tertiary",
        fg: "--color-feedback-error-primary",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: "ia text (stop 1)",
        fg: "--color-filigran-ia-secondary",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: "ia text (stop 2)",
        fg: "--color-filigran-ia-primary",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: "highlight text (stop 1)",
        fg: "--color-filigran-brand-primary",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: "highlight text (stop 2)",
        fg: "--color-filigran-tonic-primary",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: "focus ring",
        fg: "--color-filigran-brand-primary",
        bg: "--bg-elevation-default",
        minRatio: 3
      }
    ],
    notes: "Uses native <button> semantics, keyboard accessible, visible focus ring (focus-visible:ring-2). Known limitation: highlight gradient stop 2 (--color-filigran-tonic-primary, light mode turquoise-600 #00bd94) on --bg-elevation-default (#f2f2f3) = 2.16:1 \u2014 fails 4.5:1 AA. Allow-listed until 2026-08-31, requires further Figma token remediation. All other declared pairs pass. Loading state disables the button and sets aria-busy=true."
  },
  render() {
    const labelClass = "text-content-compact font-content-compact leading-content-compact tracking-content-compact text-default-secondary w-28 shrink-0";
    const sectionClass = "text-content-caption font-content-caption leading-content-caption tracking-content-caption text-default-secondary";
    const grid = [
      { variant: "default", priorities: ["primary", "secondary", "tertiary"] },
      {
        variant: "destructive",
        priorities: ["primary", "secondary", "tertiary"]
      },
      { variant: "ia", priorities: ["secondary", "tertiary"] },
      { variant: "highlight", priorities: ["secondary", "tertiary"] }
    ];
    return (0, import_react2.createElement)(
      "div",
      { className: "flex flex-col gap-6" },
      // Section 1: Variant × Priority grid
      (0, import_react2.createElement)("p", { className: sectionClass }, "Variant \xD7 Priority"),
      (0, import_react2.createElement)(
        "div",
        { className: "flex flex-col gap-4" },
        // Column headers
        (0, import_react2.createElement)(
          "div",
          { className: "flex items-center gap-4" },
          (0, import_react2.createElement)("span", { className: "w-28 shrink-0" }),
          (0, import_react2.createElement)("span", { className: sectionClass }, "primary"),
          (0, import_react2.createElement)("span", { className: sectionClass }, "secondary"),
          (0, import_react2.createElement)("span", { className: sectionClass }, "tertiary")
        ),
        ...grid.map(
          (row) => (0, import_react2.createElement)(
            "div",
            {
              key: row.variant,
              className: "flex items-center gap-4 flex-wrap"
            },
            (0, import_react2.createElement)("span", { className: labelClass }, row.variant),
            ...["primary", "secondary", "tertiary"].map(
              (priority) => row.priorities.includes(priority) ? (0, import_react2.createElement)(Button, { key: priority, variant: row.variant, priority }, `${row.variant}`) : (0, import_react2.createElement)(
                "span",
                {
                  key: priority,
                  className: `${sectionClass} italic`
                },
                "\u2014"
              )
            )
          )
        )
      ),
      // Section 2: Sizes
      (0, import_react2.createElement)("p", { className: sectionClass }, "Sizes"),
      (0, import_react2.createElement)(
        "div",
        { className: "flex items-center gap-4" },
        (0, import_react2.createElement)(Button, { size: "sm" }, "Small"),
        (0, import_react2.createElement)(Button, { size: "md" }, "Medium")
      ),
      // Section 3: With icons
      (0, import_react2.createElement)("p", { className: sectionClass }, "With icons"),
      (0, import_react2.createElement)(
        "div",
        { className: "flex items-center gap-4 flex-wrap" },
        (0, import_react2.createElement)(Button, { startIcon: (0, import_react2.createElement)(Icon, { name: "plus", size: 16 }) }, "Start icon"),
        (0, import_react2.createElement)(Button, { endIcon: (0, import_react2.createElement)(Icon, { name: "chevron-down", size: 16 }) }, "End icon"),
        (0, import_react2.createElement)(
          Button,
          {
            priority: "secondary",
            startIcon: (0, import_react2.createElement)(Icon, { name: "plus", size: 16 })
          },
          "Add item"
        )
      ),
      // Section 4: States
      (0, import_react2.createElement)("p", { className: sectionClass }, "States"),
      (0, import_react2.createElement)(
        "div",
        { className: "flex items-center gap-4 flex-wrap" },
        (0, import_react2.createElement)(Button, { disabled: true }, "Disabled primary"),
        (0, import_react2.createElement)(Button, { priority: "secondary", disabled: true }, "Disabled secondary"),
        (0, import_react2.createElement)(Button, { priority: "tertiary", disabled: true }, "Disabled tertiary"),
        (0, import_react2.createElement)(Button, { loading: true }, "Loading")
      ),
      // Section 5: fullWidth
      (0, import_react2.createElement)("p", { className: sectionClass }, "Full width"),
      (0, import_react2.createElement)(Button, { fullWidth: true }, "Full width button")
    );
  },
  examples: [
    "<Button>Confirm</Button>",
    '<Button priority="secondary" size="sm">Cancel</Button>',
    '<Button variant="destructive" onClick={handleDelete}>Delete report</Button>',
    '<Button variant="destructive" priority="secondary">Remove</Button>',
    '<Button priority="tertiary">More options</Button>',
    '<Button variant="ia" priority="secondary">AI analysis</Button>',
    '<Button variant="highlight" priority="secondary">Highlight action</Button>',
    '<Button startIcon={<Icon name="plus" size={16} />}>Add item</Button>',
    '<Button endIcon={<Icon name="chevron-down" size={16} />}>Options</Button>',
    "<Button loading>Saving...</Button>",
    "<Button fullWidth>Full width action</Button>",
    '// asChild merges Button styles onto the child element (e.g. a router link)\n<Button asChild>\n  <a href="/reports">Open reports</a>\n</Button>'
  ],
  variants: ["default", "destructive", "ia", "highlight"],
  sizes: ["sm", "md"],
  props: {
    variant: '"default" | "destructive" | "ia" | "highlight"',
    priority: '"primary" | "secondary" | "tertiary"',
    size: '"sm" | "md"',
    asChild: "boolean",
    disabled: "boolean",
    startIcon: "ReactNode",
    endIcon: "ReactNode",
    loading: "boolean",
    fullWidth: "boolean"
  }
};

// src/components/dialog/Dialog.tsx
var React5 = __toESM(require("react"));
var DialogPrimitive = __toESM(require("@radix-ui/react-dialog"));
var import_class_variance_authority4 = require("class-variance-authority");

// src/components/icon-button/IconButton.tsx
var React3 = __toESM(require("react"));
var import_class_variance_authority3 = require("class-variance-authority");
var import_jsx_runtime4 = require("react/jsx-runtime");
var iconButtonVariants = (0, import_class_variance_authority3.cva)(
  [
    "inline-flex items-center justify-center",
    "rounded-sm transition",
    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-focus",
    "disabled:pointer-events-none"
  ],
  {
    variants: {
      variant: {
        default: "focus-visible:ring-filigran-brand-primary",
        destructive: "focus-visible:ring-feedback-error-primary",
        ia: "focus-visible:ring-filigran-ia-secondary"
      },
      priority: {
        primary: "disabled:bg-elevation-disabled disabled:text-default-disabled",
        secondary: ["border", "disabled:text-default-disabled disabled:border-current"],
        tertiary: "disabled:text-default-disabled"
      },
      size: {
        sm: "h-6 w-6",
        md: "h-9 w-9"
      }
    },
    compoundVariants: [
      // default × primary
      {
        variant: "default",
        priority: "primary",
        className: "bg-filigran-brand-primary text-negative-primary hover:bg-filigran-brand-tertiary active:bg-filigran-brand-tertiary"
      },
      // default × secondary
      {
        variant: "default",
        priority: "secondary",
        className: "border-elevation-default text-filigran-brand-primary hover:bg-filigran-brand-primary-transparency active:bg-filigran-brand-primary-transparency"
      },
      // default × tertiary
      {
        variant: "default",
        priority: "tertiary",
        className: "text-filigran-brand-primary hover:bg-filigran-brand-primary-transparency active:bg-filigran-brand-primary-transparency"
      },
      // destructive × primary
      {
        variant: "destructive",
        priority: "primary",
        className: "bg-feedback-error-primary text-negative-primary hover:bg-feedback-error-secondary active:bg-feedback-error-secondary"
      },
      // destructive × secondary
      {
        variant: "destructive",
        priority: "secondary",
        className: "border-feedback-error-secondary text-feedback-error-primary hover:bg-feedback-error-secondary-transparency active:bg-feedback-error-secondary-transparency"
      },
      // destructive × tertiary
      {
        variant: "destructive",
        priority: "tertiary",
        className: "text-feedback-error-primary hover:bg-feedback-error-secondary-transparency active:bg-feedback-error-secondary-transparency"
      },
      // ia × secondary — gradient border
      {
        variant: "ia",
        priority: "secondary",
        className: "border-transparent hover:bg-filigran-ia-secondary-transparency active:bg-filigran-ia-secondary-transparency"
      },
      // ia × tertiary
      {
        variant: "ia",
        priority: "tertiary",
        className: "hover:bg-filigran-ia-secondary-transparency active:bg-filigran-ia-secondary-transparency"
      }
    ],
    defaultVariants: {
      variant: "default",
      priority: "primary",
      size: "md"
    }
  }
);
var IconButton = React3.forwardRef(
  ({ className, variant, priority, size, icon, active, type = "button", ...props }, ref) => {
    const resolvedVariant = variant ?? "default";
    const resolvedPriority = priority ?? "primary";
    const enforcedPriority = resolvedVariant === "ia" && resolvedPriority === "primary" ? "secondary" : resolvedPriority;
    const isGradientType = resolvedVariant === "ia";
    const isGradientBorder = isGradientType && enforcedPriority === "secondary" && !props.disabled;
    return /* @__PURE__ */ (0, import_jsx_runtime4.jsx)(
      "button",
      {
        className: cn(
          iconButtonVariants({ variant, priority: enforcedPriority, size }),
          isGradientBorder && "gradient-border-ia",
          active && (resolvedVariant === "destructive" ? "bg-feedback-error-secondary-transparency" : resolvedVariant === "ia" ? "bg-filigran-ia-secondary-transparency" : "bg-filigran-brand-primary-transparency"),
          className
        ),
        ref,
        type,
        ...active !== void 0 && { "aria-pressed": active },
        ...props,
        children: /* @__PURE__ */ (0, import_jsx_runtime4.jsx)("span", { className: "inline-flex shrink-0", "aria-hidden": "true", children: icon })
      }
    );
  }
);
IconButton.displayName = "IconButton";

// src/components/icon-button/IconButton.meta.ts
var import_react3 = require("react");
var IconButtonMeta = {
  name: "IconButton",
  description: "Square button that renders a single icon with no text label. Requires an accessible aria-label. Shares the variant \xD7 priority \xD7 size pattern with Button. Use for toolbar actions, close/dismiss, and any action where the icon alone is sufficient context (pair with Tooltip for disambiguation).",
  status: "stable",
  version: "0.1.0",
  radixPrimitive: "none",
  figmaLink: "https://www.figma.com/design/0PmhuZzF9XcaIEfMMW2a51/Design_System_2026?node-id=2785-15267&t=tG8r0rUXodx8CTLs-1",
  figmaNodeId: "2785:15267",
  examples: [
    `<IconButton icon={<Icon name="plus" />} aria-label="Add item" />`,
    `<IconButton variant="destructive" priority="secondary" icon={<Icon name="trash-2" />} aria-label="Delete" />`,
    `<IconButton variant="ia" priority="secondary" icon={<Icon name="bot" gradient="ia" />} aria-label="AI assist" />`,
    `<IconButton size="sm" priority="tertiary" icon={<Icon name="x" />} aria-label="Close" />`
  ],
  variants: ["default", "destructive", "ia"],
  sizes: ["sm", "md"],
  props: {
    icon: "ReactNode \u2014 The icon element to render (must be aria-hidden; the button carries the accessible label)",
    variant: '"default" | "destructive" | "ia" \u2014 Visual intent (default: "default")',
    priority: '"primary" | "secondary" | "tertiary" \u2014 Visual weight (default: "primary")',
    size: '"sm" | "md" \u2014 Size: sm=24px, md=36px (default: "md")',
    active: "boolean \u2014 Active/toggle-on state (default: false)",
    "aria-label": "string \u2014 Required accessible label (no visible text)"
  },
  render() {
    const iconEl = (name) => (0, import_react3.createElement)(Icon, { name, size: 24 });
    const iconIaEl = (name) => (0, import_react3.createElement)(Icon, { name, size: 24, gradient: "ia" });
    const iconSmEl = (name) => (0, import_react3.createElement)(Icon, { name, size: 16 });
    const labelClass = "text-content-caption font-content-caption leading-content-caption tracking-content-caption text-default-secondary";
    return (0, import_react3.createElement)(
      "div",
      { className: "flex flex-col gap-6" },
      // Priority: Primary
      (0, import_react3.createElement)(
        "div",
        { className: "flex flex-col gap-2" },
        (0, import_react3.createElement)("span", { className: labelClass }, "Primary"),
        (0, import_react3.createElement)(
          "div",
          { className: "flex items-center gap-4" },
          (0, import_react3.createElement)(IconButton, {
            variant: "default",
            priority: "primary",
            icon: iconEl("plus"),
            "aria-label": "Add"
          }),
          (0, import_react3.createElement)(IconButton, {
            variant: "default",
            priority: "primary",
            icon: iconEl("plus"),
            "aria-label": "Add (disabled)",
            disabled: true
          })
        )
      ),
      // Priority: Secondary
      (0, import_react3.createElement)(
        "div",
        { className: "flex flex-col gap-2" },
        (0, import_react3.createElement)("span", { className: labelClass }, "Secondary"),
        (0, import_react3.createElement)(
          "div",
          { className: "flex items-center gap-4" },
          (0, import_react3.createElement)(IconButton, {
            variant: "default",
            priority: "secondary",
            icon: iconEl("pencil"),
            "aria-label": "Edit"
          }),
          (0, import_react3.createElement)(IconButton, {
            variant: "destructive",
            priority: "secondary",
            icon: iconEl("trash-2"),
            "aria-label": "Delete"
          }),
          (0, import_react3.createElement)(IconButton, {
            variant: "ia",
            priority: "secondary",
            icon: iconIaEl("bot"),
            "aria-label": "AI"
          })
        )
      ),
      // Priority: Tertiary
      (0, import_react3.createElement)(
        "div",
        { className: "flex flex-col gap-2" },
        (0, import_react3.createElement)("span", { className: labelClass }, "Tertiary"),
        (0, import_react3.createElement)(
          "div",
          { className: "flex items-center gap-4" },
          (0, import_react3.createElement)(IconButton, {
            variant: "default",
            priority: "tertiary",
            icon: iconEl("ellipsis-vertical"),
            "aria-label": "More"
          }),
          (0, import_react3.createElement)(IconButton, {
            variant: "destructive",
            priority: "tertiary",
            icon: iconEl("trash-2"),
            "aria-label": "Delete"
          }),
          (0, import_react3.createElement)(IconButton, {
            variant: "ia",
            priority: "tertiary",
            icon: iconIaEl("bot"),
            "aria-label": "AI"
          })
        )
      ),
      // Size: sm
      (0, import_react3.createElement)(
        "div",
        { className: "flex flex-col gap-2" },
        (0, import_react3.createElement)("span", { className: labelClass }, "Small (sm)"),
        (0, import_react3.createElement)(
          "div",
          { className: "flex items-center gap-4" },
          (0, import_react3.createElement)(IconButton, {
            size: "sm",
            variant: "default",
            priority: "primary",
            icon: iconSmEl("plus"),
            "aria-label": "Add"
          }),
          (0, import_react3.createElement)(IconButton, {
            size: "sm",
            variant: "default",
            priority: "secondary",
            icon: iconSmEl("pencil"),
            "aria-label": "Edit"
          }),
          (0, import_react3.createElement)(IconButton, {
            size: "sm",
            variant: "default",
            priority: "tertiary",
            icon: iconSmEl("x"),
            "aria-label": "Close"
          })
        )
      ),
      // Disabled state
      (0, import_react3.createElement)(
        "div",
        { className: "flex flex-col gap-2" },
        (0, import_react3.createElement)("span", { className: labelClass }, "Disabled"),
        (0, import_react3.createElement)(
          "div",
          { className: "flex items-center gap-4" },
          (0, import_react3.createElement)(IconButton, {
            variant: "default",
            priority: "primary",
            disabled: true,
            icon: iconEl("bookmark"),
            "aria-label": "Pinned (disabled)"
          }),
          (0, import_react3.createElement)(IconButton, {
            variant: "default",
            priority: "secondary",
            disabled: true,
            icon: iconEl("bookmark"),
            "aria-label": "Pinned (disabled)"
          }),
          (0, import_react3.createElement)(IconButton, {
            variant: "default",
            priority: "tertiary",
            disabled: true,
            icon: iconEl("bookmark"),
            "aria-label": "Pinned (disabled)"
          })
        )
      )
    );
  },
  accessibility: {
    wcag: "2.1 AA",
    wcagStatus: "pass",
    contrastPairs: [
      {
        id: "primary-icon",
        fg: "--text-negative-primary",
        bg: "--color-filigran-brand-primary",
        minRatio: 4.5
      },
      {
        id: "secondary-icon",
        fg: "--color-filigran-brand-primary",
        bg: "--bg-elevation-default",
        minRatio: 3
      },
      {
        id: "destructive-icon",
        fg: "--color-feedback-error-primary",
        bg: "--bg-elevation-default",
        minRatio: 3
      },
      {
        id: "focus ring",
        fg: "--color-filigran-brand-primary",
        bg: "--bg-elevation-default",
        minRatio: 3
      }
    ],
    notes: "primary dark: --text-negative-primary (#18191b) on --color-filigran-brand-primary (blue-500 #0fbcff) = 8.0:1 \u2705\nprimary light: --text-negative-primary (#f2f2f3) on --color-filigran-brand-primary (darkblue-700 #0015a8) = 11.2:1 \u2705\nsecondary icon dark: --color-filigran-brand-primary (#0fbcff) on grayblue-1000 (#070d18) = 9.2:1 \u2705\nKeyboard: standard button focus ring. aria-label required (no visible text)."
  }
};

// src/components/dialog/Dialog.tsx
var import_jsx_runtime5 = require("react/jsx-runtime");
var Dialog = DialogPrimitive.Root;
var DialogTrigger = DialogPrimitive.Trigger;
var DialogClose = DialogPrimitive.Close;
var DialogOverlay = React5.forwardRef(({ className, ...props }, ref) => /* @__PURE__ */ (0, import_jsx_runtime5.jsx)(
  DialogPrimitive.Overlay,
  {
    ref,
    className: cn(
      "fixed inset-0 z-50",
      // RFC §3.4/§9 arbitration #11 (visual-fix-pass-1 correction, live
      // re-fetch of node 5476:11938 "overlay" inside mise-en-situation
      // 5138:38875 + master component 2879:22353): scrim color is
      // --bg-elevation-default-layer-0, at 80% opacity — confirmed via
      // get_variable_defs (bound var) AND get_design_context (rendered
      // "opacity-80" + screenshot). CORRECTS a bug shipped in the previous
      // pass: "bg-elevation-default-layer-0/80" (a bare custom-utility name
      // + /NN opacity modifier) generates ZERO CSS — verified empirically
      // by compiling this exact class with the Tailwind v4 CLI (`tailwindcss
      // -i src/tokens/index.css`) and finding no matching rule in the
      // output; this is exactly why the overlay was reported "manquant"
      // (missing), not a z-index or logic bug.
      //
      // A same-pass first attempt at fixing this
      // (`bg-[var(--bg-elevation-default-layer-0)]/80`) was ALSO wrong and
      // is called out here rather than silently swapped: `pnpm check:tokens`
      // (scripts/check-token-usage.ts) rejects it — its themeTokens set is
      // built from `theme.base.keys()` (scripts/lib/theme.ts loadTheme()),
      // and loadTheme() merges `:root {}` INTO `base` (not just `@theme {}`
      // as AGENTS.md's own table wording suggests in isolation), so
      // `--bg-elevation-default-layer-0` (declared in :root/.light/.dark)
      // counts as a "theme token" for this gate's purposes too — its
      // `[var(--x)]`-forbidding rule isn't @theme{}-only in practice.
      // Confirmed empirically (`pnpm check:tokens` failure), not just by
      // re-reading the script.
      //
      // Actual fix: the SAME `.layer-N` context-class mechanism used for
      // fix #1 (DialogContent's background, below) — `layer-0` re-points
      // the ambient --bg-elevation-default alias to layer-0's value for
      // this element, then the pre-existing `bg-elevation-default` @utility
      // (a plain, un-modified class — no token suffix, so untouched by the
      // check-token-usage.ts rule above) picks it up. The 80% dimming is
      // then native `opacity-*` on the whole element rather than a
      // color-alpha modifier — safe here because DialogOverlay has no
      // children/border/text of its own for `opacity` to over-affect, so a
      // whole-element opacity and a background-only alpha are visually
      // identical. This also conveniently folds the "80%" target into the
      // SAME classes already doing the open/close fade (below) instead of
      // stacking a second, conflicting opacity utility. All 3 candidates
      // (this one, the two rejected above) were compiled with the Tailwind
      // v4 CLI directly to confirm real output before choosing.
      "layer-0 bg-elevation-default",
      // RESOLVED (2026-07-27, PR #49 merged): --blur-sm (4px) landed in
      // theme.css's native `blur` namespace, generating `backdrop-blur-sm`
      // natively — the `dialog-tokens-consume` blocker is lifted for blur.
      // Value note: this is 4px, NOT the 2px this comment block previously
      // cited from the live Figma re-fetch (node 5476:11938/5476:11958,
      // re-checked again this pass — both STILL render `backdrop-blur-[2px]`
      // raw). Sandy's explicit instruction is to snap to the nearest real
      // system scale step (--blur-sm = 4px) instead of carrying a bespoke,
      // token-less 2px value forward — the same kind of deliberate
      // design-lead override already recorded for the footer gap and
      // close-icon size (Dialog.rfc.md §9 arbitrations #19/#20) — tracked
      // as arbitration #21, which supersedes #11's original 2px figure
      // (see RFC §3.4 for the superseding note). `backdrop-blur-sm` is a
      // real generated utility
      // class (native theme scale, not an arbitrary-value `[2px]`/`[4px]`),
      // so it also finally closes out the AI-BACKLOG "AppShell.tsx —
      // migrate backdrop-blur-[2px]" entry's blocking dependency (that
      // entry's own proposed name `--blur-global-overlay` did not ship —
      // corrected there to point at the real `--blur-sm`).
      "backdrop-blur-sm",
      // Open-state target is opacity-80 (not -100): this element's own
      // background is already fully opaque (see above), so the visible
      // "80%" scrim dimming happens here, on the whole-element opacity,
      // through the exact same classes that drive the enter/exit fade.
      "opacity-0 data-[state=open]:opacity-80 data-[state=closed]:opacity-0",
      "transition-opacity duration-150 motion-reduce:transition-none",
      className
    ),
    ...props
  }
));
DialogOverlay.displayName = DialogPrimitive.Overlay.displayName;
var dialogContentVariants = (0, import_class_variance_authority4.cva)(
  [
    "fixed left-1/2 top-1/2 z-50 -translate-x-1/2 -translate-y-1/2",
    "flex flex-col gap-6",
    // max-h-screen + overflow-hidden bounds the panel to the viewport so
    // DialogBody's own flex-1/overflow-y-auto (below) has real remaining
    // space to compute against and can actually take over scrolling
    // (RFC §3.6/§9 arbitration #10 — internal scroll, header/footer fixed,
    // page never scrolls). `fixed` above already establishes the
    // containing block the close button's `absolute` corner needs — no
    // separate `relative` class required. Native Tailwind utilities only,
    // no custom token needed for either.
    "max-h-screen overflow-hidden",
    // visual-fix-pass-1: panel background must resolve to #13213e — live
    // re-fetch of node 2879:22353 (master "md" component) confirms this via
    // TWO independent MCP calls: get_variable_defs on 2879:22352 returns
    // "var(--bg-elevation-default)":"#13213e", and get_design_context on
    // 2879:22353 shows `bg-[var(--bg-elevation-default,#13213e)]` — Figma's
    // own binding is the alias `--bg-elevation-default`, not a "-layer-2"
    // suffixed name. Confirmed in theme.css: :root/.dark's
    // --bg-elevation-default-layer-2 resolves to var(--grayblue-700) =
    // #13213e exactly (.light's equivalent is var(--gray-50), the correct
    // per-mode counterpart). The `layer-2` class below is the theme.css-
    // authored context class (":root"-sibling, unused elsewhere in this
    // codebase but purpose-built for exactly this) that locally re-points
    // --bg-elevation-default (and its heading/highlight/disabled/border
    // siblings) at the layer-2 values for this subtree — so the existing
    // `bg-elevation-default` utility class name below needs NO change, and
    // Dialog.meta.ts's contrastPairs literal token names (`bg:
    // "--bg-elevation-default"`) also stay unchanged — check-contrast.ts's
    // Rule 5 coverage check matches contrastPairs by the literal CSS custom
    // property name a class resolves through, not by the cascaded runtime
    // value, so the entries are correctly left as-is (see Dialog.meta.ts's
    // notes for the full reasoning and hand-verified real-vs-ambient ratio
    // numbers this cascade implies — no WCAG risk either way). Verified by
    // compiling both classes with the Tailwind v4 CLI directly
    // (`tailwindcss -i src/tokens/index.css`): `.layer-2{...}` and
    // `.bg-elevation-default{background-color:var(--bg-elevation-default)}`
    // both emit real rules. No theme.css edit, no new token — AGENTS.md
    // line 162 forbids agent-authored tokens; this needed none.
    "layer-2",
    "bg-elevation-default text-default-primary rounded-sm shadow-global-shadow p-6",
    "opacity-0 data-[state=open]:opacity-100 data-[state=closed]:opacity-0",
    "transition-opacity duration-150 motion-reduce:transition-none"
  ],
  {
    variants: {
      size: {
        // RESOLVED (2026-07-27, PR #49 merged): the `dialog-tokens-consume`
        // blocker is lifted. Landed tokens are --width-overlay-sm/md/lg/xl
        // (420/640/960/1360px — sm/md/lg match arbitration #4 exactly),
        // native `width` namespace, exposed via Thibault's custom
        // `w-overlay-*` @utility. Mechanism note: this is a FIXED width
        // (`width:`), NOT the max-w-dialog-*/`--container-*` max-width
        // mechanism this comment originally anticipated — Sandy confirmed
        // fixed width is the correct, deliberate choice (no responsive
        // shrink-below-breakpoint behavior wanted), so `w-overlay-*` is
        // used as-is rather than routed through a max-width wrapper.
        sm: "w-overlay-sm",
        md: "w-overlay-md",
        lg: "w-overlay-lg"
      }
    },
    defaultVariants: {
      size: "md"
    }
  }
);
function hasDescendantOfType(node, type) {
  return React5.Children.toArray(node).some((child) => {
    if (!React5.isValidElement(child)) {
      return false;
    }
    if (child.type === type) {
      return true;
    }
    const childProps = child.props;
    return hasDescendantOfType(childProps.children, type);
  });
}
var DialogContent = React5.forwardRef(({ className, size, hideCloseButton = false, children, ...props }, ref) => {
  if (typeof process !== "undefined" && process.env.NODE_ENV !== "production" && !hasDescendantOfType(children, DialogTitle)) {
    console.warn(
      "[Dialog] DialogContent was rendered without a DialogTitle child. The dialog will still open and function, but has no accessible name (WCAG 2.4.6, 4.1.2). Add a DialogTitle \u2014 wrap it in Radix's VisuallyHidden if it shouldn't be shown visually, never omit it."
    );
  }
  return /* @__PURE__ */ (0, import_jsx_runtime5.jsxs)(DialogPrimitive.Portal, { children: [
    /* @__PURE__ */ (0, import_jsx_runtime5.jsx)(DialogOverlay, {}),
    /* @__PURE__ */ (0, import_jsx_runtime5.jsxs)(
      DialogPrimitive.Content,
      {
        ref,
        ...props,
        "aria-modal": "true",
        className: cn(dialogContentVariants({ size }), className),
        children: [
          !hideCloseButton && /* @__PURE__ */ (0, import_jsx_runtime5.jsx)(DialogPrimitive.Close, { asChild: true, children: /* @__PURE__ */ (0, import_jsx_runtime5.jsx)(
            IconButton,
            {
              icon: /* @__PURE__ */ (0, import_jsx_runtime5.jsx)(Icon, { name: "x", size: 24, "aria-hidden": true }),
              "aria-label": "Close",
              priority: "tertiary",
              size: "md",
              className: "absolute top-2.5 right-2.5"
            }
          ) }),
          children
        ]
      }
    )
  ] });
});
DialogContent.displayName = DialogPrimitive.Content.displayName;
var DialogTitle = React5.forwardRef(({ className, ...props }, ref) => /* @__PURE__ */ (0, import_jsx_runtime5.jsx)(
  DialogPrimitive.Title,
  {
    ref,
    className: cn(
      "font-title-sm font-bold text-title-sm leading-title-sm tracking-title-sm",
      "text-default-primary",
      // Unconditional pr-8 (32px, native Tailwind spacing scale) reserves
      // room so a long title doesn't run under the absolute close-icon
      // corner (24px icon + 10px inset). Harmless when hideCloseButton is
      // set (a few px of unused padding on an otherwise short title) —
      // not worth threading that prop across sibling components for a
      // cosmetic edge case Figma doesn't itself demonstrate (RFC §3.6).
      // Flagged for a look during the STOP #2 visual pass.
      "pr-8",
      className
    ),
    ...props
  }
));
DialogTitle.displayName = DialogPrimitive.Title.displayName;
var DialogDescription = React5.forwardRef(({ className, ...props }, ref) => /* @__PURE__ */ (0, import_jsx_runtime5.jsx)(
  DialogPrimitive.Description,
  {
    ref,
    className: cn(
      "font-content-base text-content-base leading-content-base tracking-content-base",
      "text-default-primary",
      className
    ),
    ...props
  }
));
DialogDescription.displayName = DialogPrimitive.Description.displayName;
var DialogBody = React5.forwardRef(
  ({ className, ...props }, ref) => /* @__PURE__ */ (0, import_jsx_runtime5.jsx)("div", { ref, className: cn("flex-1 min-h-0 overflow-y-auto", className), ...props })
);
DialogBody.displayName = "DialogBody";
var DialogFooter = React5.forwardRef(
  ({ className, ...props }, ref) => /* @__PURE__ */ (0, import_jsx_runtime5.jsx)(
    "div",
    {
      ref,
      className: cn("flex items-center justify-end gap-2 w-full", className),
      ...props
    }
  )
);
DialogFooter.displayName = "DialogFooter";

// src/components/dialog/Dialog.meta.ts
var import_react6 = require("react");

// src/components/tabs/Tabs.tsx
var React6 = __toESM(require("react"));
var TabsPrimitive = __toESM(require("@radix-ui/react-tabs"));
var import_jsx_runtime6 = require("react/jsx-runtime");
var Tabs = React6.forwardRef(
  ({ activationMode = "manual", ...props }, forwardedRef) => /* @__PURE__ */ (0, import_jsx_runtime6.jsx)(TabsPrimitive.Root, { ref: forwardedRef, activationMode, ...props })
);
Tabs.displayName = TabsPrimitive.Root.displayName;
function mergeRefs(...refs) {
  return (node) => {
    for (const ref of refs) {
      if (typeof ref === "function") {
        ref(node);
      } else if (ref && typeof ref === "object") {
        ref.current = node;
      }
    }
  };
}
var SCROLL_STEP_RATIO = 0.8;
var TabsList = React6.forwardRef(
  ({ className, actions, children, ...props }, forwardedRef) => {
    const listRef = React6.useRef(null);
    const mergedRef = React6.useMemo(() => mergeRefs(forwardedRef, listRef), [forwardedRef]);
    const leftArrowRef = React6.useRef(null);
    const rightArrowRef = React6.useRef(null);
    const [canScrollLeft, setCanScrollLeft] = React6.useState(false);
    const [canScrollRight, setCanScrollRight] = React6.useState(false);
    const updateScrollState = React6.useCallback(() => {
      const node = listRef.current;
      if (!node) return;
      const { scrollLeft, scrollWidth, clientWidth } = node;
      const nextCanScrollLeft = scrollLeft > 0;
      const nextCanScrollRight = scrollLeft + clientWidth < scrollWidth - 1;
      if (!nextCanScrollLeft && document.activeElement === leftArrowRef.current) {
        node.focus();
      }
      if (!nextCanScrollRight && document.activeElement === rightArrowRef.current) {
        node.focus();
      }
      setCanScrollLeft(nextCanScrollLeft);
      setCanScrollRight(nextCanScrollRight);
    }, []);
    React6.useEffect(() => {
      updateScrollState();
    });
    React6.useEffect(() => {
      const node = listRef.current;
      if (!node) return void 0;
      node.addEventListener("scroll", updateScrollState, { passive: true });
      window.addEventListener("resize", updateScrollState);
      const resizeObserver = typeof ResizeObserver !== "undefined" ? new ResizeObserver(updateScrollState) : void 0;
      resizeObserver?.observe(node);
      return () => {
        node.removeEventListener("scroll", updateScrollState);
        window.removeEventListener("resize", updateScrollState);
        resizeObserver?.disconnect();
      };
    }, [updateScrollState]);
    const scrollByStep = (direction) => {
      const node = listRef.current;
      if (!node) return;
      node.scrollBy({
        left: direction * node.clientWidth * SCROLL_STEP_RATIO,
        behavior: "smooth"
      });
    };
    const arrowButtonClassName = cn(
      "self-center shrink-0 inline-flex items-center justify-center",
      "size-8 rounded-sm transition",
      "text-default-secondary hover:bg-filigran-brand-primary-transparency",
      "focus-visible:outline-none focus-visible:ring-2 ring-focus",
      "focus-visible:ring-offset-2 focus-visible:ring-offset-focus"
    );
    return (
      // Visual bar: border + full width now live here (moved off the
      // tablist below) so the border spans tabs *and* actions. Carries the
      // consumer className — non-regression for existing TabsList usage.
      /* @__PURE__ */ (0, import_jsx_runtime6.jsxs)(
        "div",
        {
          className: cn("flex items-stretch w-full border-b-2 border-elevation-subtle", className),
          children: [
            canScrollLeft && /* @__PURE__ */ (0, import_jsx_runtime6.jsx)(
              "button",
              {
                ref: leftArrowRef,
                type: "button",
                "aria-label": "Scroll tabs left",
                onClick: () => scrollByStep(-1),
                className: arrowButtonClassName,
                children: /* @__PURE__ */ (0, import_jsx_runtime6.jsx)(Icon, { name: "chevron-left", size: 16 })
              }
            ),
            /* @__PURE__ */ (0, import_jsx_runtime6.jsxs)("div", { className: "relative flex-1 min-w-0", children: [
              /* @__PURE__ */ (0, import_jsx_runtime6.jsx)(
                TabsPrimitive.List,
                {
                  ref: mergedRef,
                  className: "flex flex-row items-stretch w-full h-full overflow-x-auto scrollbar-hide scroll-smooth",
                  ...props,
                  children
                }
              ),
              canScrollLeft && /* @__PURE__ */ (0, import_jsx_runtime6.jsx)(
                "div",
                {
                  "aria-hidden": "true",
                  className: "pointer-events-none absolute inset-y-0 left-0 w-8 bg-linear-to-r from-fade-overlay to-transparent"
                }
              ),
              canScrollRight && /* @__PURE__ */ (0, import_jsx_runtime6.jsx)(
                "div",
                {
                  "aria-hidden": "true",
                  className: "pointer-events-none absolute inset-y-0 right-0 w-8 bg-linear-to-l from-fade-overlay to-transparent"
                }
              )
            ] }),
            canScrollRight && /* @__PURE__ */ (0, import_jsx_runtime6.jsx)(
              "button",
              {
                ref: rightArrowRef,
                type: "button",
                "aria-label": "Scroll tabs right",
                onClick: () => scrollByStep(1),
                className: arrowButtonClassName,
                children: /* @__PURE__ */ (0, import_jsx_runtime6.jsx)(Icon, { name: "chevron-right", size: 16 })
              }
            ),
            actions != null && actions !== false && /* @__PURE__ */ (0, import_jsx_runtime6.jsx)("div", { className: "flex items-center gap-2 shrink-0 pl-8", children: actions })
          ]
        }
      )
    );
  }
);
TabsList.displayName = TabsPrimitive.List.displayName;
var badgeColorClassName = {
  brand: "bg-filigran-brand-primary-transparency",
  error: "bg-alert-error"
};
var TabsTrigger = React6.forwardRef(
  ({
    className,
    icon,
    badge,
    badgeColor = "brand",
    badgeMax,
    badgeInvisible = false,
    children,
    asChild = false,
    ...props
  }, ref) => {
    const showBadge = badge !== void 0 && !badgeInvisible;
    const hasValidCap = badgeMax !== void 0 && Number.isFinite(badgeMax) && badgeMax >= 0;
    const displayBadge = typeof badge === "number" && hasValidCap && badge > badgeMax ? `${badgeMax}+` : badge;
    return /* @__PURE__ */ (0, import_jsx_runtime6.jsx)(
      TabsPrimitive.Trigger,
      {
        ref,
        asChild,
        className: cn(
          // Base layout — matches Figma: row, px-4, gap-2, h-11
          "group inline-flex items-center justify-center gap-2",
          "h-11 px-4",
          "rounded-t-sm",
          "transition",
          // Default text: body-base / secondary colour
          "font-content-base text-content-base leading-content-base tracking-content-base",
          "text-default-secondary",
          // Hover (default state)
          "hover:bg-filigran-brand-primary-transparency",
          // Reserve 2px border space in base to prevent height shift when tab activates.
          // Border width matches TabsList so the active indicator overlays the list border.
          "border-b-2 border-transparent",
          // Active state (selected tab) — bottom indicator + bold text + brand colour
          // font-semibold: font-content-button alone only ever resolved font-
          // family (see Text.tsx's file-level comment) — it never actually made
          // the label bold/semibold as this comment always intended. Fixed
          // 2026-07-23 (PR #44 review finding), same gap as Button.tsx.
          "data-[state=active]:font-content-button",
          "data-[state=active]:font-semibold",
          "data-[state=active]:text-filigran-brand-primary",
          "data-[state=active]:border-filigran-brand-primary",
          // Disabled state — both :disabled (native button) and data-[disabled] (Radix asChild mode)
          "disabled:pointer-events-none disabled:text-default-disabled",
          "data-[disabled]:pointer-events-none data-[disabled]:text-default-disabled",
          // Focus ring — standard pattern
          "focus-visible:outline-none focus-visible:ring-2 ring-focus",
          "focus-visible:ring-offset-2 focus-visible:ring-offset-focus",
          className
        ),
        ...props,
        children: asChild ? (
          // Single child required for Slot — icon/badge (and its color/max/
          // invisible variants) are not supported in asChild mode
          children
        ) : /* @__PURE__ */ (0, import_jsx_runtime6.jsxs)(import_jsx_runtime6.Fragment, { children: [
          icon && /* @__PURE__ */ (0, import_jsx_runtime6.jsx)("span", { className: "flex-none w-4 h-4", "aria-hidden": "true", children: icon }),
          children,
          showBadge && /* @__PURE__ */ (0, import_jsx_runtime6.jsx)(
            "span",
            {
              className: cn(
                "inline-flex items-center justify-center",
                "px-1 rounded-sm",
                "font-content-caption text-content-caption leading-content-caption tracking-content-caption",
                "text-default-primary",
                badgeColorClassName[badgeColor],
                "group-data-[disabled]:bg-elevation-disabled group-data-[disabled]:text-default-disabled"
              ),
              "aria-label": `${displayBadge} items`,
              children: displayBadge
            }
          )
        ] })
      }
    );
  }
);
TabsTrigger.displayName = TabsPrimitive.Trigger.displayName;
var TabsContent = React6.forwardRef(({ className, ...props }, ref) => /* @__PURE__ */ (0, import_jsx_runtime6.jsx)(
  TabsPrimitive.Content,
  {
    ref,
    className: cn(
      "focus-visible:outline-none focus-visible:ring-2 ring-focus",
      "focus-visible:ring-offset-2 focus-visible:ring-offset-focus",
      className
    ),
    ...props
  }
));
TabsContent.displayName = TabsPrimitive.Content.displayName;

// src/components/tabs/Tabs.meta.ts
var import_react4 = require("react");
var TabsMeta = {
  name: "Tabs",
  description: "Compound tab navigation component. Organises content into labelled panels; only the active panel is visible at a time. Built on @radix-ui/react-tabs for full keyboard navigation and ARIA semantics.",
  status: "stable",
  version: "0.1.0",
  radixPrimitive: "@radix-ui/react-tabs",
  figmaLink: "https://www.figma.com/design/0PmhuZzF9XcaIEfMMW2a51/Design_System_2026?node-id=2788-20273",
  figmaNodeId: "2788:20273",
  variants: ["active", "default", "disabled"],
  sizes: [],
  examples: [
    '<Tabs defaultValue="overview">\n  <TabsList>\n    <TabsTrigger value="overview">Overview</TabsTrigger>\n    <TabsTrigger value="reports">Reports</TabsTrigger>\n  </TabsList>\n  <TabsContent value="overview">\u2026</TabsContent>\n  <TabsContent value="reports">\u2026</TabsContent>\n</Tabs>',
    '// Icon (decorative, aria-hidden) and count badge on triggers\n<TabsTrigger value="alerts" icon={<Icon name="clock" />} badge={3}>Alerts</TabsTrigger>',
    '// badgeColor "error" (tinted red, WCAG AA in both modes \u2014 never a solid fill),\n// badgeMax caps a numeric badge ("99+"), badgeInvisible hides it conditionally.\n// The badge stays purely decorative in every combination \u2014 never clickable.\n<TabsTrigger value="alerts" badge={failedCount} badgeColor="error" badgeMax={99} badgeInvisible={failedCount === 0}>\n  Alerts\n</TabsTrigger>',
    `// asChild for router links; icon/badge (and badgeColor/badgeMax/badgeInvisible) are
// ignored with asChild. This is also how a *clickable* badge/chip (e.g. OpenCTI's
// EEChip) must be composed \u2014 never by making TabsTrigger's own badge interactive.
<TabsTrigger value="settings" asChild>
  <Link to="/settings">Settings</Link>
</TabsTrigger>`,
    '// actions renders as a DOM sibling of role="tablist", never a child \u2014 right-aligned,\n// 32px minimum gap from the tabs in both the normal and overflow states. On overflow,\n// scroll arrows + a decorative fade appear automatically; actions stay fixed.\n// The 8px gap between actions is guaranteed by the slot itself (its wrapper is a\n// flex container with its own gap) \u2014 pass siblings directly, no extra wrapping div\n// needed. Secondary action(s) first, primary last (closest to the edge).\n<TabsList\n  actions={\n    <>\n      <Button priority="secondary" size="sm">Export</Button>\n      <Button priority="primary" size="sm">New</Button>\n    </>\n  }\n>\n  <TabsTrigger value="overview">Overview</TabsTrigger>\n  <TabsTrigger value="reports">Reports</TabsTrigger>\n</TabsList>'
  ],
  render() {
    const demoIcon = (0, import_react4.createElement)(
      "svg",
      {
        xmlns: "http://www.w3.org/2000/svg",
        viewBox: "0 0 16 16",
        width: 16,
        height: 16,
        fill: "none",
        stroke: "currentColor",
        strokeWidth: 1.5
      },
      (0, import_react4.createElement)("circle", { cx: 8, cy: 8, r: 5.5 }),
      (0, import_react4.createElement)("path", { d: "M8 5.5v3l1.5 1.5", strokeLinecap: "round" })
    );
    const pClass = "pt-4 text-content-base font-content-base leading-content-base tracking-content-base text-default-secondary";
    const labelClass = "text-content-caption font-content-caption leading-content-caption tracking-content-caption text-default-secondary";
    const renderDemoActions = () => [
      (0, import_react4.createElement)(Button, { key: "export", priority: "secondary", size: "sm" }, "Export"),
      (0, import_react4.createElement)(Button, { key: "new", priority: "primary", size: "sm" }, "New")
    ];
    const overflowTriggerLabels = [
      "Overview",
      "Reports",
      "Settings",
      "Alerts",
      "Billing",
      "Members",
      "Integrations",
      "Audit log",
      "Security"
    ];
    return (0, import_react4.createElement)(
      "div",
      { className: "flex flex-col gap-8" },
      // ── Basic: active / default / disabled ────────────────────────────────
      (0, import_react4.createElement)(
        "div",
        { className: "flex flex-col gap-2" },
        (0, import_react4.createElement)("p", { className: labelClass }, "Basic \u2014 active / default / disabled"),
        (0, import_react4.createElement)(
          Tabs,
          { defaultValue: "overview" },
          (0, import_react4.createElement)(
            TabsList,
            null,
            (0, import_react4.createElement)(TabsTrigger, { value: "overview" }, "Overview"),
            (0, import_react4.createElement)(TabsTrigger, { value: "reports" }, "Reports"),
            (0, import_react4.createElement)(TabsTrigger, { value: "settings", disabled: true }, "Settings")
          ),
          (0, import_react4.createElement)(
            TabsContent,
            { value: "overview" },
            (0, import_react4.createElement)(
              "p",
              { className: pClass },
              "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."
            )
          ),
          (0, import_react4.createElement)(
            TabsContent,
            { value: "reports" },
            (0, import_react4.createElement)(
              "p",
              { className: pClass },
              "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur."
            )
          ),
          (0, import_react4.createElement)(
            TabsContent,
            { value: "settings" },
            (0, import_react4.createElement)(
              "p",
              { className: pClass },
              "Sunt in culpa qui officia deserunt mollit anim id est laborum."
            )
          )
        )
      ),
      // ── With icon and badge ───────────────────────────────────────────────
      (0, import_react4.createElement)(
        "div",
        { className: "flex flex-col gap-2" },
        (0, import_react4.createElement)("p", { className: labelClass }, "With icon and badge"),
        (0, import_react4.createElement)(
          Tabs,
          { defaultValue: "overview" },
          (0, import_react4.createElement)(
            TabsList,
            null,
            (0, import_react4.createElement)(TabsTrigger, { value: "overview", icon: demoIcon }, "Overview"),
            (0, import_react4.createElement)(TabsTrigger, { value: "reports", badge: 12 }, "Reports"),
            (0, import_react4.createElement)(TabsTrigger, { value: "alerts", icon: demoIcon, badge: 3 }, "Alerts")
          ),
          (0, import_react4.createElement)(
            TabsContent,
            { value: "overview" },
            (0, import_react4.createElement)("p", { className: pClass }, "Tab with a leading icon (16\xD716, aria-hidden).")
          ),
          (0, import_react4.createElement)(
            TabsContent,
            { value: "reports" },
            (0, import_react4.createElement)("p", { className: pClass }, "Tab with a badge count next to the label.")
          ),
          (0, import_react4.createElement)(
            TabsContent,
            { value: "alerts" },
            (0, import_react4.createElement)("p", { className: pClass }, "Tab with both a leading icon and a badge count.")
          )
        )
      ),
      // ── Badge color, max cap, and invisible state ─────────────────────────
      (0, import_react4.createElement)(
        "div",
        { className: "flex flex-col gap-2" },
        (0, import_react4.createElement)(
          "p",
          { className: labelClass },
          'Badge color (brand / error), max cap ("99+"), and conditional invisible state'
        ),
        (0, import_react4.createElement)(
          Tabs,
          { defaultValue: "overview" },
          (0, import_react4.createElement)(
            TabsList,
            null,
            (0, import_react4.createElement)(TabsTrigger, { value: "overview", badge: 12, badgeColor: "brand" }, "Overview"),
            (0, import_react4.createElement)(TabsTrigger, { value: "failed", badge: 4, badgeColor: "error" }, "Failed"),
            (0, import_react4.createElement)(
              TabsTrigger,
              { value: "reports", badge: 150, badgeMax: 99, badgeColor: "error" },
              "Reports"
            ),
            (0, import_react4.createElement)(
              TabsTrigger,
              // badgeInvisible hides the badge entirely (no residual DOM node) —
              // the same pattern a consumer would drive from a live count reaching 0.
              { value: "settings", badge: 0, badgeInvisible: true },
              "Settings"
            )
          ),
          (0, import_react4.createElement)(
            TabsContent,
            { value: "overview" },
            (0, import_react4.createElement)("p", { className: pClass }, 'Default badgeColor ("brand") \u2014 unchanged visual.')
          ),
          (0, import_react4.createElement)(
            TabsContent,
            { value: "failed" },
            (0, import_react4.createElement)(
              "p",
              { className: pClass },
              'badgeColor="error" \u2014 tinted red background, WCAG AA text contrast in both modes (see accessibility notes below). Purely decorative: never clickable.'
            )
          ),
          (0, import_react4.createElement)(
            TabsContent,
            { value: "reports" },
            (0, import_react4.createElement)(
              "p",
              { className: pClass },
              'badge={150} badgeMax={99} renders "99+" (and aria-label="99+ items") \u2014 the true count is never silently capped unless badgeMax is explicitly set.'
            )
          ),
          (0, import_react4.createElement)(
            TabsContent,
            { value: "settings" },
            (0, import_react4.createElement)(
              "p",
              { className: pClass },
              "badgeInvisible={true} \u2014 the badge renders nothing at all, equivalent to omitting the badge prop."
            )
          )
        )
      ),
      // ── badgeMax boundary and invalid values (PR#56 review fix) ───────────
      // Regression coverage for the review finding: a negative or non-finite
      // badgeMax used to be applied literally (e.g. rendering "-1+"). Live
      // demo, not just unit tests, so the fix is visible without reading code.
      (0, import_react4.createElement)(
        "div",
        { className: "flex flex-col gap-2" },
        (0, import_react4.createElement)(
          "p",
          { className: labelClass },
          "badgeMax boundary (0) and invalid values (negative/NaN/Infinity)"
        ),
        (0, import_react4.createElement)(
          Tabs,
          { defaultValue: "zero-cap" },
          (0, import_react4.createElement)(
            TabsList,
            null,
            (0, import_react4.createElement)(TabsTrigger, { value: "zero-cap", badge: 3, badgeMax: 0 }, "Zero cap"),
            (0, import_react4.createElement)(TabsTrigger, { value: "invalid-cap", badge: 3, badgeMax: -1 }, "Invalid cap")
          ),
          (0, import_react4.createElement)(
            TabsContent,
            { value: "zero-cap" },
            (0, import_react4.createElement)(
              "p",
              { className: pClass },
              'badge={3} badgeMax={0} renders "0+" \u2014 zero is a valid, deliberate cap (not the same as an unset badgeMax), so it is honored like any other non-negative value.'
            )
          ),
          (0, import_react4.createElement)(
            TabsContent,
            { value: "invalid-cap" },
            (0, import_react4.createElement)(
              "p",
              { className: pClass },
              'badge={3} badgeMax={-1} renders "3" (uncapped) \u2014 a negative, NaN, or Infinity badgeMax is nonsensical as a cap, so it is treated the same as an unset badgeMax rather than rendered literally (e.g. never "-1+").'
            )
          )
        )
      ),
      // ── With actions (DOM sibling of the tablist, right-aligned) ──────────
      (0, import_react4.createElement)(
        "div",
        { className: "flex flex-col gap-2" },
        (0, import_react4.createElement)("p", { className: labelClass }, "With actions \u2014 right-aligned, sibling of the tablist"),
        (0, import_react4.createElement)(
          Tabs,
          { defaultValue: "overview" },
          (0, import_react4.createElement)(
            TabsList,
            { actions: renderDemoActions() },
            (0, import_react4.createElement)(TabsTrigger, { value: "overview" }, "Overview"),
            (0, import_react4.createElement)(TabsTrigger, { value: "reports" }, "Reports")
          ),
          (0, import_react4.createElement)(
            TabsContent,
            { value: "overview" },
            (0, import_react4.createElement)(
              "p",
              { className: pClass },
              'Actions render as a DOM sibling of role="tablist" (never a child) \u2014 see the accessibility notes below.'
            )
          ),
          (0, import_react4.createElement)(
            TabsContent,
            { value: "reports" },
            (0, import_react4.createElement)(
              "p",
              { className: pClass },
              "The 32px minimum gap between the last tab and actions holds in both the normal and overflow states."
            )
          )
        )
      ),
      // ── With actions — overflow (narrow container: real, live layout) ─────
      (0, import_react4.createElement)(
        "div",
        { className: "flex flex-col gap-2" },
        (0, import_react4.createElement)(
          "p",
          { className: labelClass },
          "With actions \u2014 overflow (narrow container: scroll + fade + arrows appear automatically; actions stay fixed)"
        ),
        (0, import_react4.createElement)(
          "div",
          { className: "w-80 max-w-full" },
          (0, import_react4.createElement)(
            Tabs,
            { defaultValue: "overview" },
            (0, import_react4.createElement)(
              TabsList,
              { actions: renderDemoActions() },
              overflowTriggerLabels.map(
                (label) => (0, import_react4.createElement)(TabsTrigger, { key: label, value: label.toLowerCase() }, label)
              )
            ),
            overflowTriggerLabels.map(
              (label) => (0, import_react4.createElement)(
                TabsContent,
                { key: label, value: label.toLowerCase() },
                (0, import_react4.createElement)("p", { className: pClass }, `${label} panel.`)
              )
            )
          )
        )
      )
    );
  },
  props: {
    "Tabs.defaultValue": "string \u2014 initially selected tab value (uncontrolled)",
    "Tabs.value": "string \u2014 controlled selected tab value",
    "Tabs.onValueChange": "(value: string) => void \u2014 called when active tab changes",
    "Tabs.orientation": '"horizontal" | "vertical" \u2014 axis of keyboard navigation (default: horizontal)',
    "Tabs.activationMode": `"automatic" | "manual" \u2014 whether tabs activate on focus or on Enter/Space (default: "manual", overriding Radix's own "automatic" default \u2014 aligns with this design system's actual shipped behavior in MUI/OpenCTI; pass activationMode="automatic" to opt back into focus-driven activation).`,
    "TabsList.className": "string \u2014 additional CSS classes for the tab bar",
    "TabsList.actions": "ReactNode \u2014 optional content aligned to the right of the bar, outside the role=tablist (e.g. secondary actions, status).",
    "TabsTrigger.value": "string (required) \u2014 unique key that links trigger to content panel",
    "TabsTrigger.disabled": "boolean \u2014 prevents the tab from being selected",
    "TabsTrigger.asChild": "boolean \u2014 merge trigger styles onto a single child element (e.g. React Router <Link>) instead of rendering a <button>. icon and badge (including badgeColor, badgeMax, badgeInvisible) are ignored when asChild is true \u2014 this is also the supported way to compose a clickable badge/chip (e.g. OpenCTI's EEChip): TabsTrigger's own badge stays purely decorative and is never clickable.",
    "TabsTrigger.icon": "ReactNode \u2014 optional 16\xD716 leading icon (decorative, aria-hidden). Ignored when asChild is true.",
    "TabsTrigger.badge": "number | string \u2014 optional count badge displayed next to the label. Ignored when asChild is true.",
    "TabsTrigger.badgeColor": '"brand" | "error" \u2014 semantic color variant for the badge (default: "brand", visually identical to the pre-existing badge). Purely decorative \u2014 never implies interactivity or clickability. Ignored when asChild is true or badge is undefined.',
    "TabsTrigger.badgeMax": 'number \u2014 caps a numeric badge: values greater than badgeMax render as "${badgeMax}+" (e.g. badge={150} badgeMax={99} \u2192 "99+"). No default (uncapped) \u2014 ignored for string badges and when asChild is true. A negative or non-finite badgeMax (e.g. -1, NaN, Infinity) is treated as uncapped rather than applied literally.',
    "TabsTrigger.badgeInvisible": "boolean \u2014 hides the badge entirely when true (no DOM node rendered at all, not a CSS-only hide), equivalent to omitting badge. Default: false. Ignored when asChild is true.",
    "TabsContent.value": "string (required) \u2014 must match the corresponding TabsTrigger value",
    "TabsContent.forceMount": "boolean \u2014 keeps content in the DOM even when inactive (useful for animations)",
    "TabsContent (usage note)": "TabsContent is optional. When content is managed externally (conditional rendering or CSS display:none), omit TabsContent entirely and render your own panels based on the active value via onValueChange. Radix handles aria-selected and keyboard navigation regardless."
  },
  accessibility: {
    wcag: "2.1 AA",
    wcagStatus: "pass",
    contrastRatios: {
      active: { light: 11.2, dark: 8.89 },
      default: { light: 7.88, dark: 8.97 },
      "badge brand": { light: 12.91, dark: 15.11 },
      "badge error": { light: 11.94, dark: 15.8 }
    },
    contrastPairs: [
      {
        id: "active",
        fg: "--color-filigran-brand-primary",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: "default",
        fg: "--text-default-secondary",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: "focus ring",
        fg: "--color-filigran-brand-primary",
        bg: "--bg-elevation-default",
        minRatio: 3
      },
      {
        id: "badge brand",
        fg: "--text-default-primary",
        bg: "--color-filigran-brand-primary-transparency",
        minRatio: 4.5
      },
      {
        id: "badge error",
        fg: "--text-default-primary",
        bg: "--bg-alert-error",
        minRatio: 4.5
      }
    ],
    notes: "Criteria: 1.1.1 Non-text Content \u2014 decorative icons carry aria-hidden='true'; badge renders an aria-label with count, computed on the displayed (post-badgeMax-cap) value so the accessible name never diverges from what is visually shown; badgeInvisible omits the badge element from the DOM entirely (no residual accessibility-tree node). 1.3.1 Info and Relationships \u2014 Radix provides role='tablist', role='tab', role='tabpanel' and aria-selected/aria-controls/aria-labelledby automatically. 1.4.1 Use of Color \u2014 active tab is differentiated by a bottom indicator border (shape) AND heavier font weight AND brand colour; not colour alone. 1.4.1 Use of Color (overflow) \u2014 the truncation fade is a decorative cue only; overflow is also conveyed by the focusable scroll arrows and by the affected tab being visibly clipped at the scroll edge, so gradient/colour is never the sole indicator. 1.4.3 Contrast (Minimum) \u2014 all text combinations \u2265 4.5:1 in both modes (verified below). 1.4.11 Non-text Contrast \u2014 focus ring \u2265 3:1 against page background in both modes. The TabsList bottom border uses border-elevation-subtle (decorative separator, not a functional UI component boundary) \u2014 exempt from WCAG 1.4.11 per SC 1.4.11 Note 1. TabsList.actions (and the scroll-arrow buttons / fade overlays rendered only while the tablist overflows) are DOM siblings of role='tablist', never children \u2014 ARIA's aria-required-children restricts a tablist's children to role='tab' elements, so any other descendant would misreport the accessible tree to assistive technology. Known limitation: actions use standard Tab order; toolbar roving-tabindex pattern deferred \u2014 see AI-BACKLOG. 2.1.1 Keyboard \u2014 Arrow keys move focus between tabs without activating them (manual activation is the default \u2014 see Tabs.activationMode); Enter/Space activates the focused tab; Tab moves to panel; Shift+Tab returns to trigger. 2.1.1 Keyboard (actions) \u2014 with actions present, Tab order is: active trigger \u2192 scroll-right arrow (only while overflowing) \u2192 actions \u2192 panel; scroll-left/right buttons are native <button> elements with aria-label='Scroll tabs left'/'Scroll tabs right', focusable and Enter/Space-activatable like any button (see Tabs.keyboard.test.tsx). 2.4.7 Focus Visible \u2014 standard ring-2 ring-focus pattern on all triggers and content panels. 4.1.2 Name, Role, Value \u2014 Radix manages aria-selected, aria-controls, aria-labelledby; disabled tab carries HTML disabled attribute. 4.1.2 Name, Role, Value (badge) \u2014 the badge (both colors, capped or not) is purely decorative and never interactive: no click handler, no role='button', no tabIndex, in any badgeColor/badgeMax/badgeInvisible combination. A clickable badge/chip (e.g. OpenCTI's EEChip) must be composed above TabsTrigger via asChild, never by making this badge interactive. 508 \xA71194.21(a) \u2014 all tab functions operable via keyboard. 508 \xA71194.21(c) \u2014 focus indication always visible via focus ring. Active tab text dark: #0fbcff on #070d18 = 8.89:1 \u2705 (\u2265 4.5:1). Active tab text light: #0015a8 on #f2f2f3 = 11.2:1 \u2705 (\u2265 4.5:1). Default tab text dark: #afb0b6 on #070d18 = 8.97:1 \u2705 (\u2265 4.5:1). Default tab text light: #494a50 on #f2f2f3 = 7.88:1 \u2705 (\u2265 4.5:1). Badge brand (bg-filigran-brand-primary-transparency, --color-filigran-brand-primary-transparency composited over --bg-elevation-default) text dark: #f2f2f3 on #081f2f = 15.11:1 \u2705 (\u2265 4.5:1). Badge brand text light: #18191b on #dadcec = 12.91:1 \u2705 (\u2265 4.5:1). Badge error (bg-alert-error, resolving to --bg-alert-error \u2014 implemented via the identical --color-feedback-error-secondary-transparency formula, composited over --bg-elevation-default) text dark: #f2f2f3 on #2e0e13 = 15.80:1 \u2705 (\u2265 4.5:1). Badge error text light: #18191b on #f3ccc9 = 11.94:1 \u2705 (\u2265 4.5:1). A tinted/transparent background was chosen deliberately over a solid fill, for consistency with the existing brand badge and the design system's established bg-alert-* 30%-opacity pattern (info/success/alert/warning/error all share the same recipe) \u2014 not to work around a Button-specific issue: Button's destructive variant (solid bg-feedback-error-primary) currently passes WCAG AA (4.69:1 dark, 5.92:1 light, see Button.meta.ts / pnpm check:contrast) after its red primitives were hardened by the Button RFC rework (PR #37). Overflow fade composite (fade painted over tab text, contrasted against the page background \u2014 worst case is the fade's 20%-opacity 'from' stop nearest the scroll edge): default dark #afb0b6 + 20% fade \u2192 #8d8f96 on #070d18 = 6.05:1 \u2705 (\u2265 4.5:1). Default light #494a50 + 20% fade \u2192 #6b6c71 on #f2f2f3 = 4.71:1 \u2705 (\u2265 4.5:1, worst case \u2014 20% opacity was chosen because default/light tolerates at most 21.83% before failing 4.5:1). Active dark #0fbcff + 20% fade \u2192 #0d99d1 on #070d18 = 6.01:1 \u2705 (\u2265 4.5:1). Active light #0015a8 + 20% fade \u2192 #3041b7 on #f2f2f3 = 7.30:1 \u2705 (\u2265 4.5:1). Known limitation: the fade uses from-fade-overlay (a dedicated @utility applying 20% of --bg-elevation-default-layer-0 via color-mix) \u2014 it cannot be expressed as a contrastPairs entry / verified by pnpm check:contrast. Re-verify these numbers by hand if the fade's color or opacity ever changes. Disabled tab text: exempt per WCAG 1.4.3 Note 1 (inactive UI component). Focus ring dark: #0fbcff on #070d18 = 8.89:1 \u2705 (\u2265 3:1). Focus ring light: #0015a8 on #f2f2f3 = 11.2:1 \u2705 (\u2265 3:1). List border (decorative separator): exempt from WCAG 1.4.11. Automated checks cover ~half of WCAG \u2014 human/screen-reader audit cadence still applies per release."
  }
};

// src/components/text/Text.tsx
var React7 = __toESM(require("react"));
var import_class_variance_authority5 = require("class-variance-authority");
var import_jsx_runtime7 = require("react/jsx-runtime");
var textVariants = (0, import_class_variance_authority5.cva)("", {
  variants: {
    variant: {
      "title-2xl": "font-title-2xl font-medium text-title-2xl leading-title-2xl tracking-title-2xl",
      "title-xl": "font-title-xl font-medium text-title-xl leading-title-xl tracking-title-xl",
      "title-lg": "font-title-lg font-medium text-title-lg leading-title-lg tracking-title-lg",
      "title-md": "font-title-md font-medium text-title-md leading-title-md tracking-title-md",
      "title-sm": "font-title-sm font-bold text-title-sm leading-title-sm tracking-title-sm",
      "title-xs": "font-title-xs font-semibold text-title-xs leading-title-xs tracking-title-xs",
      "title-jumbo": "font-title-jumbo font-medium text-title-jumbo leading-title-jumbo tracking-title-jumbo",
      "content-base": "font-content-base text-content-base leading-content-base tracking-content-base",
      "content-base-bold": "font-content-base-bold font-semibold text-content-base-bold leading-content-base-bold tracking-content-base-bold",
      "content-base-medium": "font-content-base-medium font-medium text-content-base-medium leading-content-base-medium tracking-content-base-medium",
      // The two -link variants render <a> by default (TEXT_DEFAULT_TAG) and
      // are the only case where Text's own default element is genuinely
      // interactive/focusable (a real <a href>) — see RFC §5.4/§6.3. The
      // standard focus-ring pattern (AGENTS.md "Interactive Elements") is
      // included here; it is inert via :focus-visible when the consumer
      // renders a non-navigable, href-less anchor (RFC §4.1 link-variant
      // note) or overrides `as` to a non-focusable element.
      "content-base-link": "font-content-base-link font-medium text-content-base-link leading-content-base-link tracking-content-base-link underline focus-visible:outline-none focus-visible:ring-2 ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-focus",
      "content-compact": "font-content-compact text-content-compact leading-content-compact tracking-content-compact",
      "content-compact-bold": "font-content-compact-bold font-semibold text-content-compact-bold leading-content-compact-bold tracking-content-compact-bold",
      "content-compact-medium": "font-content-compact-medium font-medium text-content-compact-medium leading-content-compact-medium tracking-content-compact-medium",
      "content-compact-link": "font-content-compact-link font-medium text-content-compact-link leading-content-compact-link tracking-content-compact-link underline focus-visible:outline-none focus-visible:ring-2 ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-focus",
      "content-caption": "font-content-caption text-content-caption leading-content-caption tracking-content-caption",
      "content-highlight": "font-content-highlight font-semibold text-content-highlight leading-content-highlight tracking-content-highlight",
      "content-button": "font-content-button font-semibold text-content-button leading-content-button tracking-content-button",
      "content-code": "font-content-code text-content-code leading-content-code tracking-content-code"
    }
  }
});
var TEXT_DEFAULT_TAG = {
  "title-2xl": "h1",
  "title-xl": "h2",
  "title-lg": "h3",
  "title-md": "h4",
  "title-sm": "h5",
  "title-xs": "h6",
  "title-jumbo": "p",
  "content-base": "p",
  "content-base-bold": "p",
  "content-base-medium": "p",
  "content-base-link": "a",
  "content-compact": "p",
  "content-compact-bold": "p",
  "content-compact-medium": "p",
  "content-compact-link": "a",
  "content-caption": "span",
  "content-highlight": "p",
  "content-button": "span",
  "content-code": "code"
};
var TextRenderFn = React7.forwardRef(
  ({ as, variant, className, children, ...props }, ref) => {
    const Component = as ?? TEXT_DEFAULT_TAG[variant];
    return /* @__PURE__ */ (0, import_jsx_runtime7.jsx)(Component, { ref, className: cn(textVariants({ variant }), className), ...props, children });
  }
);
var Text2 = TextRenderFn;
Text2.displayName = "Text";

// src/components/text/Text.meta.ts
var import_react5 = require("react");
var TextMeta = {
  name: "Text",
  description: 'The design system\'s typography primitive. Renders any of the 19 arbitrated Named Styles (process/TYPO-MAPPING.md, theme.css) while deliberately decoupling the HTML tag from the visual style: `as` picks the semantic element (h1, p, span, label, a...), `variant` picks the Named Style \u2014 never both at once. Also the primary migration vehicle for MUI <Typography variant="X"> usage across products (see Text.rfc.md \xA74.4 for the full crosswalk).',
  status: "stable",
  version: "0.1.0",
  radixPrimitive: "none",
  figmaLink: "https://www.figma.com/design/0PmhuZzF9XcaIEfMMW2a51/Design_System_2026?node-id=2671-1684",
  figmaNodeId: "2671:1684",
  variants: [
    "title-2xl",
    "title-xl",
    "title-lg",
    "title-md",
    "title-sm",
    "title-xs",
    "title-jumbo",
    "content-base",
    "content-base-bold",
    "content-base-medium",
    "content-base-link",
    "content-compact",
    "content-compact-bold",
    "content-compact-medium",
    "content-compact-link",
    "content-caption",
    "content-highlight",
    "content-button",
    "content-code"
  ],
  sizes: [],
  examples: [
    '// Section title \u2014 as defaults to h3 for title-lg\n<Text variant="title-lg">Threat overview</Text>',
    '// Same visual style, different semantics \u2014 as/variant never merge\n<Text as="div" variant="title-lg">Card header, not a heading</Text>',
    '// Standard body paragraph (as defaults to p)\n<Text variant="content-base">Lorem ipsum dolor sit amet.</Text>',
    '// Inline link \u2014 as defaults to a; underline + focus ring included in the variant\n<Text as="a" href="/reports" variant="content-base-link">\n  View full report\n</Text>',
    '// className is the sole styling escape hatch (color, spacing, alignment...)\n<Text variant="content-compact" className="text-default-secondary">\n  Last updated 2 minutes ago\n</Text>',
    '// MUI migration \u2014 variant="h3" component="div" becomes:\n<Text as="div" variant="title-lg">Widget title</Text>'
  ],
  render() {
    const label = (text) => (0, import_react5.createElement)(
      "p",
      {
        className: "text-content-caption font-content-caption leading-content-caption tracking-content-caption text-default-secondary"
      },
      text
    );
    const stack = (children) => (0, import_react5.createElement)("div", { className: "flex flex-col gap-3" }, ...children);
    const sample = (variant, text) => (0, import_react5.createElement)(Text2, { key: variant, variant, children: text });
    const linkSample = (variant, text) => (0, import_react5.createElement)(Text2, { key: variant, variant, as: "a", href: "#", children: text });
    return (0, import_react5.createElement)(
      "div",
      { className: "flex flex-col gap-8" },
      (0, import_react5.createElement)(
        "div",
        { className: "flex flex-col gap-2" },
        label("Titles \u2014 heading ladder (as defaults to h1\u2013h6)"),
        stack([
          sample("title-2xl", "Title 2XL \u2014 page title (h1)"),
          sample("title-xl", "Title XL (h2)"),
          sample("title-lg", "Title LG (h3)"),
          sample("title-md", "Title MD (h4)"),
          sample("title-sm", "Title SM (h5)"),
          sample("title-xs", "Title XS (h6)")
        ])
      ),
      (0, import_react5.createElement)(
        "div",
        { className: "flex flex-col gap-2" },
        label("Title jumbo \u2014 KPI/counter display (as defaults to p, not a heading)"),
        stack([sample("title-jumbo", "1 284")])
      ),
      (0, import_react5.createElement)(
        "div",
        { className: "flex flex-col gap-2" },
        label("Content \u2014 base (14px) and compact (12px), weight variants"),
        stack([
          sample("content-base", "Content base \u2014 regular body text (p)"),
          sample("content-base-medium", "Content base medium"),
          sample("content-base-bold", "Content base bold"),
          sample("content-compact", "Content compact \u2014 secondary/dense body text (p)"),
          sample("content-compact-medium", "Content compact medium"),
          sample("content-compact-bold", "Content compact bold")
        ])
      ),
      (0, import_react5.createElement)(
        "div",
        { className: "flex flex-col gap-2" },
        label("Content \u2014 links (underline + focus ring, as defaults to a)"),
        stack([
          linkSample("content-base-link", "Content base link \u2014 inline navigable text"),
          linkSample("content-compact-link", "Content compact link")
        ])
      ),
      (0, import_react5.createElement)(
        "div",
        { className: "flex flex-col gap-2" },
        label("Content \u2014 caption, highlight, button, code"),
        stack([
          sample("content-caption", "Content caption \u2014 annotation/legend text (span)"),
          sample("content-highlight", "Content highlight \u2014 emphasized lead text (p)"),
          sample("content-button", "Content button \u2014 reserved for the Button component (span)"),
          sample("content-code", "Content code \u2014 inline code sample (code)")
        ])
      )
    );
  },
  props: {
    as: 'React.ElementType (optional) \u2014 the native HTML tag to render (e.g. "h1", "p", "span", "label", "a"). Purely semantic, never affects the visual style. Defaults to a sensible tag per variant when omitted (TEXT_DEFAULT_TAG export; full rationale table in Text.rfc.md \xA74.1).',
    variant: "TextVariant (required) \u2014 the Named Style to apply, sourced from theme.css / process/TYPO-MAPPING.md. No implicit default: MUI's own silent body1 default was found to cause ambiguity in 11% of real usages (Text.rfc.md \xA72.4).",
    className: "string (optional) \u2014 extra classes merged via tailwind-merge. The sole styling escape hatch in v1 scope: color, alignment, truncation, spacing all flow through here instead of dedicated props (Text.rfc.md \xA77 Out of scope).",
    children: "React.ReactNode (required) \u2014 the text content."
  },
  accessibility: {
    wcag: "2.1 AA",
    wcagStatus: "pass",
    contrastPairs: [
      {
        id: "body text (primary) on default elevation",
        fg: "--text-default-primary",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: "secondary body text on default elevation",
        fg: "--text-default-secondary",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: "large heading (primary) on default elevation",
        fg: "--text-default-primary",
        bg: "--bg-elevation-default",
        minRatio: 3
      }
    ],
    notes: "Criteria: 1.3.1 Info and Relationships \u2014 `as` renders the real native element (h1\u2013h6, p, span, label, a, code) with no ARIA role substitution, so the document outline and text semantics are exactly what the markup says; `variant` never influences this (Text.rfc.md \xABArbitrage\xBB). 1.4.3 Contrast (Minimum) \u2014 Text sets no default color of its own by design; color flows through className, chosen per usage. The pairs above document the common/recommended token combinations exercised in render(): normal-size content-* variants at 4.5:1, and large (\u226524px) title-2xl/xl/lg headings at the WCAG large-text allowance of 3:1. 2.4.7 Focus Visible / 2.1.1 Keyboard \u2014 Text adds no keyboard handling of its own. The two link variants (content-base-link, content-compact-link) default to <a> and include the standard focus-ring utility classes, active whenever the consumer supplies href (or overrides as to another focusable element); all other variants are static, non-interactive text with no focus behaviour. wcagStatus 'pass' basis: axe-core conformity suite reports 0 violations across every variant in both modes (render() surface), and all three declared contrast pairs are computed from tokens in CI (pnpm check:contrast) rather than hand-entered. Automated checks cover roughly half of WCAG \u2014 human/screen-reader audit cadence still applies per release."
  }
};

// src/components/dialog/Dialog.meta.ts
var DialogMeta = {
  name: "Dialog",
  description: "Modal overlay that interrupts the current context to request confirmation, collect input, or surface focused content. Built on @radix-ui/react-dialog for accessible behavior (focus trap, Escape/backdrop dismiss, focus return to trigger). Compound component: Dialog > DialogTrigger + DialogContent > DialogTitle + DialogDescription? + DialogBody + DialogFooter. Scroll is internal to DialogBody \u2014 the title, description, and footer stay fixed while the body scrolls.",
  status: "beta",
  version: "0.1.0",
  radixPrimitive: "@radix-ui/react-dialog",
  figmaLink: "https://www.figma.com/design/0PmhuZzF9XcaIEfMMW2a51/Design_System_2026?node-id=2879-22352",
  figmaNodeId: "2879:22352",
  examples: [
    `<Dialog>
  <DialogTrigger asChild>
    <Button priority="primary">Delete indicator</Button>
  </DialogTrigger>
  <DialogContent size="sm">
    <DialogTitle>Delete this indicator?</DialogTitle>
    <DialogDescription>This action cannot be undone.</DialogDescription>
    <DialogFooter>
      <DialogClose asChild>
        <Button priority="secondary">Cancel</Button>
      </DialogClose>
      <Button variant="destructive" priority="primary">Delete</Button>
    </DialogFooter>
  </DialogContent>
</Dialog>`,
    `// size defaults to "md" \u2014 long or dynamic content goes in DialogBody so it
// scrolls internally while the title/footer stay fixed (arbitration #10)
<Dialog>
  <DialogTrigger asChild>
    <Button priority="primary">Create label</Button>
  </DialogTrigger>
  <DialogContent>
    <DialogTitle>Create label</DialogTitle>
    <DialogDescription>Labels help you organize and filter entities.</DialogDescription>
    <DialogBody>
      {/* form fields, lists, or any long-form content */}
    </DialogBody>
    <DialogFooter>
      <DialogClose asChild>
        <Button priority="secondary">Cancel</Button>
      </DialogClose>
      <Button priority="primary">Save</Button>
    </DialogFooter>
  </DialogContent>
</Dialog>`,
    `// DialogDescription is optional \u2014 omitting it is valid, not a contract
// violation (only a missing DialogTitle triggers FDS's dev warning)
<DialogContent size="lg" hideCloseButton>
  <DialogTitle>Import STIX bundle</DialogTitle>
  <DialogBody>\u2026</DialogBody>
  <DialogFooter>
    <DialogClose asChild>
      <Button priority="secondary">Back</Button>
    </DialogClose>
    <Button priority="primary">Continue</Button>
  </DialogFooter>
</DialogContent>`
  ],
  variants: ["default"],
  sizes: ["sm", "md", "lg"],
  props: {
    "Dialog.open": "boolean \u2014 Controlled open state (Radix Root prop)",
    "Dialog.onOpenChange": "(open: boolean) => void \u2014 Fires on any open-state change (backdrop, Escape, trigger, close)",
    "Dialog.defaultOpen": "boolean \u2014 Uncontrolled initial open state (default: false)",
    "DialogContent.size": `"sm" | "md" | "lg" \u2014 Panel width, matches Figma's 3 designed sizes (default: "md")`,
    "DialogContent.hideCloseButton": "boolean \u2014 Hides the built-in corner close icon (default: false \u2014 visible, conforms to Figma)",
    "DialogTitle.children": "ReactNode \u2014 Required for accessibility (dialog name, WCAG 2.4.6/4.1.2). FDS warns in dev if omitted.",
    "DialogDescription.children": "ReactNode \u2014 Optional short subtitle. Not for arbitrary body content \u2014 use DialogBody for that.",
    "DialogBody.children": "ReactNode \u2014 The scrollable middle region. Title/Description/Footer stay fixed; only DialogBody scrolls when content overflows.",
    "DialogFooter.children": "ReactNode \u2014 Action row, typically Button instances (Cancel + primary/destructive action).",
    "DialogTrigger.asChild / DialogClose.asChild": "boolean \u2014 Radix convention: render the single child element directly instead of wrapping it (e.g. to use a Button as the trigger)."
  },
  render() {
    const captionColorClass = "text-default-secondary";
    const longBody = Array.from(
      { length: 12 },
      (_, i) => (0, import_react6.createElement)(Text2, {
        key: i,
        variant: "content-base",
        className: "mb-4",
        children: `Paragraph ${i + 1}. Lorem ipsum dolor sit amet, consectetur adipiscing elit. This is placeholder body copy used to demonstrate that only the middle region scrolls while the title and footer stay fixed in place.`
      })
    );
    return (0, import_react6.createElement)(
      Tabs,
      { defaultValue: "sm", className: "flex flex-col gap-4" },
      (0, import_react6.createElement)(
        TabsList,
        null,
        (0, import_react6.createElement)(TabsTrigger, { value: "sm" }, "Small"),
        (0, import_react6.createElement)(TabsTrigger, { value: "md" }, "Medium (default)"),
        (0, import_react6.createElement)(TabsTrigger, { value: "lg" }, "Large"),
        (0, import_react6.createElement)(TabsTrigger, { value: "scroll" }, "Long content"),
        (0, import_react6.createElement)(TabsTrigger, { value: "no-description" }, "No description"),
        (0, import_react6.createElement)(TabsTrigger, { value: "hidden-close" }, "Hidden close button")
      ),
      // ── Small: destructive-confirmation scenario, the most common
      // real-usage pattern found in product code (§2.4). Footer buttons
      // homogenized to Cancel/Next Step (visual-fix-pass-3) so the 6 demo
      // tabs render an identical footer pair while scrolling — only the
      // trigger label and title/description keep the per-demo scenario ───
      (0, import_react6.createElement)(
        TabsContent,
        { value: "sm", className: "flex flex-col gap-2 py-4" },
        (0, import_react6.createElement)(Text2, {
          variant: "content-caption",
          className: captionColorClass,
          children: 'size="sm" \u2014 destructive confirmation'
        }),
        (0, import_react6.createElement)(
          Dialog,
          null,
          (0, import_react6.createElement)(
            DialogTrigger,
            { asChild: true },
            (0, import_react6.createElement)(Button, { priority: "primary" }, "Delete indicator")
          ),
          (0, import_react6.createElement)(
            DialogContent,
            { size: "sm" },
            (0, import_react6.createElement)(DialogTitle, null, "Delete this indicator?"),
            (0, import_react6.createElement)(DialogDescription, null, "This action cannot be undone."),
            (0, import_react6.createElement)(
              DialogFooter,
              null,
              (0, import_react6.createElement)(DialogClose, { asChild: true }, (0, import_react6.createElement)(Button, { priority: "secondary" }, "Cancel")),
              (0, import_react6.createElement)(Button, { priority: "primary" }, "Next Step")
            )
          )
        )
      ),
      // ── Medium (default): form-style dialog with a DialogBody ──────────
      (0, import_react6.createElement)(
        TabsContent,
        { value: "md", className: "flex flex-col gap-2 py-4" },
        (0, import_react6.createElement)(Text2, {
          variant: "content-caption",
          className: captionColorClass,
          children: 'size="md" (default) \u2014 form-style with DialogBody'
        }),
        (0, import_react6.createElement)(
          Dialog,
          null,
          (0, import_react6.createElement)(DialogTrigger, { asChild: true }, (0, import_react6.createElement)(Button, { priority: "primary" }, "Create label")),
          (0, import_react6.createElement)(
            DialogContent,
            null,
            (0, import_react6.createElement)(DialogTitle, null, "Create label"),
            (0, import_react6.createElement)(
              DialogDescription,
              null,
              "Labels help you organize and filter entities across the platform."
            ),
            (0, import_react6.createElement)(
              DialogBody,
              null,
              (0, import_react6.createElement)(Text2, {
                variant: "content-base",
                children: "Form fields would go here in real usage."
              })
            ),
            (0, import_react6.createElement)(
              DialogFooter,
              null,
              (0, import_react6.createElement)(DialogClose, { asChild: true }, (0, import_react6.createElement)(Button, { priority: "secondary" }, "Cancel")),
              (0, import_react6.createElement)(Button, { priority: "primary" }, "Next Step")
            )
          )
        )
      ),
      // ── Large: richer multi-step-looking content ────────────────────────
      (0, import_react6.createElement)(
        TabsContent,
        { value: "lg", className: "flex flex-col gap-2 py-4" },
        (0, import_react6.createElement)(Text2, {
          variant: "content-caption",
          className: captionColorClass,
          children: 'size="lg" \u2014 richer content'
        }),
        (0, import_react6.createElement)(
          Dialog,
          null,
          (0, import_react6.createElement)(
            DialogTrigger,
            { asChild: true },
            (0, import_react6.createElement)(Button, { priority: "primary" }, "Import STIX bundle")
          ),
          (0, import_react6.createElement)(
            DialogContent,
            { size: "lg" },
            (0, import_react6.createElement)(DialogTitle, null, "Import STIX bundle"),
            (0, import_react6.createElement)(
              DialogDescription,
              null,
              "Upload a STIX 2.1 bundle to import indicators, observables, and relationships."
            ),
            (0, import_react6.createElement)(
              DialogBody,
              null,
              (0, import_react6.createElement)(Text2, {
                variant: "content-base",
                children: "File picker and import options would go here."
              })
            ),
            (0, import_react6.createElement)(
              DialogFooter,
              null,
              (0, import_react6.createElement)(DialogClose, { asChild: true }, (0, import_react6.createElement)(Button, { priority: "secondary" }, "Cancel")),
              (0, import_react6.createElement)(Button, { priority: "primary" }, "Next Step")
            )
          )
        )
      ),
      // ── Long content: demonstrates internal scroll (arbitration #10) —
      // title and footer stay fixed, only DialogBody scrolls. Footer
      // homogenized to Cancel/Next Step (visual-fix-pass-3): a 2nd
      // (primary) button was added here — this tab previously shipped a
      // single dismiss-only "Close" button, which was the one structural
      // outlier vs. the other 5 tabs' Cancel+action pair ─────────────────
      (0, import_react6.createElement)(
        TabsContent,
        { value: "scroll", className: "flex flex-col gap-2 py-4" },
        (0, import_react6.createElement)(Text2, {
          variant: "content-caption",
          className: captionColorClass,
          children: "Long content \u2014 header/footer fixed, DialogBody scrolls internally"
        }),
        (0, import_react6.createElement)(
          Dialog,
          null,
          (0, import_react6.createElement)(DialogTrigger, { asChild: true }, (0, import_react6.createElement)(Button, { priority: "primary" }, "View changelog")),
          (0, import_react6.createElement)(
            DialogContent,
            null,
            (0, import_react6.createElement)(DialogTitle, null, "Changelog"),
            (0, import_react6.createElement)(DialogDescription, null, "Recent platform updates."),
            (0, import_react6.createElement)(DialogBody, null, ...longBody),
            (0, import_react6.createElement)(
              DialogFooter,
              null,
              (0, import_react6.createElement)(DialogClose, { asChild: true }, (0, import_react6.createElement)(Button, { priority: "secondary" }, "Cancel")),
              (0, import_react6.createElement)(Button, { priority: "primary" }, "Next Step")
            )
          )
        )
      ),
      // ── No description: DialogDescription is optional, this is a valid,
      // non-warned state (only a missing DialogTitle warns) ──────────────
      (0, import_react6.createElement)(
        TabsContent,
        { value: "no-description", className: "flex flex-col gap-2 py-4" },
        (0, import_react6.createElement)(Text2, {
          variant: "content-caption",
          className: captionColorClass,
          children: "No DialogDescription \u2014 valid, optional"
        }),
        (0, import_react6.createElement)(
          Dialog,
          null,
          (0, import_react6.createElement)(DialogTrigger, { asChild: true }, (0, import_react6.createElement)(Button, { priority: "primary" }, "Sign out")),
          (0, import_react6.createElement)(
            DialogContent,
            { size: "sm" },
            (0, import_react6.createElement)(DialogTitle, null, "Sign out?"),
            (0, import_react6.createElement)(
              DialogFooter,
              null,
              (0, import_react6.createElement)(DialogClose, { asChild: true }, (0, import_react6.createElement)(Button, { priority: "secondary" }, "Cancel")),
              (0, import_react6.createElement)(Button, { priority: "primary" }, "Next Step")
            )
          )
        )
      ),
      // ── Hidden close button: opt-out via hideCloseButton, e.g. a
      // step-flow dialog where footer actions are the only way out ───────
      (0, import_react6.createElement)(
        TabsContent,
        { value: "hidden-close", className: "flex flex-col gap-2 py-4" },
        (0, import_react6.createElement)(Text2, {
          variant: "content-caption",
          className: captionColorClass,
          children: "hideCloseButton \u2014 footer actions are the only way out"
        }),
        (0, import_react6.createElement)(
          Dialog,
          null,
          (0, import_react6.createElement)(DialogTrigger, { asChild: true }, (0, import_react6.createElement)(Button, { priority: "primary" }, "Start setup")),
          (0, import_react6.createElement)(
            DialogContent,
            { hideCloseButton: true },
            (0, import_react6.createElement)(DialogTitle, null, "Step 1 of 3 \u2014 Connect a source"),
            (0, import_react6.createElement)(
              DialogDescription,
              null,
              "This step is required. Use the buttons below to continue or cancel."
            ),
            (0, import_react6.createElement)(
              DialogFooter,
              null,
              (0, import_react6.createElement)(DialogClose, { asChild: true }, (0, import_react6.createElement)(Button, { priority: "secondary" }, "Cancel")),
              (0, import_react6.createElement)(Button, { priority: "primary" }, "Next Step")
            )
          )
        )
      )
    );
  },
  accessibility: {
    wcag: "2.1 AA",
    wcagStatus: "pass",
    // Confirmed by the real `pnpm check:contrast` run (not hand-declared —
    // CODE-REVIEW-LEARNINGS.md: never let wcagStatus contradict the actual
    // gate). All 6 pairs below use existing theme.css tokens (RFC §5.4) and
    // computed ✅ in both light and dark mode (report: reports/contrast-report.md).
    // Width/blur (§3.3/§3.4) landed in PR #49 (arbitration #21) and only ever
    // affected non-text layout properties, never these text/icon pairs.
    contrastPairs: [
      {
        id: "Dialog title (panel)",
        fg: "--text-default-primary",
        // Kept as the literal "--bg-elevation-default" (NOT "-layer-2") —
        // this is the actual CSS custom property the `bg-elevation-default`
        // utility class (still present on DialogContent) resolves through,
        // and check-contrast.ts's Rule 5 coverage check matches
        // contrastPairs entries by that literal property name (see
        // scripts/check-contrast.ts declaredTokens/resolveColorToken — a
        // static, per-class-name scan of the .tsx source, with no notion of
        // the .layer-2 context class added by fix #1). Real/reported ratio
        // numbers documented in `notes` below — see there for why they
        // differ and why both still comfortably pass.
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: "Dialog content (panel)",
        fg: "--text-default-primary",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: "Dialog close icon (panel)",
        fg: "--color-filigran-brand-primary",
        bg: "--bg-elevation-default",
        minRatio: 3
      },
      {
        id: 'Dialog footer "Cancel" (panel)',
        fg: "--color-filigran-brand-primary",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: 'Dialog footer "Confirm" (panel)',
        fg: "--text-negative-primary",
        // Unaffected by the .layer-2 context class either way — Button's
        // primary priority uses --color-filigran-brand-primary directly
        // (its own brand-fixed background, not one of the elevation-family
        // aliases .layer-2 re-points — confirmed against theme.css's own
        // .layer-2 block, which only lists *-elevation-* aliases).
        bg: "--color-filigran-brand-primary",
        minRatio: 4.5
      },
      {
        id: "Dialog panel text (overlay-composited context)",
        fg: "--text-default-primary",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      }
    ],
    notes: "RFC \xA75.4. The overlay/backdrop itself (--bg-elevation-default-layer-0 at 80% opacity + 4px blur, --blur-sm, landed PR #49 \u2014 superseded from the originally-arbitrated 2px, see RFC \xA79 arbitration #21) is intentionally NOT a contrastPairs entry \u2014 it's a non-text dimming scrim, not a text/UI-component contrast requirement (RFC \xA75.4 final row, 'n/a \u2014 not a text contrast pair'), consistent with how decorative overlays are treated across WCAG 1.4.3/1.4.11. 'Dialog panel text (overlay-composited context)' duplicates the title/content pair deliberately, matching NavbarSubmenu.meta.ts's precedent of listing the same token pair once per distinct rendering context \u2014 DialogContent's own background is fully opaque, so the ratio is identical to the plain-panel entries; the separate entry documents that the pairing was verified in that context too, not just assumed.\nvisual-fix-pass-1 (background fix): DialogContent now renders inside the pre-existing `.layer-2` context class (theme.css) so that its `bg-elevation-default` utility resolves to #13213e/--gray-50 (dark/light) instead of the un-layered default #070d18/#f2f2f3 \u2014 see Dialog.tsx for the full reasoning. All contrastPairs `bg` fields above intentionally stay literal `--bg-elevation-default` (not `-layer-2`) because that's the actual CSS custom property the rendered class resolves through, and check-contrast.ts's Rule 5 coverage check matches contrastPairs by that literal name, with no notion of the `.layer-2` class's cascade \u2014 a known blind spot of the static checker, not something this file can route around without breaking Rule 5 coverage. Concretely this means the ratios `pnpm check:contrast` computes/reports use the AMBIENT (layer-0) color, not the real rendered (layer-2) one. Hand-verified with the exact same WCAG 2.x formula (theme.ts's relativeLuminance/contrastRatio) against the real layer-2 colors so this isn't just asserted: title/content/close-icon-bg/Cancel-bg/overlay-context pairs are dark 14.28:1 (real, layer-2 #13213e) vs 17.38:1 (ambient #070d18 as gate-reported) for the text-default-primary pairs (\u22654.5 either way), and light 16.02:1 (real, layer-2 #f4f4f6) vs 15.72:1 (ambient #f2f2f3 as gate-reported, also \u22654.5); the brand-primary-on-panel pairs (close icon, Cancel) are dark 7.35:1 (real) vs 8.94:1 (ambient, \u22653 and \u22654.5 either way) and light 11.43:1 (real) vs 11.22:1 (ambient, same). No WCAG risk either way \u2014 this is a reporting-precision note, not a compliance gap.\nKeyboard: focus trapped inside the panel while open (Tab/Shift+Tab cycle); Escape closes and returns focus to DialogTrigger; backdrop click closes (Radix defaults, no deviation \u2014 arbitration #7/#8). role=\"dialog\" aria-modal=\"true\", aria-labelledby wired to DialogTitle automatically by Radix; aria-describedby wired to DialogDescription when present, silently omitted when absent (verified empirically against the installed @radix-ui/react-dialog@1.1.22 \u2014 no warning either way, \xA75.3)."
  }
};

// src/components/input/Input.tsx
var React8 = __toESM(require("react"));
var import_class_variance_authority6 = require("class-variance-authority");
var import_jsx_runtime8 = require("react/jsx-runtime");
var inputVariants = (0, import_class_variance_authority6.cva)(
  [
    // Figma's own resting box uses asymmetric padding (16px/8px) — the 8px
    // right edge is only enough for text, no icon; hasEndSlot widens it.
    "h-9 w-full rounded-sm pl-4 pr-2",
    "bg-input-default text-input-placeholder",
    "font-sans-plex font-normal text-3 leading-normal",
    "placeholder:text-input-placeholder",
    "border transition outline-none",
    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-focus",
    "disabled:bg-input-disabled disabled:text-input-disabled disabled:pointer-events-none",
    // `disabled:text-input-disabled` above only recolors the ELEMENT's own text
    // (the typed value) — `::placeholder` is a separate box that never inherits
    // color from a `:disabled` rule on its host once any rule targets the
    // pseudo-element directly (which line 15's unconditional
    // `placeholder:text-input-placeholder` already does). Without this line the
    // placeholder stayed on `--text-input-placeholder` even while disabled
    // (review: PR #54 round 5). The combined `disabled:placeholder:` variant
    // compiles to a selector requiring BOTH `:disabled` AND `::placeholder`
    // (`.disabled\:placeholder\:text-input-disabled:disabled::placeholder`),
    // which is structurally higher-specificity than the plain
    // `::placeholder`-only rule above — so it wins the cascade deterministically,
    // regardless of Tailwind's internal rule emission order. Proven against the
    // real compiled CSS in scripts/lib/input-disabled-placeholder.test.ts.
    "disabled:placeholder:text-input-disabled"
  ],
  {
    variants: {
      error: {
        true: "border-input-error hover:border-input-error",
        false: "border-transparent hover:border-input-hover"
      },
      hasStartIcon: {
        true: "pl-9",
        false: ""
      },
      hasEndSlot: {
        true: "pr-9",
        false: ""
      }
    },
    defaultVariants: {
      error: false,
      hasStartIcon: false,
      hasEndSlot: false
    }
  }
);
var Input = React8.forwardRef(
  ({
    className,
    id,
    type = "text",
    value,
    defaultValue: defaultValue2,
    onChange,
    label,
    required = false,
    infoTooltip,
    placeholder,
    helperText,
    error,
    success = false,
    maxLength,
    startIcon,
    endIcon,
    disabled = false,
    ...props
  }, ref) => {
    const generatedId = React8.useId();
    const inputId = id ?? generatedId;
    const helperId = `${inputId}-helper`;
    if (typeof process !== "undefined" && process.env.NODE_ENV !== "production" && !label && !props["aria-label"] && !props["aria-labelledby"]) {
      console.warn(
        "[Input] rendered without a `label` prop, an `aria-label`, or an `aria-labelledby`. The field will still render and function, but has no accessible name (WCAG 1.3.1, 4.1.2). Add a visible `label`, an `aria-label`, or an `aria-labelledby`."
      );
    }
    const [uncontrolledLength, setUncontrolledLength] = React8.useState(
      () => (defaultValue2 ?? "").length
    );
    const isControlled = value !== void 0;
    const currentLength = isControlled ? value.length : uncontrolledLength;
    const handleChange = (event) => {
      if (!isControlled) setUncontrolledLength(event.target.value.length);
      onChange?.(event);
    };
    const showErrorIcon = Boolean(error);
    const showSuccessIcon = success && !showErrorIcon;
    const hasStateIcon = showErrorIcon || showSuccessIcon;
    const hasEndSlot = hasStateIcon || Boolean(endIcon);
    const helperMessage = error ?? helperText;
    const showHelperRow = Boolean(helperMessage) || maxLength !== void 0;
    const mergedDescribedBy = showHelperRow ? [props["aria-describedby"], helperId].filter(Boolean).join(" ") : props["aria-describedby"];
    return /* @__PURE__ */ (0, import_jsx_runtime8.jsxs)("div", { className: cn("flex w-full flex-col gap-2", className), children: [
      label && /* @__PURE__ */ (0, import_jsx_runtime8.jsxs)("div", { className: "flex items-center gap-1", children: [
        /* @__PURE__ */ (0, import_jsx_runtime8.jsx)(
          "label",
          {
            htmlFor: inputId,
            className: cn(
              "font-sans-plex font-medium text-2",
              disabled ? "text-input-disabled" : error ? "text-input-error" : "text-input-label"
            ),
            children: label
          }
        ),
        required && /* @__PURE__ */ (0, import_jsx_runtime8.jsx)(
          "span",
          {
            "aria-hidden": "true",
            className: cn(
              "font-sans-plex font-normal text-3",
              disabled ? "text-input-disabled" : error ? "text-input-error" : "text-input-required"
            ),
            children: "*"
          }
        ),
        infoTooltip && /* @__PURE__ */ (0, import_jsx_runtime8.jsx)("span", { className: "inline-flex items-center", children: infoTooltip })
      ] }),
      /* @__PURE__ */ (0, import_jsx_runtime8.jsxs)("div", { className: "relative w-full", children: [
        startIcon && /* @__PURE__ */ (0, import_jsx_runtime8.jsx)(
          "span",
          {
            "aria-hidden": "true",
            className: cn(
              "pointer-events-none absolute inset-y-0 left-0 flex items-center pl-4",
              disabled ? "text-input-disabled" : "text-input-placeholder"
            ),
            children: startIcon
          }
        ),
        /* @__PURE__ */ (0, import_jsx_runtime8.jsx)(
          "input",
          {
            ref,
            id: inputId,
            type,
            ...value !== void 0 ? { value } : { defaultValue: defaultValue2 },
            onChange: handleChange,
            placeholder,
            disabled,
            required,
            maxLength,
            className: inputVariants({
              error: showErrorIcon,
              hasStartIcon: Boolean(startIcon),
              hasEndSlot
            }),
            ...props,
            "aria-required": required || void 0,
            "aria-invalid": showErrorIcon || void 0,
            "aria-describedby": mergedDescribedBy || void 0
          }
        ),
        showErrorIcon && /* @__PURE__ */ (0, import_jsx_runtime8.jsx)(
          "span",
          {
            "aria-hidden": "true",
            className: "pointer-events-none absolute inset-y-0 right-0 flex items-center pr-4 text-input-error",
            children: /* @__PURE__ */ (0, import_jsx_runtime8.jsx)(Icon, { name: "circle-alert", size: 16, "aria-hidden": true })
          }
        ),
        showSuccessIcon && /* @__PURE__ */ (0, import_jsx_runtime8.jsx)(
          "span",
          {
            "aria-hidden": "true",
            className: "pointer-events-none absolute inset-y-0 right-0 flex items-center pr-4 text-feedback-success-primary",
            children: /* @__PURE__ */ (0, import_jsx_runtime8.jsx)(Icon, { name: "circle-check", size: 16, "aria-hidden": true })
          }
        ),
        !hasStateIcon && endIcon && endIcon.type === "iconButton" && /* @__PURE__ */ (0, import_jsx_runtime8.jsx)("span", { className: "absolute inset-y-0 right-0 flex items-center pr-2", children: /* @__PURE__ */ (0, import_jsx_runtime8.jsx)(
          IconButton,
          {
            icon: endIcon.icon,
            "aria-label": endIcon.label,
            onClick: endIcon.onClick,
            disabled,
            variant: "default",
            priority: "tertiary",
            size: "sm"
          }
        ) }),
        !hasStateIcon && endIcon && endIcon.type === "icon" && /* @__PURE__ */ (0, import_jsx_runtime8.jsx)(
          "span",
          {
            "aria-hidden": "true",
            className: cn(
              "pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2",
              disabled ? "text-input-disabled" : "text-input-placeholder"
            ),
            children: endIcon.icon
          }
        )
      ] }),
      showHelperRow && /* @__PURE__ */ (0, import_jsx_runtime8.jsxs)("div", { id: helperId, className: "flex items-start justify-between gap-2", children: [
        helperMessage && /* @__PURE__ */ (0, import_jsx_runtime8.jsx)(
          "span",
          {
            className: cn(
              "font-sans-plex font-normal text-1",
              disabled ? "text-input-disabled" : error ? "text-input-error" : "text-input-helper"
            ),
            children: helperMessage
          }
        ),
        maxLength !== void 0 && /* @__PURE__ */ (0, import_jsx_runtime8.jsxs)(
          "span",
          {
            className: cn(
              "font-sans-plex font-normal text-1 shrink-0",
              disabled ? "text-input-disabled" : error ? "text-input-error" : "text-input-helper"
            ),
            children: [
              currentLength,
              "/",
              maxLength
            ]
          }
        )
      ] })
    ] });
  }
);
Input.displayName = "Input";

// src/components/input/Input.meta.ts
var React11 = __toESM(require("react"));

// src/components/tooltip/Tooltip.tsx
var React9 = __toESM(require("react"));
var TooltipPrimitive = __toESM(require("@radix-ui/react-tooltip"));
var import_jsx_runtime9 = require("react/jsx-runtime");
var TooltipProvider = TooltipPrimitive.Provider;
var Tooltip = TooltipPrimitive.Root;
var TooltipTrigger = TooltipPrimitive.Trigger;
var TooltipContent = React9.forwardRef(({ className, sideOffset = 8, ...props }, ref) => /* @__PURE__ */ (0, import_jsx_runtime9.jsx)(TooltipPrimitive.Portal, { children: /* @__PURE__ */ (0, import_jsx_runtime9.jsx)(
  TooltipPrimitive.Content,
  {
    ref,
    sideOffset,
    className: cn(
      "z-50 max-w-75 p-2 rounded-sm break-words",
      "bg-tooltip text-default-primary shadow-global-shadow",
      "text-content-compact font-content-compact leading-content-compact tracking-content-compact",
      "opacity-0 transition-opacity duration-150",
      "data-[state=delayed-open]:opacity-100 data-[state=instant-open]:opacity-100 data-[state=closed]:opacity-0",
      className
    ),
    ...props
  }
) }));
TooltipContent.displayName = TooltipPrimitive.Content.displayName;

// src/components/tooltip/Tooltip.meta.ts
var import_react7 = require("react");
var TooltipMeta = {
  name: "Tooltip",
  description: "Contextual popup that displays informational text when a trigger element is hovered or focused. Built on @radix-ui/react-tooltip for accessible behavior (delay, dismiss on Escape, screen reader announcements). Compound component: wrap content in TooltipProvider > Tooltip > TooltipTrigger + TooltipContent.",
  status: "stable",
  version: "0.1.0",
  radixPrimitive: "@radix-ui/react-tooltip",
  figmaLink: "https://www.figma.com/design/0PmhuZzF9XcaIEfMMW2a51/Design_System_2026?node-id=2777-2937&t=tG8r0rUXodx8CTLs-1",
  figmaNodeId: "2777:2937",
  examples: [
    `<TooltipProvider>
  <Tooltip>
    <TooltipTrigger>Hover me</TooltipTrigger>
    <TooltipContent>
      Lorem ipsum dolor sit amet
    </TooltipContent>
  </Tooltip>
</TooltipProvider>`,
    `<TooltipProvider>
  <Tooltip>
    <TooltipTrigger asChild>
      <button>Action</button>
    </TooltipTrigger>
    <TooltipContent side="right">
      Lorem ipsum dolor sit amet
    </TooltipContent>
  </Tooltip>
</TooltipProvider>`
  ],
  variants: ["default"],
  sizes: [],
  props: {
    "TooltipContent.content": "ReactNode \u2014 Content displayed inside the tooltip popup (children of TooltipContent)",
    "TooltipContent.side": '"top" | "right" | "bottom" | "left" \u2014 Preferred side of the trigger (default: "top")',
    "TooltipContent.sideOffset": "number \u2014 Distance in px between trigger and tooltip (default: 8)",
    "TooltipProvider.delayDuration": "number \u2014 Ms from pointer enter until tooltip opens (default: 700, set on TooltipProvider)"
  },
  render() {
    const triggerClass = "inline-flex items-center justify-center rounded-md border border-elevation-default px-4 py-2 text-content-compact font-content-compact leading-content-compact tracking-content-compact text-default-primary focus-visible:outline-none focus-visible:ring-2 ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-focus";
    const labelClass = "text-content-caption font-content-caption leading-content-caption tracking-content-caption text-default-secondary";
    const questionIcon = (0, import_react7.createElement)(Icon, {
      name: "circle-question-mark",
      size: 16,
      "aria-hidden": true
    });
    return (0, import_react7.createElement)(
      TooltipProvider,
      { delayDuration: 0 },
      (0, import_react7.createElement)(
        "div",
        { className: "flex flex-col gap-6 py-4" },
        // Row 1: Placements
        (0, import_react7.createElement)(
          "div",
          { className: "flex flex-col gap-2" },
          (0, import_react7.createElement)("span", { className: labelClass }, "Placements"),
          (0, import_react7.createElement)(
            "div",
            { className: "flex items-center gap-6 py-4" },
            (0, import_react7.createElement)(
              Tooltip,
              null,
              (0, import_react7.createElement)(TooltipTrigger, { className: triggerClass }, "Left"),
              (0, import_react7.createElement)(TooltipContent, { side: "left" }, "Left placement")
            ),
            (0, import_react7.createElement)(
              Tooltip,
              null,
              (0, import_react7.createElement)(TooltipTrigger, { className: triggerClass }, "Top"),
              (0, import_react7.createElement)(TooltipContent, { side: "top" }, "Top placement")
            ),
            (0, import_react7.createElement)(
              Tooltip,
              null,
              (0, import_react7.createElement)(TooltipTrigger, { className: triggerClass }, "Bottom"),
              (0, import_react7.createElement)(TooltipContent, { side: "bottom" }, "Bottom placement")
            ),
            (0, import_react7.createElement)(
              Tooltip,
              null,
              (0, import_react7.createElement)(TooltipTrigger, { className: triggerClass }, "Right"),
              (0, import_react7.createElement)(TooltipContent, { side: "right" }, "Right placement")
            )
          )
        ),
        // Row 2: Icon trigger (no visible label)
        (0, import_react7.createElement)(
          "div",
          { className: "flex flex-col gap-2" },
          (0, import_react7.createElement)("span", { className: labelClass }, "Icon trigger (no visible label)"),
          (0, import_react7.createElement)(
            "div",
            { className: "flex items-center gap-4 py-4" },
            (0, import_react7.createElement)(
              Tooltip,
              null,
              (0, import_react7.createElement)(
                TooltipTrigger,
                {
                  asChild: true
                },
                (0, import_react7.createElement)(
                  "button",
                  {
                    type: "button",
                    "aria-label": "API token details",
                    className: "inline-flex items-center justify-center rounded-md p-2 text-default-primary hover:bg-elevation-highlight focus-visible:outline-none focus-visible:ring-2 ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-focus"
                  },
                  questionIcon
                )
              ),
              (0, import_react7.createElement)(TooltipContent, { side: "right" }, "Show API token details")
            )
          )
        ),
        // Row 3: Jargon definition
        (0, import_react7.createElement)(
          "div",
          { className: "flex flex-col gap-2" },
          (0, import_react7.createElement)("span", { className: labelClass }, "Jargon definition"),
          (0, import_react7.createElement)(
            "div",
            { className: "flex items-center gap-4 py-4" },
            (0, import_react7.createElement)(
              Tooltip,
              null,
              (0, import_react7.createElement)(
                TooltipTrigger,
                { asChild: true },
                (0, import_react7.createElement)(
                  "span",
                  {
                    tabIndex: 0,
                    className: "text-content-compact font-content-compact leading-content-compact tracking-content-compact text-default-primary border-b border-dashed border-elevation-default cursor-help focus-visible:outline-none focus-visible:ring-2 ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-focus"
                  },
                  "TLP"
                )
              ),
              (0, import_react7.createElement)(
                TooltipContent,
                { side: "top" },
                "TLP indicates sharing restrictions for this intelligence."
              )
            )
          )
        ),
        // Row 4: Truncated text
        (0, import_react7.createElement)(
          "div",
          { className: "flex flex-col gap-2" },
          (0, import_react7.createElement)("span", { className: labelClass }, "Truncated text with full content"),
          (0, import_react7.createElement)(
            "div",
            { className: "flex items-center gap-4 py-4" },
            (0, import_react7.createElement)(
              Tooltip,
              null,
              (0, import_react7.createElement)(
                TooltipTrigger,
                { asChild: true },
                (0, import_react7.createElement)(
                  "span",
                  {
                    tabIndex: 0,
                    className: "truncate max-w-45 inline-block text-content-compact font-content-compact leading-content-compact tracking-content-compact text-default-primary focus-visible:outline-none focus-visible:ring-2 ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-focus"
                  },
                  "This is the complete value for the truncated cell content."
                )
              ),
              (0, import_react7.createElement)(
                TooltipContent,
                { side: "top" },
                "This is the complete value for the truncated cell content."
              )
            )
          )
        )
      )
    );
  },
  accessibility: {
    wcag: "2.1 AA",
    wcagStatus: "pass",
    contrastPairs: [
      {
        id: "content",
        fg: "--text-default-primary",
        bg: "--border-elevation-subtle",
        minRatio: 4.5
      }
    ],
    notes: "content dark: --text-default-primary (gray-100 #f2f2f3) on --border-elevation-subtle (grayblue-500 #1f3965) = 8.6:1 \u2705\ncontent light: --text-default-primary (gray-900 #18191b) on --border-elevation-subtle (gray-200 #cacbce) = 10.2:1 \u2705\nKeyboard: tooltip opens on trigger focus, closes on Escape. Role=tooltip with aria-describedby on the trigger (handled by Radix)."
  }
};

// src/components/input/Input.meta.ts
var InputMeta = {
  name: "Input",
  description: 'Base single-line text field for forms. Standalone component built on a native <input> with integrated label, required marker, info tooltip trigger, helper/error text, optional character-limit counter (tied to maxLength), an optional leading startIcon (decorative), and an optional trailing endIcon slot (decorative icon or interactive IconButton \u2014 password toggle, copy, attach, clear). Covers MUI TextField and @filigran/ui Input. Split from the former "Inputs" umbrella into its own dedicated entry \u2014 does not cover Select, FormControl or InputLabel.',
  status: "beta",
  version: "0.1.0",
  radixPrimitive: "none",
  figmaLink: "https://www.figma.com/design/0PmhuZzF9XcaIEfMMW2a51/Design_System_2026?node-id=2682-4369",
  figmaNodeId: "2682:4369",
  examples: [
    `<Input label="Name" placeholder="Jane Doe" onChange={(e) => console.log(e.target.value)} />`,
    `<Input label="Email" type="email" required helperText="We'll never share your email" />`,
    `<Input
  label="API key"
  infoTooltip={
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <IconButton
            icon={<Icon name="info" size={16} className="text-feedback-info-primary" />}
            aria-label="What is this?"
            variant="default"
            priority="tertiary"
            size="sm"
          />
        </TooltipTrigger>
        <TooltipContent>Found in Settings \u2192 API access</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  }
/>`,
    `<Input
  label="Password"
  type={showPassword ? "text" : "password"}
  endIcon={{
    type: "iconButton",
    icon: <Icon name={showPassword ? "eye-off" : "eye"} size={16} />,
    label: showPassword ? "Hide password" : "Show password",
    onClick: () => setShowPassword((v) => !v),
  }}
/>`,
    `<Input
  label="Invite link"
  defaultValue="https://filigran.io/i/ab12cd"
  endIcon={{
    type: "iconButton",
    icon: <Icon name="clipboard-list" size={16} />,
    label: "Copy to clipboard",
    onClick: () => navigator.clipboard.writeText("https://filigran.io/i/ab12cd"),
  }}
/>`,
    `<Input label="Bio" maxLength={120} defaultValue="Threat intel analyst" helperText="Shown on your public profile" />`,
    `<Input label="Username" startIcon={<Icon name="user" size={16} />} placeholder="username" />`,
    `<Input label="Display name" error="This name is already taken" defaultValue="admin" />`,
    `<Input label="Workspace slug" success defaultValue="filigran-prod" />`,
    `<Input label="Organization" disabled defaultValue="Filigran" />`,
    `<Input aria-label="Search unlabeled" placeholder="No visible label \u2014 aria-label required" />`
  ],
  variants: ["default", "error", "success", "disabled"],
  sizes: ["md"],
  props: {
    type: '"text" | "password" | "number" | "email" \u2014 Input type (default: "text")',
    value: "string \u2014 Controlled value",
    defaultValue: "string \u2014 Default value (uncontrolled)",
    onChange: "(event: ChangeEvent<HTMLInputElement>) => void \u2014 Fires on each keystroke",
    label: "string \u2014 Visible label above the field. Omit \u2192 aria-label or aria-labelledby required",
    required: "boolean \u2014 Sets aria-required always; renders the `*` marker only when `label` is also set, since the marker is label-row anatomy (default: false)",
    infoTooltip: "ReactNode \u2014 Info trigger shown next to the label (caller supplies icon/tooltip and its own color/aria-label); only renders when `label` is set, for the same reason",
    placeholder: "string \u2014 Placeholder shown when empty",
    helperText: "ReactNode \u2014 Helper/description text below the field; replaced by `error` when set",
    error: "string \u2014 Error message. String only (no boolean) \u2014 sets aria-invalid and error tokens",
    success: "boolean \u2014 Shows the check-circle indicator (ignored while `error` is set) (default: false)",
    maxLength: 'number \u2014 Character-limit counter, shown as "N/max" in the helper row, also enforced natively',
    startIcon: "ReactNode \u2014 Single leading decorative icon, always aria-hidden (no interactive variant)",
    endIcon: '{ type: "iconButton"; icon: ReactNode; onClick: React.MouseEventHandler<HTMLButtonElement>; label: string } | { type: "icon"; icon: ReactNode } \u2014 Trailing slot; iconButton composes the FDS IconButton (needs an accessible label), icon is decorative only. Masked by the state icon in error/success.',
    disabled: "boolean \u2014 Disabled state (default: false)",
    className: "string \u2014 Additional CSS classes on the outer wrapper"
  },
  accessibility: {
    wcag: "2.1 AA",
    wcagStatus: "fail",
    contrastPairs: [
      {
        id: "input-text",
        fg: "--text-input-placeholder",
        bg: "--bg-input-default",
        minRatio: 4.5
      },
      {
        id: "label-text",
        fg: "--text-input-label",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: "helper-text",
        fg: "--text-input-helper",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: "required-mark",
        fg: "--text-input-required",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: "error-text",
        fg: "--text-input-error",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: "error-border",
        fg: "--border-input-error",
        bg: "--bg-input-default",
        minRatio: 3
      },
      {
        id: "error-icon",
        fg: "--text-input-error",
        bg: "--bg-input-default",
        minRatio: 3
      },
      {
        id: "success-icon",
        fg: "--color-feedback-success-primary",
        bg: "--bg-input-default",
        minRatio: 3
      },
      {
        id: "placeholder-disabled",
        fg: "--text-input-disabled",
        bg: "--bg-input-disabled",
        minRatio: 4.5
      },
      {
        id: "counter-error",
        fg: "--text-input-error",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: "hover-border",
        fg: "--border-input-hover",
        bg: "--bg-input-default",
        minRatio: 3
      },
      {
        id: "focus-ring",
        fg: "--color-filigran-brand-primary",
        bg: "--bg-elevation-default",
        minRatio: 3
      }
    ],
    notes: `Native <input>, ref forwards directly to it (no wrapper). label uses htmlFor/id association; omitting label requires an aria-label or aria-labelledby \u2014 enforced in development with a console.warn (never a throw) if all three are missing, per AGENTS.md's prop contract violation pattern (same mechanism as Dialog's missing-title check). required sets both the visual * and aria-required. error sets aria-invalid and links the message via aria-describedby; helperText is linked the same way \u2014 a consumer-provided aria-describedby is merged with (never replaced by) this internal link (space-separated, per WAI-ARIA's multi-id support), while aria-invalid/aria-required stay component-managed and always win over a same-named consumer prop (review: PR #54 round 3). The trailing state icon (error/success) is decorative (aria-hidden) \u2014 meaning is carried by text + aria-invalid, not color/icon alone (WCAG 1.4.1). startIcon is always decorative and aria-hidden. endIcon type="iconButton" composes the FDS IconButton and requires an accessible label; type="icon" is decorative. Focus uses the standard FDS ring (ring-focus + ring-offset-focus), matching the Figma Focus state exactly (box-shadow ring in Figma). Disabled uses native disabled (removed from tab order); its low-contrast tokens are exempt under WCAG 1.4.3 Note 1. Known limitation: the success check-circle icon (--color-feedback-success-primary, --green-600 #17ab1f, Figma's own literal value \u2014 verified live, not invented) on --bg-input-default (#e4e5e7 light) = 2.42:1 \u2014 fails the 3:1 AA non-text threshold (WCAG 1.4.11) in light mode only (dark mode passes at 5.23:1). No FDS token fixes this without either deviating from Figma's literal color or editing theme.css (forbidden for agents per AGENTS.md) \u2014 there is no dedicated --*-input-success-* family mirroring --text-input-error/--border-input-error to fall back to. Tracked in process/AI-BACKLOG.md for a design/token-owner decision (new dedicated success token, mode-specific override, or accepted as-is). All other declared pairs pass.`
  },
  render: () => React11.createElement(
    "div",
    { className: "flex max-w-sm flex-col gap-6" },
    React11.createElement(Input, {
      label: "Name",
      placeholder: "Jane Doe"
    }),
    React11.createElement(Input, {
      label: "Email",
      type: "email",
      required: true,
      helperText: "We'll never share your email"
    }),
    React11.createElement(Input, {
      label: "API key",
      infoTooltip: React11.createElement(
        TooltipProvider,
        null,
        React11.createElement(
          Tooltip,
          null,
          React11.createElement(
            TooltipTrigger,
            { asChild: true },
            React11.createElement(IconButton, {
              icon: React11.createElement(Icon, {
                name: "info",
                size: 16,
                className: "text-feedback-info-primary"
              }),
              "aria-label": "What is this?",
              variant: "default",
              priority: "tertiary",
              size: "sm"
            })
          ),
          React11.createElement(TooltipContent, null, "Found in Settings \u2192 API access")
        )
      )
    }),
    React11.createElement(Input, {
      label: "Password",
      type: "password",
      endIcon: {
        type: "iconButton",
        icon: React11.createElement(Icon, { name: "eye", size: 16 }),
        label: "Show password",
        onClick: () => {
        }
      }
    }),
    React11.createElement(Input, {
      label: "Username",
      startIcon: React11.createElement(Icon, { name: "user", size: 16 }),
      placeholder: "username"
    }),
    React11.createElement(Input, {
      label: "Bio",
      maxLength: 120,
      defaultValue: "Threat intel analyst"
    }),
    React11.createElement(Input, {
      label: "Display name",
      error: "This name is already taken",
      defaultValue: "admin"
    }),
    React11.createElement(Input, {
      label: "Workspace slug",
      success: true,
      defaultValue: "filigran-prod"
    }),
    React11.createElement(Input, {
      label: "Organization",
      disabled: true,
      defaultValue: "Filigran"
    })
  )
};

// src/components/navbar/Navbar.tsx
var React14 = __toESM(require("react"));

// src/components/navbar-item/NavbarItem.tsx
var React13 = __toESM(require("react"));
var import_react_slot2 = require("@radix-ui/react-slot");

// src/components/navbar/NavbarContext.ts
var React12 = __toESM(require("react"));
var defaultValue = { collapsed: false };
var NavbarContext = React12.createContext(defaultValue);
function useNavbarCollapsed() {
  return React12.useContext(NavbarContext).collapsed;
}

// src/components/navbar-item/NavbarItem.tsx
var import_jsx_runtime10 = require("react/jsx-runtime");
function navbarItemClass(collapsed) {
  return cn(
    "group relative flex w-full items-center gap-2 h-9 pl-4 pr-2",
    "transition-colors",
    "font-content-base text-content-base leading-normal tracking-content-base",
    "text-default-primary",
    // Real 2px left border, always present in every state (color-only
    // toggle below, never width) — transparent by default (Figma-confirmed
    // width, node 6291:4003 — was 3px pre-fidelity-re-pass).
    "border-l-2 border-l-transparent",
    "hover:bg-elevation-highlight",
    // ⚠️ proposed, see RFC §3.4
    "focus-visible:outline-none focus-visible:ring-2 ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-focus",
    "disabled:pointer-events-none disabled:text-default-disabled data-[disabled]:pointer-events-none data-[disabled]:text-default-disabled",
    // "Selected" is derived from aria-current="page" — no separate prop (RFC
    // §4.2). Text color intentionally does NOT change (Figma-confirmed, node
    // 6291:4003): only bg + border carry the brand accent.
    "aria-[current=page]:bg-filigran-brand-primary-transparency aria-[current=page]:border-l-filigran-brand-primary",
    // Fidelity re-pass #4 point 2 / re-pass #5: collapsed rows were
    // left-anchored (pl-4 pr-2, same as expanded) and relied on the icon
    // happening to be exactly 16px wide for pl-4(16px) to coincidentally
    // land its center at the row's true midpoint. That coincidence broke
    // once the reserved left border was accounted for: `justify-center`
    // centers content within the box MINUS the one-sided border, so its
    // available midpoint sits exactly half the border's own width to the
    // right of the row's true visual center — confirmed empirically
    // (getBoundingClientRect: icon centerX 25.00 vs header logo centerX
    // 24.00 in a 48px row, delta collapses to exactly 0.00 the instant the
    // border is neutralized — no second cause). The icon span's own
    // `mr-0.5` counterbalances this precisely; row layout itself needs no
    // further change beyond mirroring the header row's `justify-center
    // px-0` collapsed treatment (Navbar.tsx).
    collapsed && "justify-center px-0"
  );
}
var NavbarItem = React13.forwardRef(
  ({
    className,
    asChild = false,
    icon,
    showIcon = true,
    chevron = false,
    children,
    tooltipLabel,
    disabled,
    type = "button",
    ...props
  }, ref) => {
    const collapsed = useNavbarCollapsed();
    const effectiveShowIcon = collapsed || showIcon;
    const row = asChild ? /* @__PURE__ */ (0, import_jsx_runtime10.jsx)(
      import_react_slot2.Slot,
      {
        ref,
        className: cn(navbarItemClass(collapsed), className),
        "data-disabled": disabled ? "" : void 0,
        "aria-disabled": disabled ? true : void 0,
        ...props,
        ...disabled ? {
          onClick: (e) => e.preventDefault(),
          onKeyDown: (e) => {
            if (e.key === "Enter" || e.key === " ") e.preventDefault();
          }
        } : {},
        children
      }
    ) : /* @__PURE__ */ (0, import_jsx_runtime10.jsxs)(
      "button",
      {
        ref,
        type,
        className: cn(navbarItemClass(collapsed), className),
        disabled,
        "data-disabled": disabled ? "" : void 0,
        ...props,
        children: [
          effectiveShowIcon && icon && // text-default-secondary: fidelity re-pass #2 point 7 — pixel-
          // sampled the live render against Figma node 5425:11518 and found
          // the leading/content icon is #afb0b6 (--gray-300, secondary),
          // while the label and chevron are #f2f2f3 (--gray-100, primary).
          // Content icons and affordance/control icons (chevron) are NOT
          // the same tone in this design — only this span needs overriding;
          // the row's own text-default-primary (label/chevron) is correct.
          //
          // group-data-[disabled]:text-default-disabled: fidelity re-pass
          // #4 point 3 — the icon kept text-default-secondary even when the
          // row was disabled, since that class always wins over the row's
          // own `disabled:text-default-disabled` (which only colors the
          // row/label, and doesn't cascade past this span's own explicit
          // override). Icon.tsx renders every glyph with currentColor by
          // design ("style it with text-* token classes ... never hardcoded
          // values") — Button/IconButton already establish the precedent of
          // `disabled:text-default-disabled` for exactly this, rather than
          // the unused `--icon-disabled` token (theme.css), which is only
          // ever an alias of this same `--text-default-disabled` value and
          // is not consumed by any component or utility actually wired to
          // currentColor-based icons.
          //
          // mr-0.5 (collapsed only): counterbalances the row's own reserved
          // left border (border-l-2 on navbarItemClass()) so this span's
          // center lands on the header logo's centerline (fidelity re-pass
          // #5) — both are static Tailwind core-scale classes (2px each),
          // see the reserved-indicator note above navbarItemClass(). Only
          // needed while collapsed: in expanded rows nothing is centered
          // (`justify-start`/default flow), so the one-sided border has no
          // centering effect to counteract there.
          /* @__PURE__ */ (0, import_jsx_runtime10.jsx)(
            "span",
            {
              className: cn(
                "inline-flex shrink-0 text-default-secondary group-data-[disabled]:text-default-disabled",
                collapsed && "mr-0.5"
              ),
              "aria-hidden": "true",
              children: icon
            }
          ),
          /* @__PURE__ */ (0, import_jsx_runtime10.jsx)("span", { className: cn("flex-1 truncate text-left", collapsed && "sr-only"), children }),
          chevron && !collapsed && /* @__PURE__ */ (0, import_jsx_runtime10.jsx)(
            "span",
            {
              className: "inline-flex shrink-0 transition-transform duration-150 motion-reduce:transition-none group-data-[state=open]:rotate-180",
              "aria-hidden": "true",
              children: /* @__PURE__ */ (0, import_jsx_runtime10.jsx)(Icon, { name: "chevron-down", size: 16 })
            }
          )
        ]
      }
    );
    const collapsedTooltipContent = asChild ? tooltipLabel : children;
    const missingTooltipLabel = collapsed && !chevron && asChild && !collapsedTooltipContent;
    if (typeof process !== "undefined" && process.env.NODE_ENV !== "production" && missingTooltipLabel) {
      console.warn(
        "NavbarItem: `asChild` is set on a collapsed row without `tooltipLabel`. The row has no accessible tooltip text to show, so no Tooltip is rendered \u2014 pass `tooltipLabel` to restore it."
      );
    }
    if (!collapsed || chevron || missingTooltipLabel) {
      return row;
    }
    return /* @__PURE__ */ (0, import_jsx_runtime10.jsx)(TooltipProvider, { children: /* @__PURE__ */ (0, import_jsx_runtime10.jsxs)(Tooltip, { children: [
      /* @__PURE__ */ (0, import_jsx_runtime10.jsx)(TooltipTrigger, { asChild: true, children: row }),
      /* @__PURE__ */ (0, import_jsx_runtime10.jsx)(TooltipContent, { side: "right", children: collapsedTooltipContent })
    ] }) });
  }
);
NavbarItem.displayName = "NavbarItem";

// src/components/navbar/Navbar.tsx
var import_jsx_runtime11 = require("react/jsx-runtime");
var Navbar = React14.forwardRef(
  ({
    className,
    collapsed: collapsedProp,
    defaultCollapsed = false,
    onCollapsedChange,
    header,
    children,
    footer,
    ...props
  }, ref) => {
    const [uncontrolledCollapsed, setUncontrolledCollapsed] = React14.useState(defaultCollapsed);
    const collapsed = collapsedProp ?? uncontrolledCollapsed;
    const handleToggle = React14.useCallback(() => {
      const next = !collapsed;
      if (collapsedProp === void 0) {
        setUncontrolledCollapsed(next);
      }
      onCollapsedChange?.(next);
    }, [collapsed, collapsedProp, onCollapsedChange]);
    return /* @__PURE__ */ (0, import_jsx_runtime11.jsx)(NavbarContext.Provider, { value: { collapsed }, children: /* @__PURE__ */ (0, import_jsx_runtime11.jsxs)(
      "nav",
      {
        ref,
        className: cn(
          "flex h-full flex-col justify-between overflow-hidden",
          // No border-r: fidelity re-pass #2 (node 5425:11608, the real
          // OpenCTI-integrated frame) confirmed the panel's right edge is
          // a plain background-color transition — pixel-sampled, no
          // distinct stroke color exists. The prior `border-r
          // border-elevation-default` was invented (§ report point 1).
          //
          // Fidelity re-pass #3 point 2: node 5425:11518/5425:11608
          // confirmed the panel's fill is the two-stop dark gradient
          // (--bg-elevation-default-layer-0 #070d18 -> -layer-0-gradient
          // #0c1527), not the flat --bg-elevation-default solid used
          // previously.
          //
          // Fidelity re-pass #4 point 1: the token's angle was wrongly set
          // to 135deg (an invented "average" of 3 differently-shaped
          // frames' resampled exports — 91.9/105.7/160.86deg — none of
          // which is actually 135). At 135deg on a tall/narrow fixed-width
          // panel, the transition stretches across the variable viewport
          // height, making the ~5% RGB delta imperceptible in the visible
          // fold (measured: rgb(7,14,25)->rgb(11,20,37) over 900px,
          // Playwright pixel sample). The Navbar's own node (5425:11608)
          // measures 91.9166deg — i.e. ~90deg, matching the codebase's
          // other 3 gradient tokens (gradient-focus/warning/ia, all
          // exactly 90deg). Corrected --gradient-default to 90deg: the
          // transition now runs across the fixed width (180px/48px),
          // rendering consistently and visibly regardless of nav height.
          "bg-gradient-default",
          // Width is the only Navbar-owned transition (§3.5): 150ms,
          // Tailwind's default easing (same curve family as opencti's own
          // 225ms transition, but reused from Tooltip's already-proven
          // 150ms duration instead of opencti's, since opencti's never
          // respects prefers-reduced-motion — repo-wide grep confirmed
          // zero matches). First `prefers-reduced-motion` support in FDS.
          "transition-[width] duration-150 motion-reduce:transition-none",
          collapsed ? "w-12" : "w-45",
          className
        ),
        ...props,
        children: [
          /* @__PURE__ */ (0, import_jsx_runtime11.jsxs)("div", { className: "flex min-h-0 flex-1 flex-col", children: [
            header && /* @__PURE__ */ (0, import_jsx_runtime11.jsx)(
              "div",
              {
                className: cn(
                  "flex h-17 shrink-0 items-center",
                  collapsed ? "justify-center px-0" : "justify-between pl-4 pr-1"
                ),
                children: header
              }
            ),
            /* @__PURE__ */ (0, import_jsx_runtime11.jsx)("div", { className: "flex flex-1 flex-col gap-1 overflow-y-auto py-2", children })
          ] }),
          /* @__PURE__ */ (0, import_jsx_runtime11.jsxs)("div", { className: "flex shrink-0 flex-col gap-1 py-2", children: [
            footer,
            /* @__PURE__ */ (0, import_jsx_runtime11.jsx)(
              NavbarItem,
              {
                icon: /* @__PURE__ */ (0, import_jsx_runtime11.jsx)(Icon, { name: collapsed ? "panel-left-open" : "panel-left-close", size: 16 }),
                onClick: handleToggle,
                children: collapsed ? "Expand" : "Collapse"
              }
            )
          ] })
        ]
      }
    ) });
  }
);
Navbar.displayName = "Navbar";
var NavbarSeparator = React14.forwardRef(
  ({ className, ...props }, ref) => /* @__PURE__ */ (0, import_jsx_runtime11.jsx)(
    "hr",
    {
      ref,
      className: cn("my-2 w-full border-t border-elevation-subtle", className),
      ...props
    }
  )
);
NavbarSeparator.displayName = "NavbarSeparator";

// src/components/navbar/Navbar.meta.ts
var import_react9 = require("react");

// src/components/navbar-submenu/NavbarSubmenu.tsx
var React15 = __toESM(require("react"));
var AccordionPrimitive = __toESM(require("@radix-ui/react-accordion"));
var DropdownMenuPrimitive = __toESM(require("@radix-ui/react-dropdown-menu"));
var import_react_slot3 = require("@radix-ui/react-slot");

// src/components/navbar-item/NavbarItem.meta.ts
var import_react8 = require("react");
var NavbarItemMeta = {
  name: "NavbarItem",
  description: `The single interactive row used throughout Navbar: a plain navigation link (leaf item), a NavbarSubmenu trigger row, and the Navbar footer's built-in collapse toggle all render this same component \u2014 only icon, chevron, and label content differ. Renders icon + label + optional trailing chevron. 'Selected' styling is derived from the native aria-current="page" attribute (no separate selected prop). Supports asChild (Radix Slot, same pattern as Button) so it can render as a router Link, and a showIcon prop mirroring opencti's real submenu_show_icons preference (forced true while the ancestor Navbar is collapsed, so the row is never rendered fully content-less). Wraps itself in the library's Tooltip when the ancestor Navbar is collapsed (icon-only mode).`,
  status: "stable",
  version: "0.1.0",
  radixPrimitive: "@radix-ui/react-slot",
  figmaLink: "https://www.figma.com/design/0PmhuZzF9XcaIEfMMW2a51/Design_System_2026?node-id=5125-47152",
  figmaNodeId: "5125:47152",
  accessibility: {
    wcag: "2.1 AA",
    wcagStatus: "pass",
    // "default" and "selected" are Figma-confirmed (get_design_context on
    // 5125:47152, then re-confirmed + corrected against 6291:4003 in the
    // 2026-07-23 fidelity re-pass: 2px border not 3px, text stays
    // default-colored not brand). hover/focus remain precedent-based
    // proposals (Button/Tabs precedent — no hover/focus instance was visible
    // in either Figma extraction), pending design sign-off (NavbarItem.rfc.md
    // §3.4/§3.6/§8). Disabled is exempt from contrast per WCAG 1.4.3 Note 1
    // (inactive UI component, same exemption Tabs documents) and is
    // intentionally NOT listed below.
    //
    // "default icon"/"hover icon" fg: fidelity re-pass #2 point 7 — pixel-
    // sampling the live render against Figma node 5425:11518 proved the
    // leading icon is --text-default-secondary, NOT --icon-default (which
    // aliases --text-default-primary, the label's own color). Corrected
    // from the original pre-evidence assumption that icon and label always
    // matched.
    //
    // Fidelity re-pass #3 point 2: NavbarItem always renders directly
    // inline inside Navbar's own container (never inside a portalled
    // popup), so every state whose row has no bg of its own (default,
    // selected border, focus ring) now sits on --gradient-default instead
    // of the flat --bg-elevation-default it used before. Rule 4 of
    // check-contrast.ts requires each of the gradient's stop tokens
    // (--bg-elevation-default-layer-0 / -layer-0-gradient) to appear
    // literally as an fg/bg value, so these entries are declared once per
    // stop instead of once against the old compound token. hover
    // label/icon are unaffected — --bg-elevation-highlight resolves to an
    // opaque solid (--grayblue-800 dark / --gray-150 light), fully
    // occluding whatever is behind it. Both stops verified (WCAG
    // relative-luminance formula, both modes): dark 17.38:1/16.29:1
    // (label), 8.99:1/8.43:1 (icon), 8.94:1/8.38:1 (brand-primary border
    // and focus ring); light 15.72:1/17.59:1 (label), 7.89:1/8.83:1
    // (icon), 11.22:1/12.55:1 (brand-primary) — all comfortably above
    // their thresholds.
    contrastPairs: [
      {
        id: "default label (stop 1)",
        fg: "--text-default-primary",
        bg: "--bg-elevation-default-layer-0",
        minRatio: 4.5
      },
      {
        id: "default label (stop 2)",
        fg: "--text-default-primary",
        bg: "--bg-elevation-default-layer-0-gradient",
        minRatio: 4.5
      },
      {
        id: "default icon (stop 1)",
        fg: "--text-default-secondary",
        bg: "--bg-elevation-default-layer-0",
        minRatio: 3
      },
      {
        id: "default icon (stop 2)",
        fg: "--text-default-secondary",
        bg: "--bg-elevation-default-layer-0-gradient",
        minRatio: 3
      },
      {
        id: "hover label",
        fg: "--text-default-primary",
        bg: "--bg-elevation-highlight",
        minRatio: 4.5
      },
      {
        id: "hover icon",
        fg: "--text-default-secondary",
        bg: "--bg-elevation-highlight",
        minRatio: 3
      },
      {
        id: "selected left border (stop 1)",
        fg: "--color-filigran-brand-primary",
        bg: "--bg-elevation-default-layer-0",
        minRatio: 3
      },
      {
        id: "selected left border (stop 2)",
        fg: "--color-filigran-brand-primary",
        bg: "--bg-elevation-default-layer-0-gradient",
        minRatio: 3
      },
      {
        id: "focus ring (stop 1)",
        fg: "--color-filigran-brand-primary",
        bg: "--bg-elevation-default-layer-0",
        minRatio: 3
      },
      {
        id: "focus ring (stop 2)",
        fg: "--color-filigran-brand-primary",
        bg: "--bg-elevation-default-layer-0-gradient",
        minRatio: 3
      },
      {
        id: "selected label",
        fg: "--text-default-primary",
        bg: "--color-filigran-brand-primary-transparency",
        minRatio: 4.5
      },
      {
        id: "selected icon",
        fg: "--text-default-secondary",
        bg: "--color-filigran-brand-primary-transparency",
        minRatio: 3
      }
    ],
    notes: `Uses native <button> semantics by default (or asChild's rendered child), Tab-reachable, Enter/Space activates. Visible focus ring (focus-visible:ring-2 ring-focus). 'Selected' is derived from aria-current="page" \u2014 no separate selected prop, a deliberate improvement over opencti/openaev (neither uses aria-current today, both compare routes manually). Only the left border and background tint change on selection \u2014 the label color stays --text-default-primary and the icon stays --text-default-secondary in every state, selected included (fidelity re-pass #2, node 5425:11518: the leading icon is always dimmer than the label; the original pre-evidence proposal of both using --icon-default, and switching text to brand-primary on selection, were both corrected against real evidence). The 'selected label'/'selected icon' pairs above verify the --color-filigran-brand-primary-transparency background (a color-mix() composite): correcting an earlier claim in this file that this could not be expressed as a contrastPairs entry \u2014 check-contrast.ts's resolveOpaqueBackdrop() DOES resolve color-mix() foregrounds/backgrounds by compositing them over --bg-elevation-default, confirmed empirically (pnpm check:contrast output matches this file's own prior hand-computed numbers to within sRGB rounding: 15.11:1/12.91:1 label, 7.82:1/6.48:1 icon, dark/light). showIcon={false} + collapsed Navbar is defensively overridden (icon always shown) to avoid a content-less, focusable-but-invisible row \u2014 see NavbarItem.rfc.md \xA73.6.`
  },
  render() {
    const sectionClass = "text-content-caption font-content-caption leading-content-caption tracking-content-caption text-default-secondary";
    return (0, import_react8.createElement)(
      "div",
      { className: "flex flex-col gap-6" },
      // Section 1: States (expanded)
      (0, import_react8.createElement)("p", { className: sectionClass }, "States (expanded)"),
      (0, import_react8.createElement)(
        "div",
        { className: "flex flex-col gap-1 w-45 border border-elevation-subtle rounded-sm p-2" },
        (0, import_react8.createElement)(NavbarItem, { icon: (0, import_react8.createElement)(Icon, { name: "house", size: 16 }), children: "Default" }),
        (0, import_react8.createElement)(NavbarItem, {
          icon: (0, import_react8.createElement)(Icon, { name: "shield", size: 16 }),
          "aria-current": "page",
          children: "Selected"
        }),
        (0, import_react8.createElement)(NavbarItem, {
          icon: (0, import_react8.createElement)(Icon, { name: "settings", size: 16 }),
          disabled: true,
          children: "Disabled"
        })
      ),
      // Section 2: Chevron (submenu trigger look)
      (0, import_react8.createElement)("p", { className: sectionClass }, "With chevron (submenu trigger)"),
      (0, import_react8.createElement)(
        "div",
        { className: "flex flex-col gap-1 w-45 border border-elevation-subtle rounded-sm p-2" },
        (0, import_react8.createElement)(NavbarItem, {
          icon: (0, import_react8.createElement)(Icon, { name: "book", size: 16 }),
          chevron: true,
          children: "Knowledge"
        })
      ),
      // Section 3: showIcon=false (submenu preference)
      (0, import_react8.createElement)("p", { className: sectionClass }, "showIcon=false (submenu icon preference)"),
      (0, import_react8.createElement)(
        "div",
        { className: "flex flex-col gap-1 w-45 border border-elevation-subtle rounded-sm p-2" },
        (0, import_react8.createElement)(NavbarItem, { showIcon: false, children: "Text-only label" })
      ),
      // Section 4: Collapsed Navbar (icon-only + Tooltip)
      (0, import_react8.createElement)("p", { className: sectionClass }, "Collapsed ancestor Navbar (icon-only, Tooltip-wrapped)"),
      (0, import_react8.createElement)(
        NavbarContext.Provider,
        { value: { collapsed: true } },
        (0, import_react8.createElement)(
          "div",
          { className: "flex flex-col gap-1 w-12 border border-elevation-subtle rounded-sm p-2" },
          (0, import_react8.createElement)(NavbarItem, { icon: (0, import_react8.createElement)(Icon, { name: "house", size: 16 }), children: "Home" }),
          (0, import_react8.createElement)(NavbarItem, {
            icon: (0, import_react8.createElement)(Icon, { name: "shield", size: 16 }),
            "aria-current": "page",
            children: "Threats"
          })
        )
      ),
      // Section 5: asChild (router Link)
      (0, import_react8.createElement)("p", { className: sectionClass }, "asChild (router Link)"),
      (0, import_react8.createElement)(
        "div",
        { className: "flex flex-col gap-1 w-45 border border-elevation-subtle rounded-sm p-2" },
        (0, import_react8.createElement)(NavbarItem, {
          asChild: true,
          children: (0, import_react8.createElement)("a", { href: "#", "aria-current": "page" }, "Dashboard")
        })
      ),
      // Section 6: asChild + collapsed Navbar (needs tooltipLabel — §5.3)
      (0, import_react8.createElement)(
        "p",
        { className: sectionClass },
        "asChild + collapsed ancestor Navbar (tooltipLabel required)"
      ),
      (0, import_react8.createElement)(
        NavbarContext.Provider,
        { value: { collapsed: true } },
        (0, import_react8.createElement)(
          "div",
          { className: "flex flex-col gap-1 w-12 border border-elevation-subtle rounded-sm p-2" },
          (0, import_react8.createElement)(NavbarItem, {
            asChild: true,
            tooltipLabel: "Dashboard",
            children: (0, import_react8.createElement)(
              "a",
              { href: "#", "aria-current": "page" },
              (0, import_react8.createElement)(
                "span",
                { className: "inline-flex shrink-0 text-default-secondary", "aria-hidden": "true" },
                (0, import_react8.createElement)(Icon, { name: "house", size: 16 })
              ),
              (0, import_react8.createElement)("span", { className: "flex-1 truncate text-left sr-only" }, "Dashboard")
            )
          })
        )
      )
    );
  },
  examples: [
    '<NavbarItem icon={<Icon name="house" size={16} />}>Dashboard</NavbarItem>',
    '// "Selected" comes from aria-current \u2014 no separate selected prop\n<NavbarItem icon={<Icon name="shield" size={16} />} aria-current="page">Threats</NavbarItem>',
    "// showIcon mirrors opencti's real submenu_show_icons preference\n<NavbarItem showIcon={false}>Reports</NavbarItem>",
    '// chevron is set by NavbarSubmenu when composing NavbarItem as its trigger row\n<NavbarItem icon={<Icon name="book" size={16} />} chevron>Knowledge</NavbarItem>',
    '// asChild merges NavbarItem styles onto a router Link, same pattern as Button\n<NavbarItem asChild>\n  <Link to="/dashboard" aria-current={isActive ? "page" : undefined}>Dashboard</Link>\n</NavbarItem>',
    `// asChild + a collapsed ancestor Navbar: icon/label must be composed manually
// inside the Link (asChild ignores icon/showIcon), and tooltipLabel supplies
// the Tooltip's accessible text instead of duplicating the whole Link (\xA75.3)
<NavbarItem asChild tooltipLabel="Dashboard">
  <Link to="/dashboard" aria-current={isActive ? "page" : undefined}>
    <span aria-hidden="true"><Icon name="house" size={16} /></span>
    <span className="sr-only">Dashboard</span>
  </Link>
</NavbarItem>`
  ],
  variants: ["default", "hover", "focus", "selected", "disabled"],
  sizes: [],
  props: {
    asChild: "boolean",
    icon: "ReactNode",
    showIcon: "boolean",
    chevron: "boolean",
    "aria-current": '"page" (drives the selected visual state)',
    disabled: "boolean",
    children: "ReactNode",
    tooltipLabel: "ReactNode (asChild + collapsed only \u2014 accessible duplicate for the Tooltip, since children is then the slotted element, not plain text)"
  },
  // Fidelity re-pass #2 point 9: docs-site presentation only — consolidates
  // this component onto the shared "Navbar" docs page as a Tabs entry
  // alongside Navbar/NavbarSubmenu/ProductSwitcher. The component itself
  // stays its own file/export; nothing else about it changes.
  docsGroup: {
    id: "navbar",
    label: "Navbar",
    tabLabel: "Navbar Item"
  }
};

// src/components/navbar-submenu/NavbarSubmenu.tsx
var import_jsx_runtime12 = require("react/jsx-runtime");
var navbarSubmenuItemClass = cn(
  "group relative flex w-full items-center gap-2 h-9 pl-6 pr-4",
  "transition-colors",
  "font-content-compact text-content-compact leading-normal tracking-content-compact",
  "text-default-secondary",
  // 2px left border reserved (transparent) in every state, same reasoning
  // as NavbarItem: the aria-current accent below never shifts row content
  // (Figma-confirmed width, node 6291:4003 — was 3px pre-fidelity-re-pass).
  "border-l-2 border-l-transparent",
  "hover:bg-elevation-highlight",
  // ⚠️ proposed, see RFC §3.4
  "focus-visible:outline-none focus-visible:ring-2 ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-focus",
  // "Selected" derived from aria-current="page" — same mechanism as
  // NavbarItem (RFC §4.2). Text color intentionally does not change
  // (Figma-confirmed, node 6291:4003, same evidence as NavbarItem). No
  // left-border accent here (unlike NavbarItem): fidelity re-pass #2 point 1
  // — node 5425:11518 shows the selected submenu row with only the tint,
  // no stroke; the border was an unverified symmetry-with-NavbarItem
  // assumption from the original RFC, now corrected against the real frame.
  "aria-[current=page]:bg-filigran-brand-primary-transparency"
);
var NavbarSubmenuItem = React15.forwardRef(
  ({ className, asChild = false, icon, showIcon = true, children, ...props }, ref) => {
    if (asChild) {
      return /* @__PURE__ */ (0, import_jsx_runtime12.jsx)(import_react_slot3.Slot, { ref, className: cn(navbarSubmenuItemClass, className), ...props, children });
    }
    return /* @__PURE__ */ (0, import_jsx_runtime12.jsxs)("a", { ref, className: cn(navbarSubmenuItemClass, className), ...props, children: [
      showIcon && icon && /* @__PURE__ */ (0, import_jsx_runtime12.jsx)("span", { className: "inline-flex shrink-0", "aria-hidden": "true", children: icon }),
      /* @__PURE__ */ (0, import_jsx_runtime12.jsx)("span", { className: "flex-1 truncate text-left", children })
    ] });
  }
);
NavbarSubmenuItem.displayName = "NavbarSubmenuItem";
var ACCORDION_ITEM_VALUE = "submenu";
var HOVER_CLOSE_DELAY_MS = 150;
function hasActiveDescendant(children) {
  return React15.Children.toArray(children).some((child) => {
    if (!React15.isValidElement(child)) return false;
    const props = child.props;
    if (props["aria-current"] === "page") return true;
    const nested = props.children;
    if (React15.isValidElement(nested)) {
      const nestedProps = nested.props;
      if (nestedProps["aria-current"] === "page") return true;
    }
    return false;
  });
}
function NavbarSubmenu({
  label,
  icon,
  open,
  defaultOpen = false,
  onOpenChange,
  href,
  to,
  children
}) {
  const collapsed = useNavbarCollapsed();
  const isActiveDescendant = hasActiveDescendant(children);
  const [uncontrolledOpen, setUncontrolledOpen] = React15.useState(defaultOpen);
  const isOpen = open ?? uncontrolledOpen;
  if (typeof process !== "undefined" && process.env.NODE_ENV !== "production" && href && to) {
    console.warn(
      "NavbarSubmenu: both `href` and `to` are set, which are documented as mutually exclusive. `href` takes priority and `to` is ignored."
    );
  }
  const linkHref = href ?? to;
  const setOpen = React15.useCallback(
    (next) => {
      setUncontrolledOpen(next);
      onOpenChange?.(next);
    },
    [onOpenChange]
  );
  const triggerRef = React15.useRef(null);
  const contentHasFocusRef = React15.useRef(false);
  const handleContentFocus = React15.useCallback(() => {
    contentHasFocusRef.current = true;
  }, []);
  const handleContentBlur = React15.useCallback(() => {
    contentHasFocusRef.current = false;
  }, []);
  const prevCollapsedRef = React15.useRef(collapsed);
  const pendingRefocusRef = React15.useRef(false);
  const pendingOpenChangeNotifyRef = React15.useRef(false);
  if (prevCollapsedRef.current !== collapsed) {
    const justCollapsed = collapsed && !prevCollapsedRef.current;
    prevCollapsedRef.current = collapsed;
    if (justCollapsed && contentHasFocusRef.current) {
      contentHasFocusRef.current = false;
      pendingRefocusRef.current = true;
      if (open === void 0) {
        setUncontrolledOpen(false);
      } else {
        pendingOpenChangeNotifyRef.current = true;
      }
    }
  }
  React15.useEffect(() => {
    if (pendingOpenChangeNotifyRef.current) {
      pendingOpenChangeNotifyRef.current = false;
      onOpenChange?.(false);
    }
  });
  React15.useLayoutEffect(() => {
    if (pendingRefocusRef.current) {
      pendingRefocusRef.current = false;
      triggerRef.current?.focus();
    }
  });
  const closeTimerRef = React15.useRef(void 0);
  const cancelScheduledClose = React15.useCallback(() => {
    if (closeTimerRef.current) clearTimeout(closeTimerRef.current);
  }, []);
  const justOpenedByHoverRef = React15.useRef(false);
  const ignoreRadixToggleRef = React15.useRef(false);
  const openOnHover = React15.useCallback(() => {
    cancelScheduledClose();
    if (!isOpen) {
      justOpenedByHoverRef.current = true;
    }
    setOpen(true);
  }, [cancelScheduledClose, setOpen, isOpen]);
  const closeOnHoverLeave = React15.useCallback(() => {
    cancelScheduledClose();
    closeTimerRef.current = setTimeout(() => setOpen(false), HOVER_CLOSE_DELAY_MS);
  }, [cancelScheduledClose, setOpen]);
  React15.useEffect(() => cancelScheduledClose, [cancelScheduledClose]);
  const markPendingToggleFromHover = React15.useCallback(() => {
    if (justOpenedByHoverRef.current) {
      justOpenedByHoverRef.current = false;
      ignoreRadixToggleRef.current = true;
      setTimeout(() => {
        ignoreRadixToggleRef.current = false;
      }, 0);
    }
  }, []);
  const suppressLinkToggleRef = React15.useRef(false);
  const markSuppressToggleFromLink = React15.useCallback(() => {
    suppressLinkToggleRef.current = true;
    setTimeout(() => {
      suppressLinkToggleRef.current = false;
    }, 0);
  }, []);
  const handleRadixOpenChange = React15.useCallback(
    (next) => {
      if (suppressLinkToggleRef.current) {
        return;
      }
      if (!next && ignoreRadixToggleRef.current) {
        return;
      }
      setOpen(next);
    },
    [setOpen]
  );
  const handleLinkTriggerKeyDown = React15.useCallback((event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      event.currentTarget.click();
    }
  }, []);
  if (collapsed) {
    return (
      // `modal={false}`: the default modal DropdownMenu traps focus and sets
      // `pointer-events: none` on the rest of the page while open — correct
      // for an action/context menu, but wrong for a persistent nav's hover
      // flyout, where the user must remain able to point at sibling nav
      // items (and the rest of the page) while this panel is open.
      /* @__PURE__ */ (0, import_jsx_runtime12.jsxs)(DropdownMenuPrimitive.Root, { open: isOpen, onOpenChange: handleRadixOpenChange, modal: false, children: [
        /* @__PURE__ */ (0, import_jsx_runtime12.jsx)(
          DropdownMenuPrimitive.Trigger,
          {
            asChild: true,
            ref: triggerRef,
            onKeyDown: linkHref ? handleLinkTriggerKeyDown : void 0,
            children: linkHref ? (
              // `NavbarItem asChild`: Slot merges `navbarItemClass`/aria-*
              // onto a real, hand-composed `<a>` — icon/label spans are
              // duplicated here (not reused from NavbarItem's own internal
              // rendering) because Slot can't inject wrapper markup around an
              // arbitrary child (NavbarItem.tsx's own `asChild` doc comment);
              // this mirrors exactly what NavbarItem's non-asChild branch
              // renders in collapsed mode (icon visible, label visually
              // hidden — collapsed is always true here, so `effectiveShowIcon`
              // is always true and the label is always `sr-only`), so a
              // future edit to NavbarItem's collapsed markup should double as
              // a reminder to keep this copy in sync.
              /* @__PURE__ */ (0, import_jsx_runtime12.jsx)(
                NavbarItem,
                {
                  asChild: true,
                  chevron: true,
                  "aria-current": isActiveDescendant ? "page" : void 0,
                  onMouseEnter: openOnHover,
                  onMouseLeave: closeOnHoverLeave,
                  children: /* @__PURE__ */ (0, import_jsx_runtime12.jsxs)("a", { href: linkHref, onPointerDown: markSuppressToggleFromLink, children: [
                    icon && /* @__PURE__ */ (0, import_jsx_runtime12.jsx)(
                      "span",
                      {
                        className: "inline-flex shrink-0 text-default-secondary group-data-[disabled]:text-default-disabled mr-0.5",
                        "aria-hidden": "true",
                        children: icon
                      }
                    ),
                    /* @__PURE__ */ (0, import_jsx_runtime12.jsx)("span", { className: "flex-1 truncate text-left sr-only", children: label })
                  ] })
                }
              )
            ) : /* @__PURE__ */ (0, import_jsx_runtime12.jsx)(
              NavbarItem,
              {
                icon,
                chevron: true,
                "aria-current": isActiveDescendant ? "page" : void 0,
                onMouseEnter: openOnHover,
                onMouseLeave: closeOnHoverLeave,
                onPointerDown: markPendingToggleFromHover,
                children: label
              }
            )
          }
        ),
        /* @__PURE__ */ (0, import_jsx_runtime12.jsx)(DropdownMenuPrimitive.Portal, { children: /* @__PURE__ */ (0, import_jsx_runtime12.jsx)(
          DropdownMenuPrimitive.Content,
          {
            side: "right",
            align: "start",
            sideOffset: 8,
            onMouseEnter: openOnHover,
            onMouseLeave: closeOnHoverLeave,
            onFocus: handleContentFocus,
            onBlur: handleContentBlur,
            className: cn(
              // Fidelity re-pass #2 points 1/5: node 6301:10728 (flyout
              // reference) confirmed NO border, shadow, radius, or internal
              // padding — the panel is a flat 179px-wide fill, items running
              // edge-to-edge. `min-w-48` (192px) and the
              // border/shadow/rounded-sm/p-1 below were all invented in the
              // prior pass; replaced with the measured fixed width.
              "z-50 w-[179px]",
              // token-lint-allow(179px is the exact Figma-measured flyout width, node 6301:10728 — not a multiple of the 4px spacing scale, so no --spacing-* token fits; same category as LAYOUT.md's content-measure exceptions)
              "bg-elevation-default",
              // Same fade pattern as Tooltip.tsx, extended with
              // motion-reduce awareness per this component's RFC §3.5.
              "opacity-0 transition-opacity duration-150 motion-reduce:transition-none",
              "data-[state=open]:opacity-100 data-[state=closed]:opacity-0"
            ),
            children: React15.Children.map(children, (child) => /* @__PURE__ */ (0, import_jsx_runtime12.jsx)(DropdownMenuPrimitive.Item, { asChild: true, children: child }))
          }
        ) })
      ] })
    );
  }
  return /* @__PURE__ */ (0, import_jsx_runtime12.jsx)(
    AccordionPrimitive.Root,
    {
      type: "multiple",
      value: isOpen ? [ACCORDION_ITEM_VALUE] : [],
      onValueChange: (value) => setOpen(value.includes(ACCORDION_ITEM_VALUE)),
      children: /* @__PURE__ */ (0, import_jsx_runtime12.jsxs)(AccordionPrimitive.Item, { value: ACCORDION_ITEM_VALUE, children: [
        /* @__PURE__ */ (0, import_jsx_runtime12.jsx)(AccordionPrimitive.Header, { asChild: true, children: /* @__PURE__ */ (0, import_jsx_runtime12.jsx)("div", { children: /* @__PURE__ */ (0, import_jsx_runtime12.jsx)(AccordionPrimitive.Trigger, { asChild: true, ref: triggerRef, children: /* @__PURE__ */ (0, import_jsx_runtime12.jsx)(
          NavbarItem,
          {
            icon,
            chevron: true,
            "aria-current": isActiveDescendant ? "page" : void 0,
            children: label
          }
        ) }) }) }),
        /* @__PURE__ */ (0, import_jsx_runtime12.jsx)(
          AccordionPrimitive.Content,
          {
            className: "overflow-hidden",
            onFocus: handleContentFocus,
            onBlur: handleContentBlur,
            children: /* @__PURE__ */ (0, import_jsx_runtime12.jsx)("div", { className: "flex flex-col", children })
          }
        )
      ] })
    }
  );
}
NavbarSubmenu.displayName = "NavbarSubmenu";

// src/components/product-switcher/ProductSwitcher.tsx
var React16 = __toESM(require("react"));
var DropdownMenuPrimitive2 = __toESM(require("@radix-ui/react-dropdown-menu"));
var import_jsx_runtime13 = require("react/jsx-runtime");
var productSwitcherItemClass = cn(
  "flex w-full items-center gap-2 h-9 px-2",
  // Typography: fidelity re-pass #2 point 7 — this row had NO font-size/
  // weight/line-height/tracking classes at all (a real gap, not a
  // reuse-elsewhere choice); applying NavbarItem's confirmed content-base
  // treatment (node 6291:4003 lineage) for internal batch consistency, per
  // @sandy's standing instruction to reuse this batch's confirmed values in
  // the absence of a reliable Figma source for this component specifically.
  "font-content-base text-content-base leading-normal tracking-content-base",
  // text-default-primary doubles as the external-link icon's color (SVG
  // `currentColor` inheritance) — `--icon-default` (RFC §3.4) resolves to
  // this exact same token in theme.css, so no separate icon-color class
  // is needed on the icon span itself.
  "text-default-primary",
  "hover:bg-elevation-highlight",
  // ⚠️ reused from NavbarItem §3.4, to revalidate
  "focus-visible:outline-none focus-visible:ring-2 ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-focus"
);
function ProductSwitcherOptionItem({ option }) {
  const external = Boolean(option.href);
  if (typeof process !== "undefined" && process.env.NODE_ENV !== "production" && option.href && option.to) {
    console.warn(
      `ProductSwitcher: option "${option.id}" sets both \`href\` and \`to\`, which are documented as mutually exclusive. \`href\` takes priority and \`to\` is ignored.`
    );
  }
  const body = /* @__PURE__ */ (0, import_jsx_runtime13.jsxs)(import_jsx_runtime13.Fragment, { children: [
    /* @__PURE__ */ (0, import_jsx_runtime13.jsx)("span", { className: "inline-flex shrink-0 items-center", "aria-hidden": "true", children: option.logo }),
    /* @__PURE__ */ (0, import_jsx_runtime13.jsxs)("span", { className: "sr-only", children: [
      option.label,
      external && " (opens in a new tab)"
    ] }),
    external && /* @__PURE__ */ (0, import_jsx_runtime13.jsx)("span", { className: "ml-auto inline-flex shrink-0", "aria-hidden": "true", children: /* @__PURE__ */ (0, import_jsx_runtime13.jsx)(Icon, { name: "external-link", size: 16 }) })
  ] });
  let element;
  const inert = !option.href && !option.to;
  if (option.href) {
    element = /* @__PURE__ */ (0, import_jsx_runtime13.jsx)(
      "a",
      {
        href: option.href,
        target: "_blank",
        rel: "noopener noreferrer",
        className: productSwitcherItemClass,
        children: body
      }
    );
  } else if (option.to) {
    element = /* @__PURE__ */ (0, import_jsx_runtime13.jsx)("a", { href: option.to, className: productSwitcherItemClass, children: body });
  } else {
    element = /* @__PURE__ */ (0, import_jsx_runtime13.jsx)("div", { className: cn(productSwitcherItemClass, "cursor-default opacity-60"), children: body });
  }
  const item = /* @__PURE__ */ (0, import_jsx_runtime13.jsx)(DropdownMenuPrimitive2.Item, { asChild: true, disabled: inert, children: element });
  if (!option.tooltip) {
    return item;
  }
  return /* @__PURE__ */ (0, import_jsx_runtime13.jsxs)(Tooltip, { children: [
    /* @__PURE__ */ (0, import_jsx_runtime13.jsx)(TooltipTrigger, { asChild: true, children: item }),
    /* @__PURE__ */ (0, import_jsx_runtime13.jsx)(TooltipContent, { side: "left", children: option.tooltip })
  ] });
}
var ProductSwitcher = React16.forwardRef(
  ({ className, logo, label, options, ...props }, ref) => {
    const collapsed = useNavbarCollapsed();
    const logoSpan = /* @__PURE__ */ (0, import_jsx_runtime13.jsx)("span", { className: "inline-flex h-7 shrink-0 items-center overflow-hidden", "aria-hidden": "true", children: logo });
    const menu = /* @__PURE__ */ (0, import_jsx_runtime13.jsx)(DropdownMenuPrimitive2.Portal, { children: /* @__PURE__ */ (0, import_jsx_runtime13.jsx)(
      DropdownMenuPrimitive2.Content,
      {
        side: "bottom",
        align: "end",
        sideOffset: 8,
        className: cn(
          // Fidelity re-pass #2 point 1: no dedicated Figma frame exists
          // for this menu (RFC §3.1/§3.6), but the confirmed real
          // NavbarSubmenu flyout (node 6301:10728) has no
          // border/shadow/radius/padding — reused here for the same
          // reason as the color tokens above (@sandy's standing "reuse
          // this batch's confirmed values" instruction). `min-w-48`
          // unchanged: no source (Figma or otherwise) suggests a
          // different width for this specific menu.
          "z-50 min-w-48",
          "bg-elevation-default",
          // Same fade pattern as Tooltip.tsx / NavbarSubmenu.tsx, extended
          // with motion-reduce awareness per this batch's shared §3.5 decision.
          "opacity-0 transition-opacity duration-150 motion-reduce:transition-none",
          "data-[state=open]:opacity-100 data-[state=closed]:opacity-0"
        ),
        children: /* @__PURE__ */ (0, import_jsx_runtime13.jsx)(TooltipProvider, { children: options.map((option) => /* @__PURE__ */ (0, import_jsx_runtime13.jsx)(ProductSwitcherOptionItem, { option }, option.id)) })
      }
    ) });
    if (collapsed) {
      return /* @__PURE__ */ (0, import_jsx_runtime13.jsxs)(DropdownMenuPrimitive2.Root, { children: [
        /* @__PURE__ */ (0, import_jsx_runtime13.jsx)(
          DropdownMenuPrimitive2.Trigger,
          {
            ref,
            className: cn(
              "inline-flex h-9 w-full items-center justify-center px-0",
              "text-default-primary",
              "hover:bg-elevation-highlight",
              // ⚠️ reused from NavbarItem §3.4, to revalidate
              "focus-visible:outline-none focus-visible:ring-2 ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-focus",
              className
            ),
            ...props,
            "aria-label": label,
            children: logoSpan
          }
        ),
        menu
      ] });
    }
    return /* @__PURE__ */ (0, import_jsx_runtime13.jsxs)(DropdownMenuPrimitive2.Root, { children: [
      /* @__PURE__ */ (0, import_jsx_runtime13.jsxs)("div", { className: "flex h-9 w-full items-center justify-between", children: [
        logoSpan,
        /* @__PURE__ */ (0, import_jsx_runtime13.jsx)(DropdownMenuPrimitive2.Trigger, { asChild: true, children: /* @__PURE__ */ (0, import_jsx_runtime13.jsx)(
          IconButton,
          {
            ref,
            icon: /* @__PURE__ */ (0, import_jsx_runtime13.jsx)("span", { className: "transition-transform duration-150 motion-reduce:transition-none group-data-[state=open]:rotate-180", children: /* @__PURE__ */ (0, import_jsx_runtime13.jsx)(Icon, { name: "chevron-down", size: 16 }) }),
            size: "sm",
            priority: "tertiary",
            className: cn(
              "group",
              // Overrides tertiary's default brand-blue treatment to match
              // the rest of this batch's confirmed neutral tokens
              // (NavbarItem/NavbarSubmenuItem, ProductSwitcher.rfc.md §3.4)
              // — no reliable Figma color source exists for this component.
              "text-default-primary hover:bg-elevation-highlight active:bg-elevation-highlight",
              "focus-visible:ring-focus",
              className
            ),
            ...props,
            "aria-label": label
          }
        ) })
      ] }),
      menu
    ] });
  }
);
ProductSwitcher.displayName = "ProductSwitcher";

// src/components/navbar/Navbar.meta.ts
var NavbarMeta = {
  name: "Navbar",
  description: "The primary structural navigation container of every Filigran product: a vertical, collapsible sidebar hosting a header slot (typically ProductSwitcher), a scrollable list of NavbarItem/NavbarSubmenu rows (separated into groups by NavbarSeparator), an optional footer slot, and a built-in collapse toggle. A thin composition root, not a monolith \u2014 it owns the collapsed/expanded state, the <nav> landmark, and the width transition, sharing collapsed with its descendants via NavbarContext rather than rendering their content itself. Renders a real <nav aria-label> landmark, a real accessibility improvement over both opencti and openaev's current MUI Drawer-based sidebars (neither renders a landmark today).",
  status: "stable",
  version: "0.1.0",
  radixPrimitive: "none",
  figmaLink: "https://www.figma.com/design/0PmhuZzF9XcaIEfMMW2a51/Design_System_2026?node-id=2843-4074",
  figmaNodeId: "2843:4074",
  accessibility: {
    wcag: "2.1 AA",
    wcagStatus: "pass",
    // Correction (repass #2 conformity gate): Navbar is NOT actually
    // content-less — it directly renders its own footer collapse/expand
    // toggle row (`<NavbarItem icon=... onClick={handleToggle}>{collapsed ?
    // "Expand" : "Collapse"}</NavbarItem>` in Navbar.tsx), a real element
    // instantiated BY this component itself, not supplied by a consumer via
    // children/props — unlike the scrollable item list or header/footer
    // slots, which genuinely are opaque consumer content this component
    // never styles. The prior empty array assumed the whole surface was
    // opaque; the toggle row is the one exception. Its fg/bg tokens are the
    // same ones NavbarItem.meta.ts already declares for its default
    // label/icon state (this row renders in that exact state), so these
    // entries are intentionally duplicate values, not a second independent
    // token choice — declared here too because the conformity suite
    // (src/__tests__/conformity.test.tsx) requires every discovered
    // component to carry at least one contrastPairs entry, with no
    // "composes only" exemption. The border-r removal note from fidelity
    // re-pass #2 point 1 still stands (node 5425:11518: plain background
    // transition, no distinct stroke) — that part of the container remains
    // genuinely content-less.
    //
    // Fidelity re-pass #3 point 2: the panel's own background is now
    // --gradient-default (2 stops: --bg-elevation-default-layer-0 and
    // --bg-elevation-default-layer-0-gradient), not the flat
    // --bg-elevation-default it used before. check-contrast.ts Rule 4
    // requires every stop token of a gradient referenced via a
    // bg-gradient-* class to appear literally as an fg/bg value in some
    // contrastPairs entry, so the footer toggle row (which sits directly on
    // the panel background, no bg of its own) is declared once per stop
    // rather than once against the old compound token. Both stops verified
    // (WCAG relative-luminance formula, both modes): dark 17.38:1/16.29:1
    // (label) and 8.99:1/8.43:1 (icon); light 15.72:1/17.59:1 (label) and
    // 7.89:1/8.83:1 (icon) — all comfortably above their thresholds.
    contrastPairs: [
      {
        id: "footer toggle label (stop 1)",
        fg: "--text-default-primary",
        bg: "--bg-elevation-default-layer-0",
        minRatio: 4.5
      },
      {
        id: "footer toggle label (stop 2)",
        fg: "--text-default-primary",
        bg: "--bg-elevation-default-layer-0-gradient",
        minRatio: 4.5
      },
      {
        id: "footer toggle icon (stop 1)",
        fg: "--text-default-secondary",
        bg: "--bg-elevation-default-layer-0",
        minRatio: 3
      },
      {
        id: "footer toggle icon (stop 2)",
        fg: "--text-default-secondary",
        bg: "--bg-elevation-default-layer-0-gradient",
        minRatio: 3
      }
    ],
    notes: "Root element is a native <nav aria-label={ariaLabel}> landmark (required prop, no default \u2014 the exact wording is product/locale-specific and cannot be safely defaulted, \xA75.3). No interaction of its own beyond Tab reaching interactive children in DOM order (header/ProductSwitcher \u2192 items/submenus \u2192 footer collapse toggle) \u2014 no custom keyboard handling at the Navbar level (\xA75.2). Collapsed/expanded is a purely visual/layout state, not announced via a live region (\xA75.3) \u2014 aria-expanded is used at the item/submenu level (NavbarSubmenu), never at the Navbar root, since the root has no ARIA state of its own (it's a landmark, not a disclosure widget). Focus management on collapse (an open submenu's focus returning to its parent trigger) is implemented by NavbarSubmenu reacting to NavbarContext \u2014 Navbar only needs to expose collapsed correctly, not implement the mechanism itself (\xA75.2). Width transition: 150ms, Tailwind's default easing, with motion-reduce:transition-none \u2014 reused from Tooltip's already-proven 150ms duration rather than opencti's real 225ms (LeftBar.jsx:438-441), because opencti's transition never respects prefers-reduced-motion anywhere in its codebase (repo-wide grep confirmed zero matches) \u2014 this is the first prefers-reduced-motion implementation in the library (\xA73.5). The built-in footer collapse-toggle row deliberately diverges from opencti's real implementation (LeftBar.jsx:812-814, label={t_i18n('Collapse')} unconditionally, icon-only swap): a static 'Collapse' label read by AT users while already collapsed is misleading (the next action is actually 'expand'), the same class of clarity issue already corrected on ProductSwitcher's own chevron \u2014 so the label here is state-dependent ('Collapse' expanded / 'Expand' collapsed) instead of a static string, a disclosed refinement beyond the RFC's literal \xA74.2 text, not a silent change to an arbitrated decision."
  },
  render() {
    const demoItems = (withSubmenu) => [
      (0, import_react9.createElement)(NavbarItem, {
        key: "dashboard",
        icon: (0, import_react9.createElement)(Icon, { name: "layout-panel-left", size: 16 }),
        "aria-current": "page",
        children: "Dashboard"
      }),
      (0, import_react9.createElement)(NavbarItem, {
        key: "analyses",
        icon: (0, import_react9.createElement)(Icon, { name: "chart-bar", size: 16 }),
        children: "Analyses"
      }),
      (0, import_react9.createElement)(NavbarSeparator, { key: "sep-1" }),
      withSubmenu ? (0, import_react9.createElement)(NavbarSubmenu, {
        key: "knowledge",
        label: "Knowledge",
        icon: (0, import_react9.createElement)(Icon, { name: "book", size: 16 }),
        defaultOpen: true,
        children: [
          (0, import_react9.createElement)(NavbarSubmenuItem, { key: "reports", href: "#", children: "Reports" }),
          (0, import_react9.createElement)(NavbarSubmenuItem, { key: "malware", href: "#", children: "Malware" })
        ]
      }) : (0, import_react9.createElement)(NavbarItem, {
        key: "knowledge",
        icon: (0, import_react9.createElement)(Icon, { name: "book", size: 16 }),
        children: "Knowledge"
      }),
      (0, import_react9.createElement)(NavbarSeparator, { key: "sep-2" }),
      (0, import_react9.createElement)(NavbarItem, {
        key: "settings",
        icon: (0, import_react9.createElement)(Icon, { name: "settings", size: 16 }),
        children: "Settings"
      })
    ];
    const demoHeader = (0, import_react9.createElement)(ProductSwitcher, {
      logo: (0, import_react9.createElement)(Icon, { name: "custom/opencti", size: 20 }),
      label: "More Filigran products",
      options: [
        {
          id: "openaev",
          label: "OpenAEV",
          logo: (0, import_react9.createElement)(Icon, { name: "custom/openaev", size: 20 }),
          href: "https://example.com/openaev"
        }
      ]
    });
    return (0, import_react9.createElement)(
      "div",
      { className: "flex gap-6" },
      (0, import_react9.createElement)(
        "div",
        { className: "flex h-125 flex-col" },
        (0, import_react9.createElement)(
          "p",
          {
            className: "text-content-caption font-content-caption leading-content-caption tracking-content-caption text-default-secondary mb-2"
          },
          "Expanded"
        ),
        (0, import_react9.createElement)(Navbar, {
          // Distinct aria-label per demo instance — two <nav> landmarks with
          // the SAME accessible name on one page is a real axe landmark-unique
          // violation (WCAG 1.3.1), caught by the conformity test suite. Real
          // consumers only ever render one Navbar per page, so this is purely
          // a showcase-demo concern, not a Navbar API concern.
          "aria-label": "Main navigation (expanded example)",
          header: demoHeader,
          children: demoItems(true)
        })
      ),
      (0, import_react9.createElement)(
        "div",
        { className: "flex h-125 flex-col" },
        (0, import_react9.createElement)(
          "p",
          {
            className: "text-content-caption font-content-caption leading-content-caption tracking-content-caption text-default-secondary mb-2"
          },
          "Collapsed"
        ),
        (0, import_react9.createElement)(Navbar, {
          "aria-label": "Main navigation (collapsed example)",
          header: demoHeader,
          defaultCollapsed: true,
          children: demoItems(true)
        })
      )
    );
  },
  examples: [
    '// Uncontrolled \u2014 Navbar manages its own collapsed state internally\n<Navbar aria-label="Main navigation" header={<ProductSwitcher .../>}>\n  <NavbarItem icon={<Icon name="layout-panel-left" size={16} />} aria-current="page">Dashboard</NavbarItem>\n  <NavbarSeparator />\n  <NavbarSubmenu label="Knowledge" icon={<Icon name="book" size={16} />}>\n    <NavbarSubmenuItem href="/reports">Reports</NavbarSubmenuItem>\n  </NavbarSubmenu>\n</Navbar>',
    `// Controlled collapse, persisted by the consuming product
<Navbar
  aria-label="Main navigation"
  collapsed={collapsed}
  onCollapsedChange={(next) => { setCollapsed(next); localStorage.setItem('navOpen', String(!next)); }}
>
  ...
</Navbar>`,
    '// Optional footer slot, rendered above the built-in collapse toggle\n<Navbar aria-label="Main navigation" footer={<NavbarItem icon={<Icon name="circle-question-mark" size={16} />}>Help</NavbarItem>}>\n  ...\n</Navbar>'
  ],
  variants: ["expanded", "collapsed"],
  sizes: [],
  props: {
    "aria-label": "string (required) \u2014 accessible landmark name, no default (product/locale-specific)",
    collapsed: "boolean \u2014 controlled collapsed state, omit for uncontrolled",
    defaultCollapsed: "boolean \u2014 initial collapsed state (uncontrolled), default false",
    onCollapsedChange: "(collapsed: boolean) => void \u2014 notifies on footer-toggle-driven changes",
    header: "ReactNode \u2014 typically a <ProductSwitcher>, rendered in the 68px header row",
    children: "ReactNode \u2014 NavbarItem / NavbarSubmenu / NavbarSeparator content",
    footer: "ReactNode \u2014 rendered above the built-in collapse toggle (e.g. a help/settings NavbarItem)",
    "NavbarSeparator (named export)": "Divider between item groups \u2014 a plain <hr>, no props of its own beyond native <hr> attributes"
  },
  // Fidelity re-pass #2 point 9: this is the group's canonical/landing
  // entry (slug "navbar" == id) — see NavbarItem/NavbarSubmenu/
  // ProductSwitcher.meta.ts for the other three tabs on the same page.
  docsGroup: {
    id: "navbar",
    label: "Navbar",
    tabLabel: "Navbar"
  }
};

// src/components/navbar-submenu/NavbarSubmenu.meta.ts
var import_react10 = require("react");
var NavbarSubmenuMeta = {
  name: "NavbarSubmenu",
  description: `Dual-mode submenu: an inline WAI-ARIA disclosure (Radix Accordion, type="multiple") while the ancestor Navbar is expanded, and a role="menu" flyout (Radix DropdownMenu, opened by click, keyboard, or hover) while it's collapsed. The trigger in both modes IS a NavbarItem (composed via asChild, not reimplemented) with chevron set automatically. Mode is always derived from NavbarContext \u2014 there is no prop to force one manually. When the ancestor Navbar collapses while focus is inside an open accordion panel, the panel closes and focus returns to its own trigger. NavbarSubmenuItem is the row component, shipped only as part of this compound API (mirrors Tabs/TabsTrigger) \u2014 never a standalone top-level export. Optional href/to (collapsed mode only): the trigger additionally becomes a real, directly navigable link \u2014 matching openaev's real MenuItemGroup pattern \u2014 without losing its flyout role; ignored while expanded, where the trigger stays a pure accordion toggle.`,
  status: "stable",
  version: "0.2.0",
  radixPrimitive: "@radix-ui/react-accordion, @radix-ui/react-dropdown-menu",
  // Fidelity re-pass (2026-07-23): node 6291:4003 is the first
  // independently reachable instance of "navbar submenu item" (an open
  // "Analyses" submenu with 6 real rows), replacing the original
  // symmetry-with-NavbarItem fallback (NavbarSubmenu.rfc.md §3.6). The
  // trigger row is still NavbarItem itself (composed via asChild) — this
  // node additionally confirms NavbarSubmenuItem's own, previously-unverified
  // anatomy (12px/secondary text, deeper indent — see NavbarSubmenu.figma.json).
  figmaLink: "https://www.figma.com/design/0PmhuZzF9XcaIEfMMW2a51/Design_System_2026?node-id=6291-4003",
  figmaNodeId: "6291:4003",
  accessibility: {
    wcag: "2.1 AA",
    wcagStatus: "pass",
    // NavbarSubmenuItem's anatomy/tokens/selected-state were confirmed (not
    // proposed by symmetry) against node 6291:4003 in the 2026-07-23 fidelity
    // re-pass: 12px/secondary text (not 14px/primary), pl-6/pr-4 (not
    // pl-4/pr-2), text stays default-secondary on selection (does not switch
    // to brand-primary). Fidelity re-pass #2 point 1 (node 5425:11518)
    // additionally removed the selected-state left border entirely — no
    // stroke is visible on the real selected row, only the background tint;
    // the original 2px-border proposal (itself already downgraded from an
    // initial 3px guess) is now confirmed to not exist at all. hover/focus
    // remain precedent-based proposals (no hover/focus instance was visible
    // in the extraction). Disabled is exempt from contrastPairs per WCAG
    // 1.4.3 Note 1 (same treatment as NavbarItem) and intentionally not
    // listed. The trigger's own default/hover/selected/disabled contrast is
    // already covered by NavbarItem.meta.ts — its focus ring is duplicated
    // below since it's a real combo NavbarSubmenu renders, matching the
    // RFC's own table entry (§5.4).
    //
    // Fidelity re-pass #3 point 2: NavbarSubmenuItem genuinely renders in
    // TWO different backdrop contexts, both real and both kept. In flyout
    // mode (collapsed Navbar) it sits inside DropdownMenuPrimitive.Content,
    // which keeps its own opaque bg-elevation-default fill (fidelity
    // re-pass #2 point 5, node 6301:10728 — unaffected by this change) —
    // the original "default label" entry still documents that context
    // unchanged. In accordion mode (expanded Navbar) AccordionPrimitive.
    // Content has no bg of its own (className="overflow-hidden" only), so
    // it sits directly on Navbar's own --gradient-default — the two new
    // "(accordion, stop N)" entries below cover that context per Rule 4.
    // The trigger row (an actual NavbarItem instance) always renders
    // inline, never inside a popup, so "trigger focus ring" is replaced
    // in full rather than added-alongside. hover label is unaffected
    // (opaque --bg-elevation-highlight) in both contexts. Both stops
    // verified (WCAG relative-luminance formula, both modes): dark
    // 8.99:1/8.43:1 (label), 8.94:1/8.38:1 (focus ring); light
    // 7.89:1/8.83:1 (label), 11.22:1/12.55:1 (focus ring) — all
    // comfortably above their thresholds.
    contrastPairs: [
      {
        id: "NavbarSubmenuItem default label (flyout)",
        fg: "--text-default-secondary",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: "NavbarSubmenuItem default label (accordion, stop 1)",
        fg: "--text-default-secondary",
        bg: "--bg-elevation-default-layer-0",
        minRatio: 4.5
      },
      {
        id: "NavbarSubmenuItem default label (accordion, stop 2)",
        fg: "--text-default-secondary",
        bg: "--bg-elevation-default-layer-0-gradient",
        minRatio: 4.5
      },
      {
        id: "NavbarSubmenuItem hover label",
        fg: "--text-default-secondary",
        bg: "--bg-elevation-highlight",
        minRatio: 4.5
      },
      {
        id: "trigger focus ring (stop 1)",
        fg: "--color-filigran-brand-primary",
        bg: "--bg-elevation-default-layer-0",
        minRatio: 3
      },
      {
        id: "trigger focus ring (stop 2)",
        fg: "--color-filigran-brand-primary",
        bg: "--bg-elevation-default-layer-0-gradient",
        minRatio: 3
      },
      {
        id: "NavbarSubmenuItem selected label",
        fg: "--text-default-secondary",
        bg: "--color-filigran-brand-primary-transparency",
        minRatio: 4.5
      }
    ],
    notes: `Expanded mode: WAI-ARIA disclosure pattern \u2014 aria-expanded/aria-controls on the trigger (Radix Accordion), no role="menu". Collapsed mode: Radix DropdownMenu imposes role="menu"/role="menuitem" \u2014 deliberately different role trees per mode, a disclosed trade-off (NavbarSubmenu.rfc.md \xA75.1/\xA75.3), not an oversight. aria-haspopup="menu" only applies to the flyout trigger (set automatically by Radix DropdownMenuTrigger) \u2014 the accordion trigger never has it, matching Radix AccordionTrigger's own behaviour. No live region for open/close changes \u2014 aria-expanded on the trigger is sufficient per @sandy's explicit arbitration. Keyboard: flyout opens on Enter/Space/ArrowDown/click/hover (hover adds a 150ms close-delay layer on top of Radix's free click/keyboard handling); Escape closes and returns focus to the trigger (Radix built-in). Accordion: ArrowUp/Down moves between triggers, Enter/Space toggles (Radix built-in); a bespoke effect closes the panel and returns focus to the trigger if the ancestor Navbar collapses while focus is inside it (not a Radix built-in). aria-current="page" drives NavbarSubmenuItem's selected state (background tint only, no border \u2014 fidelity re-pass #2 point 1), same aria-current mechanism as NavbarItem \u2014 no separate selected prop. When a descendant row is active, the trigger ITSELF also receives aria-current="page" (fidelity re-pass #2 point 3, node 5425:11518) so a collapsed Navbar still shows which section the user is in, even while the leaf row is hidden inside the closed flyout/accordion. Chevron rotation reuses NavbarItem's chevron span (150ms, motion-reduce:transition-none) \u2014 no color change, not a contrastPairs entry. The flyout panel itself has no border/shadow/radius/padding (fidelity re-pass #2 points 1/5, node 6301:10728: a flat 179px-wide fill, items running edge-to-edge) \u2014 the prior pass's border-elevation-subtle/shadow-global-shadow/rounded-sm/p-1 were all invented, not Figma-sourced. The 'NavbarSubmenuItem selected label' pair above verifies the --color-filigran-brand-primary-transparency background (a color-mix() composite): correcting an earlier claim in this file that this could not be expressed as a contrastPairs entry \u2014 check-contrast.ts's resolveOpaqueBackdrop() DOES resolve color-mix() foregrounds/backgrounds by compositing them over --bg-elevation-default, confirmed empirically (pnpm check:contrast output matches this file's own prior hand-computed numbers to within sRGB rounding: 7.82:1 dark / 6.48:1 light). No separate icon entry: NavbarSubmenuItem's optional leading icon has no color class of its own (inherits currentColor from the same --text-default-secondary on the parent <a>), so this one pair covers both label and icon \u2014 unlike NavbarItem, whose label (--text-default-primary) and icon (--text-default-secondary) differ. NavbarSubmenuItem's anatomy/tokens/selected-state are confirmed against node 6291:4003 (2026-07-23 fidelity re-pass) \u2014 no longer a symmetry-based proposal. Amendment (navbar-collapsed-submenu-link): when href/to is set, the collapsed trigger renders as a real <a> instead of a <button> but keeps the exact same NavbarItem icon/label/focus-ring markup and aria-haspopup/aria-expanded state \u2014 no new visual state, so no new contrastPairs entry. Keyboard: Enter navigates without opening the flyout (preventDefault stops Radix's own toggle, navigation replayed via a synthetic click); Space/ArrowDown/ArrowUp still open the flyout unchanged; a click never closes the flyout via the link itself (distinct from the non-link trigger, which does re-close on a second, separate click); Escape still closes and returns focus to the trigger. Ignored entirely in expanded mode \u2014 the trigger stays a pure accordion toggle there.`
  },
  render() {
    const sectionClass = "text-content-caption font-content-caption leading-content-caption tracking-content-caption text-default-secondary";
    return (0, import_react10.createElement)(
      "div",
      { className: "flex flex-col gap-6" },
      // Section 1: Expanded (accordion) mode
      (0, import_react10.createElement)("p", { className: sectionClass }, "Expanded Navbar (accordion mode)"),
      (0, import_react10.createElement)(
        "div",
        { className: "flex flex-col w-56 border border-elevation-subtle rounded-sm p-2" },
        (0, import_react10.createElement)(NavbarSubmenu, {
          label: "Knowledge",
          icon: (0, import_react10.createElement)(Icon, { name: "book", size: 16 }),
          defaultOpen: true,
          children: [
            (0, import_react10.createElement)(NavbarSubmenuItem, { key: "reports", href: "#", children: "Reports" }),
            (0, import_react10.createElement)(NavbarSubmenuItem, {
              key: "malware",
              href: "#",
              "aria-current": "page",
              children: "Malware"
            }),
            (0, import_react10.createElement)(NavbarSubmenuItem, {
              key: "observables",
              href: "#",
              icon: (0, import_react10.createElement)(Icon, { name: "settings", size: 16 }),
              children: "Observables"
            })
          ]
        })
      ),
      // Section 2: showIcon=false on a submenu row
      (0, import_react10.createElement)("p", { className: sectionClass }, "NavbarSubmenuItem showIcon=false"),
      (0, import_react10.createElement)(
        "div",
        { className: "flex flex-col w-56 border border-elevation-subtle rounded-sm p-2" },
        (0, import_react10.createElement)(NavbarSubmenu, {
          label: "Analysis",
          defaultOpen: true,
          children: (0, import_react10.createElement)(NavbarSubmenuItem, {
            href: "#",
            icon: (0, import_react10.createElement)(Icon, { name: "book", size: 16 }),
            showIcon: false,
            children: "Text-only row"
          })
        })
      ),
      // Section 3: Collapsed Navbar (flyout mode)
      (0, import_react10.createElement)("p", { className: sectionClass }, "Collapsed ancestor Navbar (flyout mode)"),
      (0, import_react10.createElement)(
        NavbarContext.Provider,
        { value: { collapsed: true } },
        (0, import_react10.createElement)(
          "div",
          { className: "flex flex-col w-12 border border-elevation-subtle rounded-sm p-2" },
          (0, import_react10.createElement)(NavbarSubmenu, {
            label: "Knowledge",
            icon: (0, import_react10.createElement)(Icon, { name: "book", size: 16 }),
            defaultOpen: true,
            children: [
              (0, import_react10.createElement)(NavbarSubmenuItem, { key: "reports", href: "#", children: "Reports" }),
              (0, import_react10.createElement)(NavbarSubmenuItem, {
                key: "malware",
                href: "#",
                "aria-current": "page",
                children: "Malware"
              })
            ]
          })
        )
      ),
      // Section 4: asChild (router Link) on a submenu row
      (0, import_react10.createElement)("p", { className: sectionClass }, "NavbarSubmenuItem asChild (router Link)"),
      (0, import_react10.createElement)(
        "div",
        { className: "flex flex-col w-56 border border-elevation-subtle rounded-sm p-2" },
        (0, import_react10.createElement)(NavbarSubmenu, {
          label: "Settings",
          defaultOpen: true,
          children: (0, import_react10.createElement)(NavbarSubmenuItem, {
            asChild: true,
            children: (0, import_react10.createElement)("a", { href: "#", "aria-current": "page" }, "Users")
          })
        })
      ),
      // Section 5: Collapsed Navbar, trigger with href (link + flyout dual role)
      (0, import_react10.createElement)(
        "p",
        { className: sectionClass },
        "Collapsed ancestor Navbar, trigger with href (link + flyout)"
      ),
      (0, import_react10.createElement)(
        NavbarContext.Provider,
        { value: { collapsed: true } },
        (0, import_react10.createElement)(
          "div",
          { className: "flex flex-col w-12 border border-elevation-subtle rounded-sm p-2" },
          (0, import_react10.createElement)(NavbarSubmenu, {
            label: "Knowledge",
            icon: (0, import_react10.createElement)(Icon, { name: "book", size: 16 }),
            href: "#",
            defaultOpen: true,
            children: [
              (0, import_react10.createElement)(NavbarSubmenuItem, { key: "reports", href: "#", children: "Reports" }),
              (0, import_react10.createElement)(NavbarSubmenuItem, {
                key: "malware",
                href: "#",
                "aria-current": "page",
                children: "Malware"
              })
            ]
          })
        )
      )
    );
  },
  examples: [
    '// Trigger row is a NavbarItem composed internally \u2014 pass label/icon, never a raw child\n<NavbarSubmenu label="Knowledge" icon={<Icon name="book" size={16} />}>\n  <NavbarSubmenuItem href="/reports">Reports</NavbarSubmenuItem>\n  <NavbarSubmenuItem href="/malware" aria-current="page">Malware</NavbarSubmenuItem>\n</NavbarSubmenu>',
    `// Mode (accordion vs flyout) is always derived from the ancestor Navbar's collapse state \u2014 never a manual prop
<NavbarSubmenu label="Analysis" showIcon={false}>...</NavbarSubmenu>`,
    '// asChild merges NavbarSubmenuItem styles onto a router Link, same pattern as NavbarItem/Button\n<NavbarSubmenuItem asChild>\n  <Link to="/users" aria-current={isActive ? "page" : undefined}>Users</Link>\n</NavbarSubmenuItem>',
    '// Controlled open state\n<NavbarSubmenu label="Knowledge" open={isOpen} onOpenChange={setIsOpen}>...</NavbarSubmenu>',
    `// href/to: collapsed-mode trigger becomes a real link IN ADDITION to a flyout trigger
// (matches openaev's MenuItemGroup: component={Link} to={item.path} when collapsed).
// Ignored while expanded \u2014 the trigger stays a pure accordion toggle there.
<NavbarSubmenu label="Knowledge" icon={<Icon name="book" size={16} />} href="/knowledge">
  <NavbarSubmenuItem href="/knowledge/reports">Reports</NavbarSubmenuItem>
</NavbarSubmenu>`
  ],
  variants: [
    "default",
    "hover",
    "selected",
    "disabled",
    "expanded (accordion)",
    "collapsed (flyout)"
  ],
  sizes: [],
  props: {
    "NavbarSubmenu.label": "ReactNode \u2014 accessible name for the trigger row (rendered via NavbarItem)",
    "NavbarSubmenu.icon": "ReactNode \u2014 optional leading icon on the trigger row",
    "NavbarSubmenu.open": "boolean \u2014 controlled open state, omit for uncontrolled",
    "NavbarSubmenu.defaultOpen": "boolean \u2014 initial open state (uncontrolled), default false",
    "NavbarSubmenu.onOpenChange": "(open: boolean) => void",
    "NavbarSubmenu.href": "string \u2014 collapsed mode ONLY: trigger becomes a real, directly navigable link (a real <a>, matches openaev's MenuItemGroup pattern) while still working as a flyout trigger (hover/focus opens it, ArrowDown still browses it). Ignored in expanded mode (pure toggle there). Mutually exclusive with `to` (href wins, dev-only console.warn if both are set).",
    "NavbarSubmenu.to": "string \u2014 alias for href, same collapsed-only behavior and priority rules",
    "NavbarSubmenu.children": "NavbarSubmenuItem elements",
    "NavbarSubmenuItem.asChild": "boolean \u2014 merge styles onto a router Link, same pattern as NavbarItem/Button",
    "NavbarSubmenuItem.icon": "ReactNode \u2014 leading icon, hidden with no fallback when showIcon is false",
    "NavbarSubmenuItem.showIcon": "boolean \u2014 mirrors opencti's real submenu_show_icons preference. Default true. Unlike NavbarItem, never forced true \u2014 submenu labels never hide.",
    "NavbarSubmenuItem.aria-current": '"page" (drives the selected visual state)',
    "NavbarSubmenuItem.children": "ReactNode \u2014 row label"
  },
  // Fidelity re-pass #2 point 9: docs-site presentation only — see
  // Navbar.meta.ts.
  docsGroup: {
    id: "navbar",
    label: "Navbar",
    tabLabel: "Navbar Submenu"
  }
};

// src/components/product-switcher/ProductSwitcher.meta.ts
var import_react11 = require("react");
var basePath = (typeof process !== "undefined" ? process.env.NEXT_PUBLIC_BASE_PATH ?? process.env.BASE_PATH : void 0) ?? "";
function productLogo(name) {
  return (0, import_react11.createElement)("img", {
    src: `${basePath}/product-logos/${name}.svg`,
    alt: "",
    className: "h-7 w-auto"
  });
}
var ProductSwitcherMeta = {
  name: "ProductSwitcher",
  description: "The Navbar header control exposing links to sibling Filigran products (OpenAEV, XTM Hub, and others) \u2014 a static menu of destination links, never a stateful 'current product' selector (no selected/active option). Each option is independently external (opens in a new tab, with a visible external-link icon and a visually-hidden accessible-name suffix) or internal (same-tab navigation), mirroring opencti's real per-item polymorphism (e.g. XTM Hub linking internally to a connection page when not yet connected). Built on @radix-ui/react-dropdown-menu \u2014 opens on click/keyboard only (never hover), so unlike NavbarSubmenu there is no hover/click race to guard against.",
  status: "stable",
  version: "0.1.0",
  radixPrimitive: "@radix-ui/react-dropdown-menu",
  // No dedicated Figma component exists for ProductSwitcher (ProductSwitcher.rfc.md
  // §3.1/§3.6) — confirmed via repeated figma-search_design_system queries across
  // two sessions. Points at the Navbar frame's productLogo header instance, which
  // is reliable for structure/behavior only (chevron placement/presence) — @sandy
  // confirmed neither this mockup nor the reference screenshot reflect real colors.
  figmaLink: "https://www.figma.com/design/0PmhuZzF9XcaIEfMMW2a51/Design_System_2026?node-id=2837-12856",
  figmaNodeId: "2837:12856",
  accessibility: {
    wcag: "2.1 AA",
    wcagStatus: "pass",
    // Transcribed verbatim from ProductSwitcher.rfc.md §5.4. No "selected"/"disabled"
    // rows — neither state exists in the real component (RFC §2.3/§3.4/§4.2), not a
    // gap. ⚠️ All tokens reused-for-consistency from NavbarItem, not independently
    // sourced for this component (RFC §3.4) — "to revalidate" once real design input
    // (colors) is available for ProductSwitcher specifically.
    //
    // Fidelity re-pass #3 point 2: "dropdown item"/"external-link icon" render
    // inside DropdownMenuPrimitive.Content, a portalled popup with its own
    // opaque bg-elevation-default fill (same floating-surface pattern as
    // NavbarSubmenu's flyout, fidelity re-pass #2 point 5) — unaffected by
    // Navbar's background change, left as-is. The trigger itself (the
    // product-logo button always visible in Navbar's header slot) renders
    // inline, directly on Navbar's own --gradient-default, so "trigger focus
    // ring" is replaced with one entry per gradient stop per Rule 4 of
    // check-contrast.ts. Both stops verified (WCAG relative-luminance
    // formula): dark 8.94:1/8.38:1, light 11.22:1/12.55:1 — comfortably
    // above the 3:1 threshold.
    contrastPairs: [
      {
        id: "dropdown item, default",
        fg: "--text-default-primary",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: "dropdown item, hover",
        fg: "--text-default-primary",
        bg: "--bg-elevation-highlight",
        minRatio: 4.5
      },
      {
        id: "external-link icon",
        fg: "--icon-default",
        bg: "--bg-elevation-default",
        minRatio: 3
      },
      {
        id: "trigger focus ring (stop 1)",
        fg: "--color-filigran-brand-primary",
        bg: "--bg-elevation-default-layer-0",
        minRatio: 3
      },
      {
        id: "trigger focus ring (stop 2)",
        fg: "--color-filigran-brand-primary",
        bg: "--bg-elevation-default-layer-0-gradient",
        minRatio: 3
      }
    ],
    notes: 'Trigger renders a native <button> (Radix DropdownMenu.Trigger) with aria-haspopup="menu"/aria-expanded automatic; Content is role="menu" with role="menuitem" children \u2014 an appropriate fit since each item is a discrete action/destination (RFC \xA75.1). Keyboard entirely inherited from Radix DropdownMenu (Enter/Space/arrow keys open/navigate, Escape closes and returns focus to the trigger) \u2014 no custom keyboard code (RFC \xA75.2). Trigger accessible name is the required `label` prop, deliberately NOT reusing opencti\'s real "Collapse"/"Expand" chevron strings, which clash with Navbar\'s own, unrelated collapse/expand toggle terminology (RFC \xA75.3) \u2014 e.g. "More Filigran products" instead. External options get an accessible-name suffix ("${label} (opens in a new tab)", visually-hidden), not just a visual icon \u2014 corrects a real WCAG gap in opencti\'s current icon-only implementation rather than reproducing it (RFC \xA72.3/\xA72.4/\xA75.3); the visible external-link icon is aria-hidden to avoid double-announcement. No live region (aria-expanded on the trigger is sufficient, same reasoning as NavbarSubmenu.rfc.md \xA75.3). No aria-current \u2014 there is no \'current selection\' concept in this component (RFC \xA75.3), unlike NavbarItem/NavbarSubmenuItem. Trigger chevron is absent from the collapsed Figma frame \u2014 same gap as Navbar.rfc.md \xA73.6 \u2014 resolved by keeping the logo itself focusable/interactive in both collapse states (RFC \xA73.6); only the chevron is conditionally hidden. `logo` (trigger and per-option) is always rendered aria-hidden \u2014 the accessible name comes from `label`/`option.label`, never from whatever alt text a consumer\'s logo asset may or may not carry. `to` (internal destination) renders a same-tab <a href> \u2014 the design system has no dependency on any specific router (unlike opencti\'s real <Link to>), so true zero-reload client-side navigation depends on the consuming app\'s router intercepting anchor clicks; documented trade-off, not a silent gap (see PR Final Summary).'
  },
  render() {
    const sectionClass = "text-content-caption font-content-caption leading-content-caption tracking-content-caption text-default-secondary";
    const options = [
      {
        id: "openaev",
        label: "OpenAEV",
        logo: productLogo("openaev"),
        tooltip: "Platform connected",
        href: "https://example.com/openaev"
      },
      {
        id: "xtmhub",
        label: "XTM Hub",
        logo: productLogo("xtmhub"),
        tooltip: "Get XTM Hub now",
        to: "/dashboard/settings/experience"
      },
      {
        id: "xtmone",
        label: "XTM One",
        logo: productLogo("xtmone"),
        href: "https://example.com/xtmone"
      },
      {
        id: "opengrc",
        label: "OpenGRC",
        logo: productLogo("opengrc"),
        href: "https://example.com/opengrc"
      }
    ];
    return (0, import_react11.createElement)(
      "div",
      { className: "flex flex-col gap-6" },
      // Section 1: Expanded Navbar (chevron visible)
      (0, import_react11.createElement)("p", { className: sectionClass }, "Expanded Navbar (logo + chevron trigger)"),
      (0, import_react11.createElement)(
        "div",
        { className: "flex flex-col w-56 border border-elevation-subtle rounded-sm p-2" },
        (0, import_react11.createElement)(ProductSwitcher, {
          logo: productLogo("opencti"),
          label: "More Filigran products",
          options
        })
      ),
      // Section 2: Options detail (rendered open, out-of-menu, for docs legibility)
      // Mirrors ProductSwitcherOptionItem's real DOM structure exactly: logo is
      // the sole VISIBLE content (aria-hidden), label is sr-only, external-link
      // icon only for href-based (external) options — see ProductSwitcher.tsx.
      // A visible label span here would be redundant AND inaccurate: it doesn't
      // match what real consumers see, and the logo image already contains the
      // product wordmark as pixels, so a second text label duplicated it and
      // overflowed the demo's fixed width.
      (0, import_react11.createElement)("p", { className: sectionClass }, "Option anatomy (external vs internal destination)"),
      (0, import_react11.createElement)(
        "div",
        { className: "flex flex-col w-56 border border-elevation-subtle rounded-sm p-1 gap-1" },
        (0, import_react11.createElement)(
          "div",
          { className: "flex w-full items-center gap-2 h-9 px-2 rounded-sm text-default-primary" },
          (0, import_react11.createElement)(
            "span",
            { className: "inline-flex shrink-0", "aria-hidden": "true" },
            productLogo("openaev")
          ),
          (0, import_react11.createElement)("span", { className: "sr-only" }, "OpenAEV (opens in a new tab)"),
          (0, import_react11.createElement)(
            "span",
            { className: "ml-auto inline-flex shrink-0", "aria-hidden": "true" },
            (0, import_react11.createElement)(Icon, { name: "external-link", size: 16 })
          )
        ),
        (0, import_react11.createElement)(
          "p",
          { className: `${sectionClass} px-2` },
          '\u2191 external (href) \u2014 target=_blank, visible external-link icon, sr-only "(opens in a new tab)" suffix'
        ),
        (0, import_react11.createElement)(
          "div",
          { className: "flex w-full items-center gap-2 h-9 px-2 rounded-sm text-default-primary" },
          (0, import_react11.createElement)(
            "span",
            { className: "inline-flex shrink-0", "aria-hidden": "true" },
            productLogo("xtmhub")
          ),
          (0, import_react11.createElement)("span", { className: "sr-only" }, "XTM Hub")
        ),
        (0, import_react11.createElement)(
          "p",
          { className: `${sectionClass} px-2` },
          "\u2191 internal (to) \u2014 same-tab anchor, no external-link icon"
        )
      )
    );
  },
  examples: [
    '// options is a flat, static list \u2014 no selected/active state to track\n<ProductSwitcher\n  logo={<Icon name="custom/opencti" size={20} />}\n  label="More Filigran products"\n  options={[\n    { id: "openaev", label: "OpenAEV", logo: <Icon name="custom/openaev" size={20} />, href: "https://openaev.example.com", tooltip: "Platform connected" },\n    { id: "xtmhub", label: "XTM Hub", logo: <XtmHubLogo />, to: "/dashboard/settings/experience", tooltip: "Get XTM Hub now" },\n  ]}\n/>',
    "// href \u2192 external (target=_blank, rel=noopener noreferrer, external-link icon + AT-only new-tab suffix)\n// to \u2192 internal (same-tab <a href>) \u2014 mutually exclusive per option"
  ],
  variants: ["default", "hover", "focus", "external link item", "internal link item"],
  sizes: [],
  props: {
    "ProductSwitcher.logo": "ReactNode \u2014 current product's logo, always-visible trigger content (aria-hidden; label carries the a11y name)",
    "ProductSwitcher.label": 'string \u2014 accessible name for the trigger button, not rendered as visible text (e.g. "More Filigran products")',
    "ProductSwitcher.options": "ProductSwitcherOption[] \u2014 other Filigran products/destinations",
    "ProductSwitcherOption.id": "string",
    "ProductSwitcherOption.label": "string \u2014 accessible name for this link, not rendered as visible text; base for the AT-only new-tab suffix when href is set",
    "ProductSwitcherOption.logo": "ReactNode \u2014 product wordmark/logo, the item's sole visible content (aria-hidden)",
    "ProductSwitcherOption.tooltip": "string (optional) \u2014 supplementary tooltip copy (e.g. connection status); copy stays the consuming product's responsibility",
    "ProductSwitcherOption.href": 'string (optional) \u2014 external destination; renders target="_blank" + rel="noopener noreferrer" + visible external-link icon + AT-only "(opens in a new tab)" suffix. Mutually exclusive with `to`.',
    "ProductSwitcherOption.to": "string (optional) \u2014 internal destination (same-tab <a href>, RFC \xA74.1). Mutually exclusive with `href`."
  },
  // Docs-site presentation only (fidelity re-pass #2 point 9) — collapses the
  // 4 navbar-family components' separate nav entries/pages into one "Navbar"
  // page with a Tabs-per-component layout. Does NOT merge the components
  // themselves (still distinct exports/files); see docs/app/layout.tsx and
  // docs/app/components/[name]/page.tsx.
  docsGroup: { id: "navbar", label: "Navbar", tabLabel: "Product Switcher" }
};

// src/components/search-field/SearchField.tsx
var React17 = __toESM(require("react"));
var import_class_variance_authority7 = require("class-variance-authority");
var import_jsx_runtime14 = (
  // eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-noninteractive-element-interactions
  require("react/jsx-runtime")
);
var searchFieldVariants = (0, import_class_variance_authority7.cva)(
  [
    "inline-flex items-center rounded-sm transition",
    "border border-transparent",
    "bg-input-default",
    "font-sans-plex font-normal",
    "focus-within:outline-none focus-within:ring-2 focus-within:ring-focus focus-within:ring-offset-2 focus-within:ring-offset-focus",
    "hover:border-input-hover"
  ],
  {
    variants: {
      size: {
        md: "h-9 w-55 gap-2 px-2 text-3",
        sm: "h-7 w-50 gap-2 px-2 text-2"
      }
    },
    defaultVariants: {
      size: "md"
    }
  }
);
var SearchField = React17.forwardRef(
  ({
    className,
    size,
    value,
    defaultValue: defaultValue2,
    onChange,
    onSubmit,
    onClear,
    searchOption,
    fullWidth = false,
    placeholder = "Search\u2026",
    disabled = false,
    ...props
  }, ref) => {
    const inputRef = React17.useRef(null);
    const combinedRef = React17.useCallback(
      (element) => {
        if (typeof ref === "function") ref(element);
        else if (ref) ref.current = element;
        inputRef.current = element;
      },
      [ref]
    );
    const handleKeyDown = (e) => {
      if (e.key === "Enter" && onSubmit) {
        e.preventDefault();
        onSubmit(inputRef.current?.value ?? "");
      }
      if (e.key === "Escape" && onClear) {
        onClear();
      }
      props.onKeyDown?.(e);
    };
    const handleWrapperClick = (e) => {
      if (e.target.closest("[data-search-option]")) return;
      inputRef.current?.focus();
    };
    return /* @__PURE__ */ (0, import_jsx_runtime14.jsxs)(
      "div",
      {
        role: "search",
        "aria-label": props["aria-label"] ?? "Search",
        className: cn(
          searchFieldVariants({ size }),
          disabled && "bg-input-disabled pointer-events-none",
          fullWidth && "w-full",
          "cursor-text",
          className
        ),
        onClick: handleWrapperClick,
        children: [
          /* @__PURE__ */ (0, import_jsx_runtime14.jsx)(
            Icon,
            {
              name: "search",
              size: size === "sm" ? 16 : 24,
              "aria-hidden": true,
              className: cn("shrink-0", disabled ? "text-input-disabled" : "text-input-placeholder")
            }
          ),
          /* @__PURE__ */ (0, import_jsx_runtime14.jsx)(
            "input",
            {
              ref: combinedRef,
              type: "search",
              ...props["aria-label"] && { "aria-label": props["aria-label"] },
              ...value !== void 0 ? { value } : { defaultValue: defaultValue2 },
              onChange,
              placeholder,
              disabled,
              className: cn(
                "flex-1 min-w-0 bg-transparent outline-none",
                "placeholder:text-input-placeholder",
                "leading-normal",
                disabled ? "text-input-disabled" : "text-input-placeholder",
                "focus-visible:outline-none"
              ),
              ...props,
              onKeyDown: handleKeyDown
            }
          ),
          searchOption && /* @__PURE__ */ (0, import_jsx_runtime14.jsx)(
            "div",
            {
              "data-search-option": true,
              className: "flex items-center shrink-0",
              ...disabled && { inert: true, "aria-hidden": true },
              children: searchOption
            }
          )
        ]
      }
    );
  }
);
SearchField.displayName = "SearchField";

// src/components/search-field/SearchField.meta.ts
var React18 = __toESM(require("react"));
var SearchFieldMeta = {
  name: "SearchField",
  description: 'Specialized text input for search and filtering. Wraps a native <input type="search"> with a leading search icon, optional trailing action slot (searchOption), and Enter-to-submit behavior. Use for data table filtering, list search, and platform-wide search bars.',
  status: "stable",
  version: "0.1.0",
  radixPrimitive: "none",
  figmaLink: "https://www.figma.com/design/0PmhuZzF9XcaIEfMMW2a51/Design_System_2026?node-id=2682-8640",
  figmaNodeId: "2682:8640",
  examples: [
    `<SearchField aria-label="Search items" onSubmit={(value) => console.log(value)} placeholder="Search\u2026" />`,
    `<SearchField aria-label="Filter results" size="sm" onChange={(e) => console.log(e.target.value)} />`,
    `<SearchField aria-label="Search with clear" fullWidth onSubmit={handleSearch} searchOption={<IconButton icon={<Icon name="x" size={16} />} aria-label="Clear search" onClick={handleClear} variant="default" priority="tertiary" size="sm" />} />`,
    `<SearchField aria-label="Search unavailable" disabled placeholder="Search unavailable" />`
  ],
  variants: ["default"],
  sizes: ["sm", "md"],
  props: {
    value: "string \u2014 Controlled value",
    defaultValue: "string \u2014 Default value (uncontrolled)",
    onChange: "(event: ChangeEvent<HTMLInputElement>) => void \u2014 Fires on each keystroke",
    onSubmit: "(value: string) => void \u2014 Fires when user presses Enter",
    onClear: "() => void \u2014 Fires when Escape is pressed or clear action is triggered programmatically",
    searchOption: "ReactNode \u2014 Trailing action slot (Figma: searchOption). Accepts IconButton(s) for clear, filter, etc. Consumer is responsible for wiring onClick to onClear when providing a clear button.",
    size: '"sm" | "md" \u2014 Size variant (default: "md")',
    fullWidth: "boolean \u2014 Expand to fill container width",
    placeholder: 'string \u2014 Placeholder text (default: "Search\u2026")',
    disabled: "boolean \u2014 Disabled state",
    className: "string \u2014 Additional CSS classes"
  },
  accessibility: {
    wcag: "2.1 AA",
    wcagStatus: "pass",
    contrastPairs: [
      {
        id: "placeholder",
        fg: "--text-input-placeholder",
        bg: "--bg-input-default",
        minRatio: 4.5
      },
      {
        id: "placeholder-disabled",
        fg: "--text-input-disabled",
        bg: "--bg-input-disabled",
        minRatio: 4.5
      },
      {
        id: "search-icon",
        fg: "--text-input-placeholder",
        bg: "--bg-input-default",
        minRatio: 3
      },
      {
        id: "hover-border",
        fg: "--border-input-hover",
        bg: "--bg-input-default",
        minRatio: 3
      },
      {
        id: "focus-ring",
        fg: "--color-filigran-brand-primary",
        bg: "--bg-elevation-default",
        minRatio: 3
      }
    ],
    notes: 'Native <input type="search"> wrapped in role="search" landmark. Search icon is decorative (aria-hidden). Enter submits, Escape fires onClear. Focus indication uses ring-focus utility (2px ring, brand-primary) per Figma spec and AGENTS.md \xA76.2 \u2014 consistent with other components. searchOption slot icons must carry their own aria-label. searchOption is inert when disabled (no keyboard access).'
  },
  render: () => React18.createElement(
    "div",
    { className: "flex flex-col gap-4" },
    React18.createElement(SearchField, {
      placeholder: "Search\u2026",
      size: "md",
      "aria-label": "Search medium"
    }),
    React18.createElement(SearchField, {
      placeholder: "Search\u2026",
      size: "sm",
      "aria-label": "Search small"
    }),
    React18.createElement(SearchField, {
      placeholder: "Search unavailable",
      disabled: true,
      "aria-label": "Search disabled"
    })
  )
};

// src/components/switch/Switch.tsx
var React19 = __toESM(require("react"));
var SwitchPrimitive = __toESM(require("@radix-ui/react-switch"));
var import_jsx_runtime15 = require("react/jsx-runtime");
var Switch = React19.forwardRef(({ className, wrapperClassName, label, id, disabled, ...props }, ref) => {
  const generatedId = React19.useId();
  const switchId = id ?? generatedId;
  const control = /* @__PURE__ */ (0, import_jsx_runtime15.jsx)(
    SwitchPrimitive.Root,
    {
      ref,
      id: switchId,
      disabled,
      className: cn(
        "group relative inline-flex h-5 w-9 shrink-0 items-center rounded-full p-0.5 transition-colors",
        "focus-visible:outline-none focus-visible:ring-2 ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-focus",
        "disabled:pointer-events-none data-[disabled]:pointer-events-none disabled:opacity-40 data-[disabled]:opacity-40",
        "data-[state=unchecked]:bg-elevation-disabled data-[state=checked]:bg-feedback-info-secondary",
        className
      ),
      ...props,
      children: /* @__PURE__ */ (0, import_jsx_runtime15.jsx)(
        SwitchPrimitive.Thumb,
        {
          className: cn(
            "pointer-events-none flex size-4 shrink-0 items-center justify-center rounded-full",
            "transition-transform data-[state=checked]:translate-x-4",
            // Hover halo (Figma: checked + hover only — no equivalent halo on
            // the unchecked side, see Switch.figma.json). Anchored on ROOT's
            // own hover state (group-hover:), never a direct hover: on the
            // Thumb itself: the Thumb has pointer-events-none, so it can
            // never be the pointer's hit-test target and therefore can never
            // enter :hover through real mouse input — a bare
            // data-[state=checked]:hover:ring-8 here is unreachable dead code
            // (Copilot automated review, PR #58). Ring utilities are
            // box-shadow based, so they layer over the thumb without an
            // extra DOM node.
            "group-hover:group-data-[state=checked]:ring-8 group-hover:group-data-[state=checked]:ring-filigran-brand-primary-transparency"
          ),
          children: /* @__PURE__ */ (0, import_jsx_runtime15.jsx)("svg", { viewBox: "0 0 16 16", width: 16, height: 16, "aria-hidden": "true", className: "block size-4", children: /* @__PURE__ */ (0, import_jsx_runtime15.jsx)(
            "circle",
            {
              cx: "8",
              cy: "8",
              r: "8",
              className: cn(
                "group-data-[state=unchecked]:fill-default",
                "group-data-[state=checked]:fill-highlight",
                // Disabled + unchecked thumb is a distinct Figma-bound
                // variable (--icon-disabled), not an opacity tint of
                // fill-default — verified via variable binding, not sampled.
                "group-data-[disabled]:group-data-[state=unchecked]:fill-disabled"
                // Disabled + checked thumb reuses --icon-highlight (same
                // token as active) and is intentionally NOT separately
                // dimmed here: Root's own disabled:opacity-40 /
                // data-[disabled]:opacity-40 (above) already applies to the
                // whole control — including this circle, a DOM descendant —
                // for every disabled state, checked or not. An earlier
                // version of this file ALSO carried a second, nested opacity
                // rule scoped to just the "checked" group-data state right
                // here (deliberately not spelled out as one literal class
                // string in this comment — Tailwind's content scanner reads
                // raw file text, comments included, so writing it verbatim
                // would compile a real, unused CSS rule for a class no
                // longer applied anywhere, confirmed via @tailwindcss/cli);
                // it did not merge with or cancel Root's opacity — CSS
                // opacity compounds multiplicatively across nested elements,
                // so the two rules stacked to ~16% (0.4 * 0.4) real rendered
                // opacity instead of the intended/documented 40%, and also
                // silently re-dimmed the DISTINCT fill-disabled token above
                // (unchecked+disabled), defeating its own "not an opacity
                // tint" rationale. Fix authorized by Sandy before committing,
                // since it changes rendered visual behavior (Copilot
                // automated review, PR #58, suppressed/low-confidence
                // comment). See scripts/lib/switch-disabled-opacity.test.ts
                // for the non-regression proof and Switch.figma.json's
                // "Thumb=Disabled, Checked" note for the full history.
              )
            }
          ) })
        }
      )
    }
  );
  if (!label) {
    return control;
  }
  return /* @__PURE__ */ (0, import_jsx_runtime15.jsxs)(
    "label",
    {
      htmlFor: switchId,
      className: cn(
        "inline-flex items-center gap-2",
        disabled ? "cursor-not-allowed" : "cursor-pointer",
        wrapperClassName
      ),
      children: [
        control,
        /* @__PURE__ */ (0, import_jsx_runtime15.jsx)(
          "span",
          {
            className: cn(
              "content-compact-medium",
              disabled ? "text-input-disabled" : "text-input-placeholder"
            ),
            children: label
          }
        )
      ]
    }
  );
});
Switch.displayName = "Switch";

// src/components/switch/Switch.meta.ts
var import_react12 = require("react");
function cell(labelText, props) {
  return (0, import_react12.createElement)(
    "div",
    { className: "flex flex-col items-start gap-2" },
    (0, import_react12.createElement)(
      "span",
      {
        className: "text-content-caption font-content-caption leading-content-caption tracking-content-caption text-default-secondary"
      },
      labelText
    ),
    (0, import_react12.createElement)(Switch, props)
  );
}
var SwitchMeta = {
  name: "Switch",
  description: 'Boolean on/off toggle built on @radix-ui/react-switch. Renders a native <button> (role="switch", aria-checked, Space/Enter keyboard activation for free) with an optional integrated label (Figma: label is part of the component, not a composed sibling \u2014 RFC arbitration Q4). Size and color are intentionally fixed: this version ships exactly the one size and one color the Figma spec defines (RFC arbitration Q1) \u2014 see Switch.rfc.md for the product evidence behind that exclusion.',
  status: "beta",
  version: "0.1.0",
  radixPrimitive: "@radix-ui/react-switch",
  figmaLink: "https://www.figma.com/design/0PmhuZzF9XcaIEfMMW2a51/Design_System_2026?node-id=2686-5152",
  figmaNodeId: "2686:5152",
  accessibility: {
    wcag: "2.1 AA",
    wcagStatus: "fail",
    contrastPairs: [
      // ── Normative pairs (Understanding SC 1.4.11, "Toggle button" example,
      // Figure 23: "the toggle button's internal background has a good
      // contrast with the external background. Also, the round toggle
      // within contrasts with the internal background.") — this is the
      // controlling worked example for a toggle, and it tests exactly
      // these two relationships (track-vs-page-bg, thumb-vs-track), for
      // BOTH toggle positions since state must stay identifiable in either.
      {
        id: "track checked vs bg",
        fg: "--color-feedback-info-secondary",
        bg: "--bg-elevation-default",
        minRatio: 3
      },
      {
        id: "track unchecked vs bg",
        fg: "--bg-elevation-disabled",
        bg: "--bg-elevation-default",
        minRatio: 3
      },
      {
        id: "thumb checked vs track checked",
        fg: "--icon-highlight",
        bg: "--color-feedback-info-secondary",
        minRatio: 3
      },
      {
        id: "thumb unchecked vs track unchecked",
        fg: "--icon-default",
        bg: "--bg-elevation-disabled",
        minRatio: 3
      },
      // ── Informative pairs (not the normative test, kept as positive
      // evidence — see notes: the thumb is what actually makes the
      // control identifiable against the page, independent of the track
      // failures below).
      {
        id: "thumb checked vs bg",
        fg: "--icon-highlight",
        bg: "--bg-elevation-default",
        minRatio: 3
      },
      {
        id: "thumb unchecked vs bg",
        fg: "--icon-default",
        bg: "--bg-elevation-default",
        minRatio: 3
      },
      // ── Standard focus ring (AGENTS.md "Interactive Elements") and label
      // text (Figma: text-input-placeholder/-disabled, verified via
      // variable binding — same binary as SearchField's placeholder/
      // disabled pattern).
      {
        id: "focus ring vs bg",
        fg: "--color-filigran-brand-primary",
        bg: "--bg-elevation-default",
        minRatio: 3
      },
      {
        id: "label text",
        fg: "--text-input-placeholder",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      {
        id: "label text disabled",
        fg: "--text-input-disabled",
        bg: "--bg-elevation-default",
        minRatio: 4.5
      },
      // ── Hover halo (decorative, hover-only — see notes for the WCAG
      // exemption this relies on). Declared so Rule 5's solid-color
      // coverage has a real entry for --color-filigran-brand-primary-
      // transparency; not part of the 4-failure allow-list below.
      {
        id: "hover halo vs bg",
        fg: "--color-filigran-brand-primary-transparency",
        bg: "--bg-elevation-default",
        minRatio: 3
      }
    ],
    notes: `Native <button> (Radix Root) with role="switch"/aria-checked, native Space/Enter keyboard activation, controlled/uncontrolled value (checked/defaultChecked + onCheckedChange). forwardRef exposed on the underlying <button> (repo convention for form-field components). Label uses <label htmlFor>/<button id> native association. Focus ring uses the standard ring-focus/ring-offset-focus pattern (AGENTS.md 'Interactive Elements') \u2014 no deviation, no justification needed. KNOWN, ACCEPTED WCAG 1.4.11 FAILURES (4 pairs below threshold in at least one mode) \u2014 shipped as-is with the current Figma color tokens, allow-listed in scripts/wcag-allowlist.json (approved by Sandy, lead design, expires 2026-08-31), resolution deferred to a global color-token pass (also covers similar known failures on two other already-shipped components; not a Switch-local fix): track checked vs bg \u2014 dark #0079a8 on #070d18 = 3.98:1 \u2705 / light #42caff on #f2f2f3 = 1.69:1 \u274C (fails 3:1). track unchecked vs bg \u2014 dark #18191b on #070d18 = 1.11:1 \u274C / light #c8d6ee on #f2f2f3 = 1.31:1 \u274C (fails 3:1 in both modes). thumb checked vs track checked \u2014 dark #0fbcff on #0079a8 = 2.25:1 \u274C (fails 3:1) / light #0015a8 on #42caff = 6.65:1 \u2705. thumb unchecked vs track unchecked \u2014 dark #f2f2f3 on #18191b = 15.72:1 \u2705 / light #18191b on #c8d6ee = 11.99:1 \u2705 (declared for Figure-23 completeness; not a failure). IDENTIFIABILITY IS NOT LOST: per Understanding SC 1.4.11's own state-indicator logic, the thumb (not just the track) carries the checked/unchecked state, and thumb-vs-page-background is comfortably above threshold in every case measured \u2014 thumb checked vs bg: dark 8.94:1 / light 11.22:1; thumb unchecked vs bg: dark 17.38:1 / light 15.72:1. This is why the component remains visually identifiable end-to-end despite the track-level failures above (verified against the toggle button worked example, not assumed). Disabled state is exempt from all of the above (WCAG 1.4.3 Note 1, inactive UI components) \u2014 the disabled+checked thumb reuses --icon-highlight at reduced opacity (opacity-40, not a Figma token \u2014 no token exists for this specific ratio; flagged in Switch.figma.json) and disabled+unchecked uses the distinct --icon-disabled variable (confirmed via Figma variable binding, not an opacity tint of the enabled fill). Hover halo (ring-8 ring-filigran-brand-primary-transparency, checked+hover only) computes well under 3:1 against the page background in both modes (dark 1.15:1, light 1.22:1) by construction \u2014 a 10%-opacity accent will always read close to its backdrop. This is not a WCAG failure: Understanding SC 1.4.11's 'Hover states' guidance is explicit that 'additional author-supplied visual treatments for hover... can be considered supplemental and do not themselves need to contrast 3:1 against the background' \u2014 the halo is exactly such a supplemental treatment (the thumb's own position/fill already carries the state; the halo adds nothing required). Declared here only for Rule 5 solid-color coverage, not counted among the 4 allow-listed failures. Halo geometry (ring-8) is a bounding-box approximation from the Figma export's flattened raster bleed values, not a pixel-perfect measurement \u2014 see Switch.figma.json. Size and color are excluded from this version (RFC arbitration Q1) \u2014 see Switch.rfc.md.`
  },
  render() {
    const rowClass = "flex items-center gap-6 flex-wrap";
    const sectionClass = "text-content-caption font-content-caption leading-content-caption tracking-content-caption text-default-secondary";
    return (0, import_react12.createElement)(
      "div",
      { className: "flex flex-col gap-6" },
      (0, import_react12.createElement)("p", { className: sectionClass }, "Unchecked / checked (uncontrolled)"),
      (0, import_react12.createElement)(
        "div",
        { className: rowClass },
        cell("Default unchecked", { "aria-label": "Default unchecked" }),
        cell("Default checked", { "aria-label": "Default checked", defaultChecked: true })
      ),
      (0, import_react12.createElement)("p", { className: sectionClass }, "Disabled"),
      (0, import_react12.createElement)(
        "div",
        { className: rowClass },
        cell("Disabled unchecked", { "aria-label": "Disabled unchecked", disabled: true }),
        cell("Disabled checked", {
          "aria-label": "Disabled checked",
          disabled: true,
          defaultChecked: true
        })
      ),
      (0, import_react12.createElement)("p", { className: sectionClass }, "With integrated label"),
      (0, import_react12.createElement)(
        "div",
        { className: "flex flex-col items-start gap-3" },
        (0, import_react12.createElement)(Switch, { label: "Enable notifications" }),
        (0, import_react12.createElement)(Switch, { label: "Enable notifications (checked)", defaultChecked: true }),
        (0, import_react12.createElement)(Switch, { label: "Enable notifications (disabled)", disabled: true })
      )
    );
  },
  examples: [
    '<Switch aria-label="Enable notifications" />',
    '<Switch label="Enable notifications" />',
    '<Switch label="Auto-refresh" checked={enabled} onCheckedChange={setEnabled} />',
    '<Switch label="Read-only mode" disabled />',
    '<Switch ref={switchRef} label="Enable notifications" defaultChecked />'
  ],
  variants: ["default"],
  sizes: [],
  props: {
    checked: "boolean \u2014 Controlled checked state",
    defaultChecked: "boolean \u2014 Default checked state (uncontrolled)",
    onCheckedChange: "(checked: boolean) => void \u2014 Fires when the checked state changes",
    label: "ReactNode \u2014 Integrated label rendered next to the control (Figma: Q4 option a)",
    wrapperClassName: "string \u2014 Additional classes for the outer <label> wrapper (only applies when `label` is set)",
    disabled: "boolean \u2014 Disabled state",
    required: "boolean \u2014 Marks the control as required in a form",
    name: "string \u2014 Name submitted with the owning form",
    value: 'string \u2014 Value submitted with the owning form when checked (defaults to "on", like a native checkbox)',
    id: "string \u2014 Overrides the auto-generated id used for label association",
    className: "string \u2014 Additional classes for the control itself"
  }
};
// Annotate the CommonJS export names for ESM import in node:
0 && (module.exports = {
  Button,
  ButtonMeta,
  Dialog,
  DialogBody,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogMeta,
  DialogTitle,
  DialogTrigger,
  ICON_NAMES,
  ICON_SIZES,
  Icon,
  IconButton,
  IconButtonMeta,
  IconMeta,
  Input,
  InputMeta,
  Navbar,
  NavbarContext,
  NavbarItem,
  NavbarItemMeta,
  NavbarMeta,
  NavbarSeparator,
  NavbarSubmenu,
  NavbarSubmenuItem,
  NavbarSubmenuMeta,
  ProductSwitcher,
  ProductSwitcherMeta,
  SearchField,
  SearchFieldMeta,
  Switch,
  SwitchMeta,
  TEXT_DEFAULT_TAG,
  Tabs,
  TabsContent,
  TabsList,
  TabsMeta,
  TabsTrigger,
  Text,
  TextMeta,
  Tooltip,
  TooltipContent,
  TooltipMeta,
  TooltipProvider,
  TooltipTrigger,
  buttonVariants,
  iconButtonVariants,
  inputVariants,
  searchFieldVariants,
  useNavbarCollapsed
});
//# sourceMappingURL=index.js.map