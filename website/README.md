# Website

The site at `https://med-herb-lens.pages.dev`: this home page, the About page, the privacy policy
and the terms of service, with the web app (`:webApp`) at `/app/`. The About pages (`about.html`, and `about-vi.html` from README.vi.md)
is made from the repository's README.md at build time (`about.template.html` holds its header and
footer), so edit the README, not the page; it shows everything above the README's `about:end`
marker. The home page stays plain HTML: search
engines and Google's sign-in verification read its text and links, which a Compose app (drawn
on a canvas) doesn't have. The legal pages keep their paths (`/pages/privacy-policy.html`,
`/pages/terms-of-service.html`), which the apps (`LegalLinks` in core:designsystem) and the store
listings link to.

Build the whole site, then publish it to Cloudflare Pages (free) from the repository root:

    ./gradlew :webApp:site
    npx wrangler pages deploy webApp/build/site --project-name med-herb-lens --branch main

The Release workflow does the same when the web is released (docs/release.md).
