# REA Chat

An Android client prototype for an authorized REA (Reverse Engineer Anything) analysis workflow.

## Current status

- Arabic-first RTL chat interface.
- Android Storage Access Framework picker for selecting an APK; no broad storage permission is requested.
- GitHub Actions workflow to build a debug APK and publish it as a workflow artifact.
- **REA analysis is not connected yet.** This is a UI/build foundation and does not claim to analyze uploaded files.

## Planned architecture

1. Android app: chat UI, APK selection, job status and results.
2. Authenticated API gateway: validates users and requests, stores job metadata, and routes requests.
3. Ephemeral Linux runner: executes REA and required analysis tools in an isolated job.
4. Results: return bounded text reports and downloadable artifacts through authenticated, short-lived links.

## Security rules

- Never commit APK uploads, API keys, access tokens, signing keys, or other secrets.
- Do not expose an unauthenticated REA MCP endpoint to the public internet.
- Treat uploaded APKs as untrusted input. Run analysis in isolated, disposable environments.
- Use this project only for applications and binaries you own or are authorized to assess.

## Build

The public GitHub Actions workflow builds app-debug.apk on pushes and pull requests. Open **Actions → Android Debug APK → Artifacts** to retrieve the generated APK.

## Next integration steps

- Select and configure a free-tier AI provider without embedding its key in the APK.
- Add an authenticated backend and secure upload/result storage.
- Add a server-side job dispatcher for isolated REA runs.
- Configure secrets only after the backend design and permissions are reviewed.
