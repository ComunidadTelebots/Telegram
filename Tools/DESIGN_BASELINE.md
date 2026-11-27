# Comunidad Telebots Android - visual reference 01

Date: 2026-11-06.

Status: historical partial evidence, rejected by the user as a final UI reference.
The Apply/Cancel dialog does not represent the intended full application design.
Do not use its composition as an approved implementation target.

## Full-screen proposals requested after the rejection

Status update: the user also rejected these generated proposals because they
reused one layout. They are not an approved visual reference. The subsequent
source comparison is documented in `CLIENT_DESIGN_COMPARISON_2026-11-06.md`.

Canva: https://canva.link/rxjjq6m7vi03obv

Design ID: `DAHaJwE0B-A`. Sixteen individual slides, one per registered style,
with a chat list, conversation and editable rules. These are design proposals,
not authenticated APK screenshots or an approved final implementation. They
remove the Apply/Cancel dialog from the proposed UI. Motion values describe
preferences, not recorded or playable animation evidence.

Canva: https://www.canva.com/d/SVyuQOtrF3XR_k1

Folder: https://www.canva.com/folder/FAHaJ7MmKUA

19 pages: cover, all 16 registered native style previews, motion parameters,
and change-control rules. The October Canva gallery remains unchanged.

## Evidence and scope

- Screenshots: Pixel emulator, synthetic preview activity, captured 2026-11-06.
- Local APK file timestamp: 2026-10-27. This task did not rebuild the APK.
- Source parameters: working tree based on `87e98351b`, inspected 2026-11-06.
- PDF: `output/pdf/comunidad-telebots-referencia-visual-2026-11-06.pdf`.
- Original screenshots and SHA-256 manifest:
  `output/design-reference/2026-11-06/manifest.json`.
- Source inspection: `SkinManager`, individual skin classes, `ClientLayout`,
  and `MotionStyle` under `org.telegram.ui.skins`.

The source parameters and installed visual evidence have separate provenance;
the screenshots do not prove that every current working-tree change is installed.
Color themes and motion preferences are independent of style selection. Historical
client names do not establish pixel parity with their official applications.

## Change rules

Preserve this edition. Use a new dated copy for an explicitly requested redesign.
Ordinary functional work must not silently change the documented style. Compare
the relevant screenshot and document differences when fixing visible defects.
Canva is an editable reference, not a technical edit lock or automated UI test.

## Coverage still to extend

Authenticated chat list and conversation flows for each style, settings, access,
AMP, calls, empty/error states, dark themes, large text and Android 5.0 are not
visually certified by these synthetic previews. Add anonymized captures before
claiming those screens are covered. Never upload real private conversations.
