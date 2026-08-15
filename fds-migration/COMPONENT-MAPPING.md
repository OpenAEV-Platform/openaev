# Component Mapping — OpenAEV

GENERATED — do not edit by hand. Regenerate: `pnpm generate:fds-migration --product openaev --write-to-product` (filigran-design-system repo).
Derived from filigran-design-system's `ROADMAP.json` × `process/mui-crosswalk.json` × `process/mui-inventory.json` (as of 2026-08-14).

Recognize a MUI component in this product, look up its design-system
replacement and current readiness. "Lib status" = is the design-system
component built yet (filigran-design-system ROADMAP.json). "Product status"
= has THIS product already adopted it. Sorted by occurrences in this
product (highest first) — that's also roughly migration priority once
Phase 2 (components) starts (AGENTS.md rule 5: not started yet).

| DS Component | Lib status | Product status | MUI identifiers | Occurrences | Files |
|---|---|---|---|---|---|
| Typography | done | partial | `Typography` | 1375 | 175 |
| Button | done | partial | `Button`, `LoadingButton`, `buttonVariants` | 1070 | 202 |
| Dialog | done | todo | `Dialog`, `DialogTitle`, `DialogContent`, `DialogContentText`, `DialogActions`, `AlertDialog`, `AlertDialogAction`, `AlertDialogCancel`, `AlertDialogContent`, `AlertDialogDescription`, `AlertDialogFooter`, `AlertDialogHeader`, `AlertDialogTitle` | 1018 | 286 |
| Menu | done | partial | `Menu`, `MenuItem`, `MenuList`, `DropdownMenu`, `DropdownMenuContent`, `DropdownMenuItem`, `DropdownMenuLabel`, `DropdownMenuSeparator`, `DropdownMenuTrigger` | 689 | 93 |
| Tooltip | done | partial | `Tooltip`, `TooltipContent`, `TooltipTrigger`, `TooltipProvider` | 444 | 110 |
| IconButton | done | partial | `IconButton` | 341 | 95 |
| Inputs | todo | todo | `InputLabel`, `FormControl`, `FormControlLabel`, `FormGroup`, `FormLabel`, `FormHelperText`, `Form`, `FormItem`, `FormField`, `FormDescription`, `FormMessage`, `Textarea` | 325 | 85 |
| Paper | in-review | todo | `Paper` | 292 | 62 |
| Card | todo | todo | `Card`, `CardContent`, `CardHeader`, `CardActions`, `CardActionArea`, `CardMedia`, `CardFooter` | 270 | 69 |
| Input | done | todo | `TextField`, `Input`, `InputAdornment` | 252 | 77 |
| Alert | todo | todo | `Alert`, `AlertTitle` | 196 | 52 |
| Chip | in-progress | partial | `Chip` | 165 | 66 |
| Tabs | done | todo | `Tabs`, `Tab`, `TabContext`, `TabList`, `TabPanel`, `TabsList`, `TabsTrigger` | 132 | 34 |
| Select | done | todo | `Select`, `SelectContent`, `SelectItem`, `SelectTrigger`, `SelectValue` | 106 | 23 |
| Accordion | todo | todo | `Accordion`, `AccordionSummary`, `AccordionDetails`, `AccordionActions`, `AccordionContent`, `AccordionItem`, `AccordionTrigger` | 93 | 29 |
| Combobox | in-review | todo | `Autocomplete`, `Combobox`, `MultiSelectFormField` | 71 | 34 |
| ButtonGroup | todo | todo | `ButtonGroup`, `ToggleButton`, `ToggleButtonGroup` | 70 | 20 |
| Checkbox | done | todo | `Checkbox` | 58 | 22 |
| Avatar | todo | todo | `Avatar` | 36 | 10 |
| Drawer | todo | todo | `Drawer`, `Sheet`, `SheetContent`, `SheetFooter`, `SheetHeader`, `SheetTitle`, `SheetTrigger` | 30 | 10 |
| Switch | done | todo | `Switch` | 30 | 15 |
| Separator | todo | todo | `Divider`, `Separator` | 29 | 11 |
| Header | in-review | partial | `AppBar`, `Toolbar` | 26 | 9 |
| Radio | done | todo | `Radio`, `RadioGroup` | 24 | 9 |
| Spinner | in-review | partial | `CircularProgress` | 22 | 11 |
| Badge | in-review | partial | `Badge` | 19 | 3 |
| ProgressBar | in-progress | partial | `LinearProgress`, `linearProgressClasses` | 11 | 5 |
| Pagination | todo | todo | `TablePagination` | 6 | 3 |
| Slider | done | todo | `Slider` | 6 | 3 |
| Breadcrumbs | todo | todo | `Breadcrumbs`, `Breadcrumb`, `BreadcrumbItem`, `BreadcrumbLink`, `BreadcrumbList`, `BreadcrumbSeparator` | 3 | 1 |
| Banner | todo | todo | — | — | — |
| FilterBar | todo | todo | — | — | — |
| Icon | done | partial | — | — | — |
| Navbar | done | partial | — | — | — |
| NavbarItem | in-review | partial | — | — | — |
| NavbarSubmenu | in-review | partial | — | — | — |
| ProductSwitcher | done | partial | — | — | — |
| SearchField | done | partial | — | — | — |
