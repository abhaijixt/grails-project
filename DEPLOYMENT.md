# Docs Deployment — GitHub Pages

Record of how the MkDocs site in this repo was set up and published to GitHub
Pages, and how redeploys work from here on.

- **Live site:** <https://abhaijixt.github.io/grails-project/>
- **Repo:** `git@github.com:abhaijixt/grails-project.git`
- **Default branch:** `main`
- **Published branch:** `gh-pages` (built output only — never edit by hand)
- **Date performed:** 2026-07-25

---

## Summary

| | |
|---|---|
| Site generator | MkDocs 1.6.1 + Material 9.7.7 (pinned in `requirements-docs.txt`) |
| Local toolchain | `.venv-docs/` virtualenv (Python 3.14) |
| Publish mechanism | `mkdocs gh-deploy --force` → pushes built site to `gh-pages` |
| Automation | `.github/workflows/deploy-docs.yml` on push to `main` |
| Commit on `main` | `3aed054` (parent `c295557`) |
| Commit on `gh-pages` | `f5b6d1b` |

---

## Step 1 — Pre-flight checks

Confirmed the remote, the branch, and that the docs build cleanly before
changing anything.

```bash
git remote -v
# origin  git@github.com:abhaijixt/grails-project.git (fetch)
# origin  git@github.com:abhaijixt/grails-project.git (push)

git branch --show-current
# main

git ls-remote --symref origin HEAD
# ref: refs/heads/main   HEAD          <- remote default branch is main
# (no gh-pages branch existed at this point)

source .venv-docs/bin/activate
mkdocs build --strict
# INFO - Documentation built in 0.59 seconds   <- passed
```

Two findings from this pass:

1. The repo is named **`grails-project`**, not `grails-bookstore` (the local
   working directory name). The Pages URL is derived from the remote, so the
   path is `/grails-project/`.
2. Most of the docs toolchain was **untracked** in git — `mkdocs.yml`,
   `requirements-docs.txt`, `docs/index.md`, and all of `docs/walkthrough/`.
   Only `docs/HLD.md`, `docs/LLD.md`, and `docs/CODE_WALKTHROUGH.md` were
   tracked. This mattered for Step 5.

## Step 2 — `site_url` added to `mkdocs.yml`

Derived from the remote: `https://<username>.github.io/<repo-name>/`.

```yaml
site_url: https://abhaijixt.github.io/grails-project/
repo_url: https://github.com/abhaijixt/grails-project
repo_name: abhaijixt/grails-project
edit_uri: edit/main/docs/
```

`site_url` gives Material the canonical `<link>` tag and a correct
`sitemap.xml`.

**Also fixed in the same edit:** `edit_uri` read `edit/master/docs/`, but there
is no `master` branch on the remote. Every "edit this page" pencil link on the
published site would have 404'd. Changed to `edit/main/docs/`.

## Step 3 — GitHub Actions workflow

Created `.github/workflows/deploy-docs.yml`:

- **Triggers:** push to `main`, filtered to `docs/**`, `mkdocs.yml`,
  `requirements-docs.txt`, and the workflow file itself — plus
  `workflow_dispatch` for manual runs.
- **Permissions:** `contents: write`, so `gh-deploy` can push to `gh-pages`.
- **Steps:** `actions/checkout@v4` (with `fetch-depth: 0`, since `gh-deploy`
  commits onto another branch), `actions/setup-python@v5` (`3.x`, pip cache
  keyed on `requirements-docs.txt`), `pip install -r requirements-docs.txt`,
  then `mkdocs gh-deploy --force`.
- **Concurrency:** a `deploy-docs` group with `cancel-in-progress: false`, so
  two rapid pushes can't race each other into a corrupt `gh-pages`.

## Step 4 — One-time immediate deploy from local machine

```bash
source .venv-docs/bin/activate
mkdocs gh-deploy --force
```

Output:

```
INFO    -  Documentation built in 0.59 seconds
WARNING -  Version check skipped: No version specified in previous deployment.
INFO    -  Copying '.../site' to 'gh-pages' branch and pushing to GitHub.
To github.com:abhaijixt/grails-project.git
 * [new branch]      gh-pages -> gh-pages
INFO    -  Your documentation should shortly be available at:
           https://abhaijixt.github.io/grails-project/
```

