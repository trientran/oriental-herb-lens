# Temporary website

Static pages until the Med Herb Lens web app (Phase 6) replaces them: a home page, the privacy
policy and the terms of service. The paths match the old site (`/pages/privacy-policy.html`,
`/pages/terms-of-service.html`), so links keep working when the domain moves.

To publish on Cloudflare Pages (free), from the repository root:

    npx wrangler pages deploy website --project-name med-herb-lens

The first run creates the project and prints its URL (`https://med-herb-lens.pages.dev`). Then point
`LegalLinks` (core:designsystem) and the store listings at the new URLs.
