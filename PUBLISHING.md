## Maven Central publishing (OSSRH / Sonatype)

VelocityAPI is configured to publish to Maven Central via Sonatype staging using the `release` Maven profile.

### One-time setup

- **1) Create Sonatype account + request namespace**
  - Create an account on Sonatype Central / OSSRH.
  - Request the namespace for your `groupId` (currently `io.github.sahilleth`).

- **2) Create a GPG key for signing**
  - Create a signing key, publish it, and make sure your Sonatype account has the signing key info.

- **3) Configure Maven credentials**
  - Add `ossrh` credentials to `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>ossrh</id>
      <username>SONATYPE_USERNAME</username>
      <password>SONATYPE_PASSWORD_OR_TOKEN</password>
    </server>
  </servers>
</settings>
```

- **4) Configure GPG for non-interactive builds (recommended)**
  - Either have `gpg` agent configured, or pass `-Dgpg.passphrase=...` at release time.

### Required project metadata (update before first release)

In `pom.xml`, replace:
- `project.url`
- `project.scm.*`
- `developers/developer`

### Local sanity checks

```bash
./mvnw -q clean test
./mvnw -q -Prelease -DskipTests package
```

### Publish a snapshot

```bash
./mvnw -q -DskipTests deploy
```

### Publish a release to Maven Central

This uploads to Sonatype staging, closes the staging repo, and (with the current config) auto-releases after close:

```bash
./mvnw -q -Prelease -DskipTests deploy
```

### Build the runnable fat JAR (not published to Central)

```bash
./mvnw -q -Pfat-jar -DskipTests package
```

