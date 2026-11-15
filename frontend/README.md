# Catsflow Mini App

React + TypeScript frontend for the LINE Mini App. Spring Boot remains the API and production host.

The visible app version comes from `package.json`. Bump the patch version for every user-facing update (for example `0.2.0` to `0.2.1`) and keep `package-lock.json` in sync.

## Requirements

- Node.js 20.19 or newer
- npm 10 or newer

## Local Setup

From the repository root, copy the local settings template and set the LINE Login channel ID and LIFF ID in `application-local.properties`. Do not commit this file or share its real values.

```powershell
Copy-Item application-local.example.properties application-local.properties
```

For this Windows development environment, Node.js 24 is installed under the current user's WinGet package directory. In each new PowerShell terminal, add it to the session PATH and use `npm.cmd` to avoid PowerShell's script execution policy:

```powershell
$nodeDir = Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Packages\OpenJS.NodeJS.LTS_Microsoft.Winget.Source_8wekyb3d8bbwe\node-v24.20.0-win-x64'
$env:PATH = "$nodeDir;$env:PATH"
node --version
npm.cmd --version
```

## PC Browser

Use two terminals from the repository root. Start the Spring Boot API in the first:

```powershell
.\mvnw.cmd spring-boot:run
```

Install frontend dependencies and start Vite in the second:

```powershell
Set-Location frontend
npm.cmd install
npm.cmd run dev
```

Open the Vite URL, normally `http://localhost:5173/`. Vite proxies `/api` and `/line/login` to Spring Boot on port 8080. LIFF ID and LINE Login channel ID must be configured for authentication and CSV operations to work.

For UI-only review without LINE authentication, open `http://localhost:5173/?preview=1`. This mode is available only in the Vite development server; values are labeled as samples, saved asset data is marked API unavailable, and CSV upload is disabled.

## LINE App

The phone cannot reach a PC's `localhost`. Expose either Vite (`5173`) or Spring Boot (`8080`) through an HTTPS development tunnel and set the LIFF endpoint URL to that tunnel's HTTPS URL. With Vite, the root serves the frontend directly and proxies API calls to Spring Boot. With Spring Boot, `/` internally forwards to the built frontend while preserving the browser URL and LIFF state parameters. Keep the corresponding tunnel target running; do not use a temporary tunnel as a production endpoint.

For a packaged deployment, build the frontend and set the LIFF endpoint URL to the deployed HTTPS address ending in `/miniapp/`.

## Planning Form Test Data

The default `dev` profile loads demo rows for the seeded sample accounts and adds clearly labeled sample profile, long-term liability, goals, and life events when a new development account registers. These rows can be edited or deleted from the Goals screen. They are test data, not personal financial records.

Before a public deployment, activate the `prod` Spring profile (for example, set `SPRING_PROFILES_ACTIVE=prod`). That profile switches SQL initialization to an empty seed script and disables the new-user planning sample seeder. Do not copy the development H2 database into production.

## Official Account Rich Menu

Run the following from the repository root in Windows PowerShell to create a Catsflow rich menu, upload its image, and set it as the default menu for the Official Account. It opens the LIFF screen directly. The script asks for confirmation because this replaces the current default rich menu for all friends, then securely prompts for the Messaging API channel access token. Do not use the LINE Login channel secret here.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Set-CatsflowRichMenu.ps1 -LiffId "your-liff-id"
```

The execution-policy option applies only to this PowerShell process. The LINE Official Account must be linked to the Messaging API channel that issued the access token. If the LIFF app is still in Developing status, only users with a Developer role can open it even though the rich menu is visible to friends.

## Production build

```powershell
Set-Location frontend
npm.cmd install
npm.cmd run build
Set-Location ..
.\mvnw.cmd package
```

Vite writes the production files to `src/main/resources/static/miniapp`; build the frontend before packaging Spring Boot so the jar contains the Mini App. The application serves it at `/miniapp/`. The development database is in-memory H2, so imported data is lost when the backend stops.
