# Security

## Reporting a vulnerability

Please report security problems privately, through
[GitHub's private vulnerability reporting](https://github.com/batatinhafcbatatinha-pixel/spotifast-mobile/security/advisories/new),
not in a public issue. You will get an answer within a week.

## What the app does with your account

- It talks only to Spotify. There is no Spotifast server, no analytics and
  no ads.
- You sign in in your browser; the app never sees your password. It keeps
  Spotify's tokens in its private storage on the phone, and **Sign out**
  removes them.
- It asks Spotify for the same permissions as desktop Spotifast, which
  shares its sign-in code: your library, playlists, follows and playback,
  plus your plan (Premium is needed to play). The Android and iPhone apps
  read your library and playlists, control playback and add songs to the
  queue; they change nothing else in your account.
- It stores no music or covers on disk.

## Checking a download

Release APKs are built from this repository's source by GitHub Actions,
which records a signed
[build provenance attestation](https://docs.github.com/actions/security-for-github-actions/using-artifact-attestations)
for each one. To check that an APK came from this repository, unchanged:

```sh
gh attestation verify spotifast-mobile-<version>.apk -R batatinhafcbatatinha-pixel/spotifast-mobile
```

Each release also lists the files' SHA-256 checksums in `SHA256SUMS.txt`.
The APKs are signed with the certificate whose SHA-256 fingerprint is
`19:55:18:58:F8:F0:57:B7:8E:BB:14:40:5B:3E:91:D3:D1:F8:D4:6C:35:E7:5D:65:48:A9:DD:92:B8:AB:E2:4A`.

## Automated checks

- [CodeQL](https://github.com/batatinhafcbatatinha-pixel/spotifast-mobile/actions/workflows/codeql.yml)
  analyses the Kotlin app, the Rust core and the workflows on every change.
- Dependabot proposes updates for the Rust, Gradle and Actions dependencies.
- [OpenSSF Scorecard](https://scorecard.dev/viewer/?uri=github.com/batatinhafcbatatinha-pixel/spotifast-mobile)
  rates the repository's security practices.
