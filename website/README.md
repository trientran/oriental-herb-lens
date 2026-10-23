# Website

The site at `https://medherblens.com`: this home page, the About page, the privacy policy
and the terms of service, with the web app (`:webApp`) at `/app/`. The About pages (`about.html`, and `about-vi.html` from README.vi.md)
is made from the repository's README.md at build time (`about.template.html` holds its header and
footer), so edit the README, not the page; it shows everything above the README's `about:end`
marker. The home page stays plain HTML: search
engines and Google's sign-in verification read its text and links, which a Compose app (drawn
on a canvas) doesn't have. The legal pages keep their paths (`/pages/privacy-policy.html`,
`/pages/terms-of-service.html`), which the apps (`LegalLinks` in core:designsystem) and the store
listings link to.

**Species pages.** For the same reason, each species has a plain HTML page under `/herbs/`
(`/herbs/3035652-polyscias-fruticosa.html`), with an A–Z index at `/herbs/`, made at build time
from the app's catalog (`androidApp/assets/herb_catalog.csv`) by the `speciesPages` task into
`species.template.html`: its names, classification, GBIF link and an *Open in Med Herb Lens* button
(`/app/?species=<id>`, which the web app opens). The build also writes `sitemap.xml` (every page)
and `robots.txt`. Canonical links point at the production address, so copies on preview addresses
don't compete with it. The pages follow the bundled catalog: a catalog published only to R2 (docs/
content-publishing.md) reaches them at the next site build, once it's also in `androidApp/assets`.
Submit `https://medherblens.com/sitemap.xml` in Google Search Console (Sitemaps), in the
medherblens.com property.

**Addresses.** The site is `https://medherblens.com`, a custom domain on the Pages project. The Pages
address, `med-herb-lens.pages.dev`, redirects there permanently (a Cloudflare Bulk Redirect, so
links in older app versions and search results still work), and every page's canonical link names
medherblens.com. Don't block the Pages address in `robots.txt`: both addresses serve the same file,
and search engines must still crawl the old one to see its redirect. The apps (`LegalLinks`) and the Hugging Face
model cards link to the Pages address on purpose: it costs nothing and can't lapse, so if the
domain is ever given up, deleting the redirect is all it takes for every installed version's links
to keep working.

**Sample photos.** `/sample-photos.html` shows photos of eight species the model knows (from
`test-images/`, with their credits), for App Review and anyone trying the app without the plants;
it isn't indexed by search engines.

Build the whole site, then publish it to Cloudflare Pages (free) from the repository root:

    ./gradlew :webApp:site
    npx wrangler pages deploy webApp/build/site --project-name med-herb-lens --branch main

The Release workflow does the same when the web is released (docs/release.md).
