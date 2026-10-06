"""Preview or upload the validated Asterion 1.5 matrix (Python 3.11+, no packages)."""
import argparse
import hashlib
import json
import os
import re
import shutil
from pathlib import Path
import subprocess
import sys
import time
import tomllib
import urllib.error
import urllib.request
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
API = "https://minecraft.curseforge.com/api"
UPLOAD_API = API
LOADERS = {"fabric": "Fabric", "quilt": "Quilt", "forge": "Forge", "neoforge": "NeoForge"}
VERSIONS = ("1.20.1", "1.21.1", "26.1.2")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def upload_chunks(body, progress=None):
    started = last_report = time.monotonic()
    size = len(body)
    print(f"Sending {size / 1024 / 1024:.1f} MiB...", flush=True)
    for offset in range(0, size, 256 * 1024):
        chunk = body[offset:offset + 256 * 1024]
        yield chunk
        sent = offset + len(chunk)
        if progress is not None:
            progress["sentBytes"] = sent
        now = time.monotonic()
        if now - last_report >= 30 or sent == size:
            elapsed = max(now - started, 0.001)
            rate = sent / elapsed
            remaining = (size - sent) / rate / 60
            print(f"Sent {sent / size:.1%} ({sent / 1024 / 1024:.1f}/{size / 1024 / 1024:.1f} MiB), "
                  f"{rate / 1024:.1f} KiB/s, estimated {remaining:.1f} min remaining", flush=True)
            last_report = now
    print("Transfer sent; waiting for CurseForge's file ID...", flush=True)


def request(endpoint, token, data=None, content_type=None, progress=None):
    headers = {"X-Api-Token": token, "Accept": "application/json", "User-Agent": "Asterion-Release-Uploader/1.0"}
    if content_type:
        headers["Content-Type"] = content_type
    if data is not None:
        headers["Content-Length"] = str(len(data))
    req = urllib.request.Request(API + endpoint, data=upload_chunks(data, progress) if data is not None else None, headers=headers)
    try:
        # This is a timeout for an individual socket operation, not the whole
        # transfer. Small writes allow a slow but progressing upload to continue.
        with urllib.request.build_opener(NoRedirect()).open(req, timeout=900 if data is not None else 30) as response:
            return json.load(response)
    except urllib.error.HTTPError as exc:
        raise RuntimeError(f"CurseForge returned HTTP {exc.code} for {endpoint}.") from None
    except (urllib.error.URLError, TimeoutError, ValueError, OSError) as exc:
        cause = exc.reason if isinstance(exc, urllib.error.URLError) else exc
        code = getattr(cause, "winerror", None) or getattr(cause, "errno", None)
        detail = type(cause).__name__ + (f" (error {code})" if code is not None else "")
        raise RuntimeError(f"No usable response from CurseForge for {endpoint}: {detail}.") from None


def relations(path, loader):
    # Read the built jar: dependencies vary across Minecraft and loader targets.
    with zipfile.ZipFile(path) as jar:
        if loader in ("fabric", "quilt"):
            dependencies = set(json.loads(jar.read("fabric.mod.json"))["depends"])
            embedded = [item["file"] for item in json.loads(jar.read("fabric.mod.json")).get("jars", [])]
        else:
            name = "META-INF/neoforge.mods.toml" if "META-INF/neoforge.mods.toml" in jar.namelist() else "META-INF/mods.toml"
            entries = tomllib.loads(jar.read(name).decode())["dependencies"]["asterion"]
            dependencies = {item["modId"] for item in entries if item.get("mandatory", item.get("type") == "required")}
            embedded = [item["path"] for item in json.loads(jar.read("META-INF/jarjar/metadata.json"))["jars"]]
    result = []
    if "geckolib" in dependencies:
        result.append({"slug": "geckolib", "type": "requiredDependency"})
    if "fabric-api" in dependencies:
        result.append({"slug": "fabric-api", "type": "requiredDependency"})
    if "fabric_api" in dependencies:
        kind = "embeddedLibrary" if any("forgified-fabric-api" in item for item in embedded) else "requiredDependency"
        result.append({"slug": "forgified-fabric-api", "type": kind})
    return {"projects": result}


def multipart(path, metadata):
    boundary = "asterion-" + uuid.uuid4().hex
    prefix = (
        f'--{boundary}\r\nContent-Disposition: form-data; name="metadata"\r\n'
        'Content-Type: application/json\r\n\r\n' + json.dumps(metadata) + '\r\n'
        f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{path.name}"\r\n'
        'Content-Type: application/java-archive\r\n\r\n'
    ).encode()
    return prefix + path.read_bytes() + f"\r\n--{boundary}--\r\n".encode(), f"multipart/form-data; boundary={boundary}"


