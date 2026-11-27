---
name: frame-room-ui
description: Use before any UI change in 帧间 so the work stays inside the existing Frame Room design system, tokens, and Element Plus stack.
---

# Frame Room UI

Before changing any UI, read these files in order:

1. `DESIGN.md`
2. `docs/frontend-visual-direction.md`
3. `frontend/src/tokens.css`

## Hard constraints

- Keep the existing visual world: warm paper background, pine green `#245e52`, terracotta accents, muted ink and hairline borders.
- Reuse the existing CSS variables and tokens. Do not introduce a second palette, a new skin, gradients as decoration, glass, or a new radius scale.
- Keep Element Plus. Do not replace it with another component library.
- Do not add Tailwind or any utility-CSS framework.
- `frontend-design` may only be used to remove template-like choices. It must not override this project's tokens, typography, spacing, or component language.
- Preserve product behavior, copy, data, and API semantics unless the task explicitly includes them.
- Prefer a narrow, real fix over a broad visual rewrite.

## Verification

- Check the rendered result with real browser screenshots at **1440×900** and **390×844** before calling the change done.
- Verify loading, empty, error, disabled, and permission states touched by the change.
- Check horizontal overflow, keyboard focus, and touch targets at mobile width.