`gh-deploy` builds from the **working directory**, not from git — which is why
this succeeded even though several docs sources were still untracked at this
point. That is exactly the gap Step 5 closes for CI.

## Step 5 — Commit and push to `main`

The literal ask was "commit the workflow + mkdocs.yml". That alone would have
produced a **workflow that fails on its first run**: a fresh CI checkout would
have no `requirements-docs.txt` (the `pip install` step dies) and no
`docs/index.md` or `docs/walkthrough/` (the `nav:` entries resolve to nothing).

So the staged set was widened to everything the build actually needs:

```bash
git add .github/workflows/deploy-docs.yml \
        mkdocs.yml \
        requirements-docs.txt \
        .gitignore \
        docs/index.md \
        docs/walkthrough/ \
        docs/HLD.md \
        docs/LLD.md
```

**Deliberately left uncommitted** (unrelated to the docs build): modified
`README.md`, `download.jpeg`, `knowledge-base/`, and `docs/site/` (a
pre-existing unrelated static site, already excluded via `exclude_docs`).

### Verification before committing

Rather than trusting that the staged set was complete, the staged tree was
exported and built in isolation — reproducing exactly what a CI checkout sees:

```bash
git archive $(git write-tree) | tar -x -C /tmp/ci-check
cd /tmp/ci-check && mkdocs build --strict
# INFO - Documentation built in 0.55 seconds   <- passed
```

### Commit and push

```bash
git commit   # -> 3aed054  "Add MkDocs GitHub Pages deploy workflow"
git push origin main
#   c295557..3aed054  main -> main
```

13 files changed, 1581 insertions.

## Step 6 — Post-deploy verification

```bash
curl -s -o /dev/null -w "%{http_code}" -L https://abhaijixt.github.io/grails-project/
# 200
```

| Path | Status |
|---|---|
| `/` | 200 |
| `/HLD/` | 200 |
| `/LLD/` | 200 |
| `/walkthrough/` | 200 |
| `/walkthrough/services/` | 200 |
| `/search/search_index.json` | 200 |

Page `<title>` is `Grails Bookstore — Design Docs` and the canonical link
resolves to `https://abhaijixt.github.io/grails-project/`.

> Verification was done over HTTP (status codes, title, canonical tag), not a
> rendered-browser check — `interceptor` is not installed on this machine.

---

## Operating notes

### Publishing a docs change

Edit anything under `docs/` (or `mkdocs.yml` / `requirements-docs.txt`), commit,
and push to `main`. The Action rebuilds and force-pushes `gh-pages`. A manual run
is available from the repo's **Actions → Deploy docs → Run workflow**.

### Timing

First deploy takes roughly **1–2 minutes** to go live after the `gh-pages` push.
Later Action-driven deploys are the workflow run (~40–60s) plus the same ~1–2
minutes of Pages propagation.

### If the site 404s

Check **Settings → Pages** is set to *Deploy from a branch:* `gh-pages` /
`(root)`. In this case Pages picked the branch up automatically and returned 200
without any manual configuration — so this is only relevant if it regresses.

### Local preview

```bash
source .venv-docs/bin/activate
mkdocs serve          # http://127.0.0.1:8000
mkdocs build --strict # what CI effectively gates on
```

### Gotchas

- **Never commit to `gh-pages` by hand.** `gh-deploy --force` overwrites it
  wholesale on every run.
- **Don't put stray `.md` files inside `docs/`.** `validation.omitted_files` is
  `warn`, and `--strict` promotes warnings to errors — a file in `docs/` that
  isn't in `nav:` will break the build. (This file lives at the repo root for
  that reason.)
- **`site/` is gitignored** (`/site/`, anchored to the repo root so the
  unrelated `docs/site/` is untouched). Build output should never be committed
  to `main`.
- **Pinned versions.** `requirements-docs.txt` pins MkDocs, Material, and the
  extension set so local and CI builds stay identical. Material 9.x also warns
  loudly about the upcoming MkDocs 2.0 break — pinning is what keeps that
  noise from becoming a failure.
