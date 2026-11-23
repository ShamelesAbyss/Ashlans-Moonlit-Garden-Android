# Contributing

Thank you for approaching Ashlan’s Moonlit Garden with care. This is a personal memorial project, not an open-source commons. The repository is public so people can see and follow its development; no contribution is invited to the private memories or memorial identity.

## Before proposing a change

Open a focused issue or draft pull request describing the proposed code change, its tests, and any assets or dependencies it introduces. Please do not submit or request private journal entries, photographs, family details, messages attributed to Ashlan, or other personal material. Never add save files, JSON backups, drafts, signing keys, credentials, analytics, advertisements, cloud services, network requests, or account requirements.

Do not copy third-party art, music, fonts, code, or models unless the owner has documented redistribution rights for this exact project and distribution. The soundtrack is intentionally held back pending rights verification. A pull request does not transfer copyright or license rights. The project owner must approve any contribution before it is included.

## Engineering expectations

- Keep the game offline-first and native Kotlin/Compose/Canvas; do not introduce WebView.
- Preserve the version-1 JSON migration contract, append-only journal behavior, and local-only storage.
- Add or update deterministic unit tests for game rules and persistence changes.
- Avoid adding permissions or dependencies without explaining why they are necessary.
- Do not publish tags/releases, sign production builds, or change ownership/licensing terms as part of a contribution.

The owner may accept, decline, revise, or close any proposed contribution. See `LICENSE.txt`: all rights are reserved.
