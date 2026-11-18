# Telegram betas — virtual Companion and experimental duck (18 November 2026)

## Verified public reports, not a stable SDK

- **iOS 13.3 (37388)**: Telegram beta trackers @telelakel and @TgInfo report a new debug-menu item **Companion**: a 3D yellow duck overlay in private chats, customizable appearance and interaction animations. It is a prototype, not a public-facing API. Sources: https://t.me/s/teleLakel ; https://t.me/s/TgInfo
- **macOS experimental builds (November 2026)**: third-party reporting describes a duck-shaped AI assistant alongside experimental helper applications (named Dictator or Teleharness in different reports). Capabilities purportedly include summarizing chats and drafting replies, but these are **not confirmed as public or generally available**. Sources: https://cybersport.metaratings.ru/news/v-telegram-poyavitsya-virtualnaya-ii-utka-kotoraya-umeet-chitat-perepiski-671539/ ; https://delabs.space/digest/2026-11-16-telegram-%D0%B8%D0%B8-%D0%B0%D0%B3%D0%B5%D0%BD%D1%82-dictator
- **macOS 13.2.0 (12 Nov 2026)**: official beta/download page https://macos.telegram.org/ does not announce a Companion feature.
- **Official macOS source** https://github.com/TelegramMessenger/telegram-macos: code search for `Companion`, `Duck`, `Dictator`, `Teleharness` on the public default branch did **not** locate reusable implementation of the reported Companion/agent; `Duck` matches unrelated assets/tests. No API or code can be claimed available for Android porting.
- **Android beta agent UI:** reports mention compact AI agent chat appearance and `agent_bot_ids`; this is not evidence of a native duck renderer being implemented on Android. Source https://t.me/s/teleLakel

## Implementation plan for Comunidad Telebots (separate from unofficial claims)

1. Keep existing **TelebotsPetState** and opt-in UI/game mechanics local by default; do not claim generative AI is already connected.
2. Introduce an optional 3D/2D lightweight **Companion overlay** only when a user enables it, with tap-to-interact, moods, show/hide per chat and never above sensitive input by default.
3. Animate simple reactions with reduced-motion, battery and low-RAM variants.
4. Use the existing local ML Kit object scanner to propose a species/appearance from a camera image. Never transmit images automatically.
5. A later AI companion must require an explicit choice of provider and fine-grained consent per chat. No automatic reading of all chats; generate drafts, never send without confirmation.
6. Track Telegram's public beta source code for an officially released/available implementation before claiming parity.

## Acceptance and licensing

- No copied private beta binaries, proprietary 3D meshes, assets or branding.
- Separate names in the app: **Mascota de Comunidad Telebots** vs **Telegram Companion experimental** in dev documentation. The former is independently implemented.
- QA: touch overlays, keyboard accessibility, FPS and battery, RTL, dark mode, screenshots without private chat content, multi-account boundaries.
