#!/usr/bin/env bash
set -euo pipefail

version="${VERSION:?VERSION is required}"
repository="${GITHUB_REPOSITORY:-EPI-Studios/Epysia}"
release_url="https://github.com/$repository/releases/download/v$version"
destination="dist/language-packs.json"
mkdir -p dist

pack="dist/epysia-lang-kotlin-$version.jar"
runtime="dist/epysia-lang-kotlin-runtime-$version.jar"

cat > "$destination" <<JSON
{
  "formatVersion": 1,
  "packs": [
    {
      "id": "kotlin",
      "name": "Kotlin",
      "description": "Kotlin scripts, with the compiler, highlighting and completion.",
      "version": "$version",
      "asset": "$(basename "$pack")",
      "url": "$release_url/$(basename "$pack")",
      "sizeBytes": $(stat -c%s "$pack"),
      "sha256": "$(sha256sum "$pack" | cut -d' ' -f1)",
      "runtimeAsset": "$(basename "$runtime")",
      "runtimeUrl": "$release_url/$(basename "$runtime")",
      "runtimeSha256": "$(sha256sum "$runtime" | cut -d' ' -f1)"
    }
  ]
}
JSON

echo "wrote $destination"
