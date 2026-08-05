# Call Secure Pro — Fluent Light Design System

This document defines the permanent UI rules for Call Secure Pro. New screens and components must reuse the resources in `app/src/main/res/values` instead of adding unrelated hardcoded colors, dimensions, shapes, or typography.

## Visual direction

- Microsoft Office 365 / Windows 11 Fluent-inspired appearance.
- Light-theme-first interface.
- Multiple very light color zones on a page rather than one flat background.
- Clean, professional, calm, readable, and compact.
- Soft surfaces, subtle borders, restrained accents, and low or zero elevation.

## Page color zones

Use the centralized colors from `colors.xml`:

- `csp_section_header` — page header and important overview cards.
- `csp_section_lavender` — search, caller intelligence, and profile areas.
- `csp_section_mint` — safe actions, contacts, and protection-success areas.
- `csp_section_peach` — warnings, reports, and attention areas.
- `csp_section_sky` — information and number-lookup areas.
- `csp_section_warm` — neutral summaries and supporting content.

All section colors must remain very light. Strong colors are reserved for text, icons, selected states, and critical actions.

## Cards

- Standard corner radius: `csp_radius_card` (18dp).
- Standard border: `csp_stroke` (1dp).
- Standard padding: `csp_card_padding` (16dp).
- Default elevation: zero.
- Use `Widget.CallSecurePro.Card` and its Header, Lavender, Mint, and Peach variants.
- Do not create large high-elevation cards unless a floating surface is functionally required.

## Buttons

- Primary actions use `Widget.CallSecurePro.Button.Primary`.
- Secondary actions use `Widget.CallSecurePro.Button.Secondary`.
- Destructive or blocking actions use `Widget.CallSecurePro.Button.Danger`.
- Button labels use sentence case, not all caps.
- Full-width buttons are preferred for important setup and confirmation flows.
- Split buttons may be used for equal-priority actions such as SIM 1 and SIM 2.

## Caller status colors

- Safe caller: green text on `csp_safe_container`.
- Verified caller: Fluent blue on `csp_verified_container`.
- Business caller: purple on `csp_business_container`.
- Unknown caller: amber on `csp_unknown_container`.
- Spam or blocked caller: red on `csp_spam_container`.

Use the matching reusable Chip styles. Status must never be communicated through color alone; always include a clear text label and, where appropriate, an icon.

## Typography

Use only the shared text appearances:

- `TextAppearance.CallSecurePro.Display`
- `TextAppearance.CallSecurePro.PageTitle`
- `TextAppearance.CallSecurePro.SectionTitle`
- `TextAppearance.CallSecurePro.CardTitle`
- `TextAppearance.CallSecurePro.Body`
- `TextAppearance.CallSecurePro.Label`
- `TextAppearance.CallSecurePro.Caption`
- `TextAppearance.CallSecurePro.Number`

Headings should be compact. Supporting text should remain readable and use the shared secondary or muted colors.

## Spacing

Use the spacing scale from `dimens.xml` rather than arbitrary margins:

- 4dp and 6dp for tightly related elements.
- 8dp and 10dp for compact component spacing.
- 12dp and 16dp for card content.
- 20dp and 24dp for section separation.
- `csp_page_horizontal` for page-level horizontal padding.

## Interaction and accessibility

- Interactive controls should meet or exceed the shared 48dp touch target whenever practical.
- Text and controls must remain readable at larger Android font sizes.
- Caller status and call direction must include text or icons, not color alone.
- Permission and privacy screens must explain why access is needed before requesting it.
- Destructive actions require clear wording and confirmation where data or blocking state could be affected.

## Development rule

Before creating a new resource, check whether an existing `csp_*` color, dimension, text appearance, card style, button style, or chip style already covers the need. Add new resources only when the new meaning is reusable across multiple screens.
