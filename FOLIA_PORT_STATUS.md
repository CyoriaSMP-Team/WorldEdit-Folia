# WorldEdit-Folia port status — 2026-10-17

**IN PROGRESS / NOT A RELEASE. DO NOT DEPLOY THIS BRANCH ON A LIVE SERVER.**

Source parent: EngineHub/WorldEdit (official fork). Target: Folia Minecraft 1.21.11.

Implemented in this draft: Folia region/entity/global scheduler adapters; command/tool dispatch and chunk ownership snapshot handling; focused tests for chunk index and interaction debouncer. Current source includes incomplete/untested compatibility adaptations.

**Unverified or unsupported:** Cross-region structure generation and regeneration, complete entity/chunk ownership paths, actual Folia multi-region stress testing and plugin integration with WorldGuard/ProtectionStones/FAWE (FAWE must not be loaded alongside WorldEdit).

**Build status:** Gradle wrapper bootstrap succeeded. Test+compile build was terminated before completion because it exhausted safe RAM headroom on a Home Server also running Minecraft. This draft has *not* passed compilation or tests. `git diff --check` passes.

No auto-update, auto-merge or production deployment is configured.
