# Copilot repository instructions

Read and follow `/AGENTS.md` before changing this repository. It is the source of truth for
project-wide constraints, source priority, validation, current Android boundaries, and the KMP/CMP
direction.

Do not infer current package names, Gradle tasks, dependencies, navigation APIs, or platform support
from examples. Confirm them in source, tests, `settings.gradle`, Gradle build files, and
`gradle/libs.versions.toml`. The KMP target structure in `docs/dev/kmp-cmp-modernization.md` is a
staged proposal, not a description of modules that already exist.

Keep changes surgical, preserve existing workspace edits, run risk-matched verification, and report
exact commands plus unverified behavior.