def upload_with_curl(endpoint, token, body, content_type, progress):
    """Use Windows' native TLS and read early server responses during uploads."""
    curl = shutil.which("curl.exe") or shutil.which("curl")
    if not curl:
        raise RuntimeError("curl is required for file uploads.")
    if any(character in token for character in '\r\n'):
        raise RuntimeError("The upload token contains an invalid newline.")
    work = ROOT / "build/curseforge-transfer"
    work.mkdir(parents=True, exist_ok=True)
    body_path = work / "multipart.bin"
    response_path = work / "response.json"
    stderr_path = work / "progress.log"
    body_path.write_bytes(body)
    # Only stdin contains the token: it never appears in a command line or file.
    escaped = token.replace('\\', '\\\\').replace('"', '\\"')
    config = f'header = "X-Api-Token: {escaped}"\n'
    args = [curl, "--config", "-", "--http1.1", "--request", "POST",
            "--connect-timeout", "30", "--speed-limit", "1", "--speed-time", "900",
            "--keepalive-time", "30", "--expect100-timeout", "5",
            "--header", "Expect: 100-continue", "--header", "Accept: application/json",
            "--header", f"Content-Type: {content_type}", "--progress-bar",
            "--data-binary", "@" + str(body_path), "--output", str(response_path),
            "--write-out", "%{http_code} %{size_upload}", UPLOAD_API + endpoint]
    try:
        with stderr_path.open("wb") as errors:
            process = subprocess.Popen(args, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                       stderr=errors, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            process.stdin.write(config.encode())
            process.stdin.close()
            last_report = time.monotonic()
            while process.poll() is None:
                time.sleep(2)
                now = time.monotonic()
                if now - last_report >= 30:
                    text = stderr_path.read_text(errors="replace")[-8192:]
                    percentages = re.findall(r"(\d+\.\d+)%", text)
                    if percentages:
                        print(f"Transfer progress: {percentages[-1]}%", flush=True)
                    else:
                        print("Transfer running; waiting for curl progress...", flush=True)
                    last_report = now
            stats = process.stdout.read().decode().strip().split()
            if len(stats) == 2:
                status = int(stats[0])
                progress["sentBytes"] = int(float(stats[1]))
            else:
                # Missing telemetry must not make a possibly complete request retryable.
                progress["sentBytes"] = len(body)
                status = 0
            if process.returncode != 0:
                raise RuntimeError(f"curl upload failed (exit {process.returncode}, HTTP {status}, "
                                   f"{progress['sentBytes']}/{len(body)} bytes sent). See {stderr_path}.")
            if not 200 <= status < 300:
                if status == 524:
                    raise RuntimeError("CurseForge's API timed out (HTTP 524) while receiving the file. "
                                       "Increasing the local timeout cannot fix the server limit. "
                                       "Use the author dashboard's upload flow or a faster upload connection.")
                raise RuntimeError(f"CurseForge returned HTTP {status} for {endpoint}; response saved in {response_path}.")
            try:
                return json.loads(response_path.read_text(encoding="utf-8"))
            except ValueError:
                raise RuntimeError("CurseForge returned an invalid upload response; inspect the saved response before retrying.") from None
    finally:
        body_path.unlink(missing_ok=True)


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-id", type=int, default=1671346)
    parser.add_argument("--changelog", type=Path, help="UTF-8 Markdown changelog file")
    parser.add_argument("--release-type", choices=("release", "beta", "alpha"))
    parser.add_argument("--manual-release", action="store_true", default=True, help="Hold approved files for manual publication (always enabled)")
    parser.add_argument("--upload", action="store_true", help="Actually send files; default only writes a local preview")
    args = parser.parse_args()
    if args.upload and (not args.changelog or not args.release_type):
        parser.error("--upload requires --changelog and --release-type")
    changelog = args.changelog.read_text(encoding="utf-8-sig").strip() if args.changelog else "[Changelog required before upload]"
    if not changelog:
        parser.error("Changelog must not be empty")
    # Validate the entire shelf before sending any files.
    subprocess.run([sys.executable, str(ROOT / "tools/verify-v15-release.py")], check=True)
    plan = []
    for version in VERSIONS:
        for loader, label in LOADERS.items():
            path = ROOT / "modbuilds" / f"Asterion-1.5-{loader}-mc{version}.jar"
            metadata = {
                "displayName": f"Asterion 1.5 - {label} - Minecraft {version}",
                "changelog": changelog, "changelogType": "markdown",
                "releaseType": args.release_type or "release",
                "gameVersionNames": [version, label, "Client", "Server"],
                "relations": relations(path, loader),
                "isMarkedForManualRelease": args.manual_release,
            }
            plan.append({"file": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "metadata": metadata})
    # One CurseForge file can support several loaders. Identical jars must share
    # a listing, rather than being uploaded again under a different filename.
    unique = {}
    for item in plan:
        digest = item["sha256"]
        if digest not in unique:
            item["sourceFiles"] = [item["file"]]
            unique[digest] = item
            continue
        shared = unique[digest]
        if shared["metadata"]["relations"] != item["metadata"]["relations"]:
            raise RuntimeError("Identical jars have conflicting dependency metadata")
        shared["sourceFiles"].append(item["file"])
        tags = shared["metadata"]["gameVersionNames"]
        for tag in item["metadata"]["gameVersionNames"]:
            if tag not in tags:
                tags.append(tag)
        labels = [label for label in LOADERS.values() if label in tags]
        shared["metadata"]["displayName"] = f"Asterion 1.5 - {' / '.join(labels)} - Minecraft {tags[0]}"
    plan = list(unique.values())
    plan_path = ROOT / "build/curseforge-upload-plan.json"
    save(plan_path, {"projectId": args.project_id, "files": plan})
    print(f"Preview: {plan_path}")
    for item in plan:
        print(item["metadata"]["displayName"], json.dumps(item["metadata"]["relations"]))
    if not args.upload:
        print("Preview only. No files uploaded.")
        return
    token = os.environ.get("CURSEFORGE_API_TOKEN", "").strip()
    if not token:
        raise RuntimeError("Set CURSEFORGE_API_TOKEN locally before uploading.")
    # Resolve all tags before the first upload, so missing loader/version tags cannot
    # cause a partially uploaded matrix. Names come from CurseForge, never guessed IDs.
    versions = request("/game/versions", token)
    version_types = request("/game/version-types", token)
    for item in plan:
        ids = []
        for name in item["metadata"].pop("gameVersionNames"):
            if name in VERSIONS:
                type_slug = "minecraft-" + "-".join(name.split(".")[:-1])
            elif name in LOADERS.values():
                type_slug = "modloader"
            else:
                type_slug = "environment"
            type_ids = [entry["id"] for entry in version_types if entry["slug"] == type_slug]
            if len(type_ids) != 1:
                raise RuntimeError(f"Expected one CurseForge version type for {type_slug!r}, found {len(type_ids)}.")
            matches = [entry["id"] for entry in versions
                       if entry["name"] == name and entry["gameVersionTypeID"] == type_ids[0]]
            if len(matches) != 1:
                raise RuntimeError(f"Expected one CurseForge version tag for {name!r}, found {len(matches)}.")
            ids.append(matches[0])
        item["metadata"]["gameVersions"] = ids
    save(plan_path, {"projectId": args.project_id, "files": plan})
    receipts_path = ROOT / f"build/curseforge-{args.project_id}-uploads.json"
    receipts = json.loads(receipts_path.read_text(encoding="utf-8-sig")) if receipts_path.exists() else {}
    for item in plan:
        path = Path(item["file"])
        key = path.name + ":" + item["sha256"]
        matching = [receipt for saved_key, receipt in receipts.items()
                    if saved_key.endswith(":" + item["sha256"]) and receipt.get("fileId")]
        if matching:
            print(f"Already received identical jar: {path.name}. Update loader tags on the existing file; do not re-upload.")
            continue
        if key in receipts:
            if receipts[key].get("fileId"):
                print(f"Already uploaded: {path.name} (file {receipts[key]['fileId']})")
                continue
            if receipts[key].get("status") != "failed_incomplete":
                raise RuntimeError(f"An earlier upload of {path.name} has an unknown outcome. Check the project's Files page before clearing its receipt and retrying.")
        # Record intent first. A lost response must never trigger an automatic
        # duplicate upload on the next invocation.
        body, content_type = multipart(path, item["metadata"])
        print(f"Uploading: {path.name}", flush=True)
        receipts[key] = {"status": "pending", "metadata": item["metadata"]}
        save(receipts_path, receipts)
        progress = {"sentBytes": 0}
        try:
            result = upload_with_curl(f"/projects/{args.project_id}/upload-file", token, body, content_type, progress)
        except RuntimeError as exc:
            receipts[key].update(progress, totalBytes=len(body), error=str(exc))
            # Leave a full chunk of uncertainty for a send interrupted mid-write.
            # If more than that remains, the entire jar could not have arrived.
            if progress["sentBytes"] + 256 * 1024 < len(body):
                receipts[key]["status"] = "failed_incomplete"
            save(receipts_path, receipts)
            raise
        if not isinstance(result, dict) or not isinstance(result.get("id"), int):
            raise RuntimeError(f"Upload outcome for {path.name} is unknown; inspect CurseForge before retrying.")
        receipts[key].update(status="uploaded", fileId=result["id"])
        save(receipts_path, receipts)
        print(f"Uploaded: {path.name} (file {result['id']})")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, subprocess.CalledProcessError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
