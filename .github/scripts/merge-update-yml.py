#!/usr/bin/env python3
"""Merges electron-builder auto-update manifests (latest*.yml) into one per name.

Nucleus runs one electron-builder invocation per target format (Exe/NSIS, Msi, Deb...). Each
format writes a single-artifact `<channel><osSuffix>.yml` into its own output directory, and all
formats of the same OS share the same manifest name (e.g. `latest.yml` for Windows). Uploading them
as-is would clobber each other in the GitHub release, leaving only one format. This script merges
them into a union manifest (deduplicated by url, sorted), exactly like the plugin's own
`UpdateYmlMerger`.

Usage:
    merge-update-yml.py <input_root> <output_dir> [--copy]

- Input root: the `desktopApp/build/compose/binaries` tree.
- Output dir: where the merged `<channel><osSuffix>.yml` files are written (one per distinct name).

When only one manifest matches a name, it is copied verbatim (keeps electron-builder's exact bytes).

With `--copy`, the files referenced by each manifest's `files[].url` are also copied into
[output_dir]. This is the authoritative list of release assets: whatever the updater reads from the
YAML must be uploaded as a GitHub release asset with that exact name. Using the YAML as the source
of truth avoids sweeping in non-installer files (launcher .exe, java.exe, bundled yt-dlp, ...) that
live inside the app-image directories.
"""

import glob
import os
import re
import shutil
import sys


def parse(path):
    version = None
    release_date = None
    entries = []
    url = sha512 = size = blockmap = None
    in_files = False

    def flush():
        nonlocal url, sha512, size, blockmap
        if url is None:
            return
        entries.append({"url": url, "sha512": sha512, "size": size, "blockmap": blockmap})
        url = sha512 = size = blockmap = None

    with open(path, encoding="utf-8", errors="replace") as fh:
        for raw in fh:
            line = raw.rstrip("\n").rstrip("\r")
            if line.startswith("version: "):
                version = line[len("version: "):].strip()
            elif line.startswith("releaseDate: "):
                release_date = line[len("releaseDate: "):].strip()
            elif line == "files:":
                in_files = True
            elif in_files and line.startswith("  - url: "):
                flush()
                url = line[len("  - url: "):].strip()
            elif in_files and url is not None and line.startswith("    "):
                field = line.strip()
                if field.startswith("sha512: "):
                    sha512 = field[len("sha512: "):]
                elif field.startswith("size: "):
                    size = field[len("size: "):]
                elif field.startswith("blockMapSize: "):
                    blockmap = field[len("blockMapSize: "):]
            elif line and not line.startswith(" "):
                flush()
                in_files = False
    flush()
    return version, release_date, entries


def merge(manifests):
    version = next((v for v, _, _ in manifests if v), None)
    if version is None:
        raise SystemExit("No manifest declares a version")
    by_url = {}
    for _, _, entries in manifests:
        for entry in entries:
            by_url.setdefault(entry["url"], entry)
    entries = sorted(by_url.values(), key=lambda e: e["url"])
    if not entries:
        raise SystemExit("No files entries found to merge")
    release_date = max((rd for _, rd, _ in manifests if rd), default=None)
    first = entries[0]

    out = [f"version: {version}", "files:"]
    for entry in entries:
        out.append(f"  - url: {entry['url']}")
        if entry["sha512"]:
            out.append(f"    sha512: {entry['sha512']}")
        if entry["size"]:
            out.append(f"    size: {entry['size']}")
        if entry["blockmap"]:
            out.append(f"    blockMapSize: {entry['blockmap']}")
    out.append(f"path: {first['url']}")
    if first["sha512"]:
        out.append(f"sha512: {first['sha512']}")
    if release_date:
        out.append(f"releaseDate: {release_date}")
    return "\n".join(out) + "\n"


def find_file(root, name):
    for dirpath, _dirnames, filenames in os.walk(root):
        if name in filenames:
            return os.path.join(dirpath, name)
    return None


def copy_referenced_files(root, outdir, manifests):
    urls = []
    for _, _, entries in manifests:
        for entry in entries:
            if entry["url"] not in urls:
                urls.append(entry["url"])
    for url in urls:
        src = find_file(root, url)
        if src is None:
            print(f"warning: '{url}' referenced by a manifest but not found under {root}", file=sys.stderr)
            continue
        shutil.copy2(src, os.path.join(outdir, url))
        print(f"copied {os.path.basename(src)} ({os.path.getsize(src)} bytes)")


def main():
    if len(sys.argv) < 3:
        raise SystemExit("usage: merge-update-yml.py <input_root> <output_dir> [--copy]")
    root, outdir = sys.argv[1], sys.argv[2]
    do_copy = "--copy" in sys.argv
    os.makedirs(outdir, exist_ok=True)

    by_name = {}
    for path in glob.glob(os.path.join(root, "**", "latest*.yml"), recursive=True):
        by_name.setdefault(os.path.basename(path), []).append(path)

    if not by_name:
        print("No update manifests found under", root)
        return

    for name, paths in sorted(by_name.items()):
        if len(paths) == 1:
            content = open(paths[0], encoding="utf-8", errors="replace").read()
            manifests = [parse(paths[0])]
        else:
            manifests = [parse(p) for p in paths]
            content = merge(manifests)
        with open(os.path.join(outdir, name), "w", encoding="utf-8") as fh:
            fh.write(content)
        print(f"wrote {name} from {len(paths)} manifest(s): {', '.join(paths)}")
        if do_copy:
            copy_referenced_files(root, outdir, manifests)


if __name__ == "__main__":
    main()
