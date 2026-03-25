# Release Guide

## Overview

This project is published to Maven Central using the `central-publishing-maven-plugin`.

Releases are:

* GPG signed (artifacts + Git tag)
* Automatically published (no manual staging required)

---

## Prerequisites

### 1. GPG setup

Make sure a secret key is available:

```
gpg --list-secret-keys
```

This project uses:

```
7228B4213B5CE78CECBF13B652E7FD2BB8911E56
```

> The passphrase is **not stored in Maven config**.
> GPG uses `gpg-agent` to prompt and cache it securely.

---

### 2. Maven settings

`~/.m2/settings.xml` must contain:

```
<servers>
  <server>
    <id>central</id>
    <username>YOUR_USERNAME</username>
    <password>YOUR_TOKEN</password>
  </server>
</servers>
```

And the GPG profile:

```
<profile>
  <id>gpg-7228B4213B5CE78CECBF13B652E7FD2BB8911E56-sonatype</id>
  <properties>
    <gpg.keyname>7228B4213B5CE78CECBF13B652E7FD2BB8911E56</gpg.keyname>
  </properties>
</profile>
```

---

## Release Process

### 1. Ensure SNAPSHOT version

```
<version>X.Y.Z-SNAPSHOT</version>
```

---

### 2. Prepare release

```
mvn -P gpg-7228B4213B5CE78CECBF13B652E7FD2BB8911E56-sonatype release:prepare
```

This will:

* Remove `-SNAPSHOT`
* Create a Git commit
* Create a Git tag (signed)

> During this step, GPG will prompt for your passphrase.
> This comes from **Git tag signing**, not Maven.

---

### 3. Perform release (publish)

```
mvn -P gpg-7228B4213B5CE78CECBF13B652E7FD2BB8911E56-sonatype release:perform
```

This will:

* Build all modules
* Sign artifacts (JARs, sources, javadoc)
* Upload to Maven Central
* Automatically publish the release

---

### 4. Push changes

```
git push --follow-tags
```

---

## Alternative (simpler) release

You can skip the release plugin entirely:

### 1. Set version manually

```
<version>X.Y.Z</version>
```

### 2. Deploy

```
mvn -P sonatype,gpg-7228B4213B5CE78CECBF13B652E7FD2BB8911E56-sonatype clean deploy
```

### 3. Tag manually

```
git tag X.Y.Z
git push --tags
```

---

## Notes on GPG

* The GPG key ID is public and safe to share.
* The passphrase is intentionally **not stored** in `settings.xml`.
* Modern GPG (2.x) uses `gpg-agent`, which:

  * prompts once
  * caches the passphrase temporarily

### Important

There are two independent signing steps:

1. **Artifact signing** (Maven)
2. **Git tag signing** (release plugin)

Git tag signing does **not** use Maven configuration and will always rely on GPG directly.

---

## Troubleshooting

### GPG prompt appears

Expected behavior:

* Triggered by Git tag signing or gpg-agent cache expiry

---

### Signing fails

Check:

```
gpg --list-secret-keys
```

---

### Deployment fails (401)

Check credentials in `~/.m2/settings.xml`:

* server id must be `central`
* username/password must be valid tokens

---

## Summary

Typical release:

```
mvn -P gpg-7228B4213B5CE78CECBF13B652E7FD2BB8911E56-sonatype release:prepare
mvn -P gpg-7228B4213B5CE78CECBF13B652E7FD2BB8911E56-sonatype release:perform
git push --follow-tags
```

Or simpler:

```
mvn -P sonatype,gpg-7228B4213B5CE78CECBF13B652E7FD2BB8911E56-sonatype clean deploy
git tag X.Y.Z
git push --tags
```

