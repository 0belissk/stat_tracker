# Contributing Guidelines

Thanks for contributing to Volleyball Stats Messenger!

## Workflow
- Branch from `main` using `feat/`, `fix/`, or `chore/` prefixes.
- Keep PRs small (< 400 lines).
- Describe *why* the change matters.

## Before pushing
1. **Format** – `npm run format` and `./mvnw spotless:apply`
2. **Lint** – `npm run lint`
3. **Test** – `npm test` and `./mvnw verify`
4. Ensure coverage ≥ 80 % (or project-agreed threshold)

## PR Review
- CI must be green before merge.
- At least one reviewer approval required.

