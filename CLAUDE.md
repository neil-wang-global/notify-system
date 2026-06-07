# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository status

This repository is currently initialized but does not yet contain application source code, README documentation, Cursor rules, Copilot instructions, or project build configuration files.

The repository is intended to contain Vue and Java projects. Re-check the current project files before assuming a specific frontend or backend build tool.

## Common commands

No project-specific commands are available yet because there is no `package.json`, Maven wrapper, Gradle wrapper, `pom.xml`, or Gradle build file in the repository.

When those files are added, prefer wrapper or package-manager commands from the repository root, for example:

- Vue projects: use the scripts declared in `package.json`.
- Maven projects: prefer `./mvnw` when present, otherwise `mvn`.
- Gradle projects: prefer `./gradlew` when present, otherwise `gradle`.

## Architecture

No application architecture is present yet. Treat this as an empty repository scaffold until Vue or Java source modules are added.

## Git ignore policy

The `.gitignore` is configured for:

- local IDE/editor files such as `.idea/`, `.vscode/`, and `*.iml`
- excluded local documentation in `docs/`
- Vue/Node dependencies, caches, logs, coverage, and build output
- Java/Maven/Gradle build output, archives, runtime dumps, and caches
- local environment files while allowing `.env.example`
