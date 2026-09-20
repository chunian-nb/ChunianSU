#!/usr/bin/env python3
"""Verify the FINAL repacked APK on a GitHub-hosted Android build runner."""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def find_tool(name: str) -> Path:
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk:
        raise RuntimeError("ANDROID_SDK_ROOT/ANDROID_HOME is not set")
    tools = list((Path(sdk) / "build-tools").glob(f"*/{name}"))
    if not tools:
        raise RuntimeError(f"Android SDK tool {name} is not installed")
    tools.sort(key=lambda p: tuple(int(x) for x in re.findall(r"\d+", p.parent.name)))
    return tools[-1]


def run(*args: object) -> str:
    return subprocess.check_output([str(a) for a in args], text=True, stderr=subprocess.STDOUT)


def verify(apk_dir: Path, expected_cert: str) -> dict:
    if not re.fullmatch(r"[0-9a-fA-F]{64}", expected_cert):
        raise RuntimeError("Invalid expected certificate SHA-256")
    apks = list(apk_dir.glob("*.apk"))
    if len(apks) != 1:
        raise RuntimeError(f"Expected exactly one final APK, found {len(apks)}")
    apk = apks[0]
    badging = run(find_tool("aapt2"), "dump", "badging", apk)
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not match or match[1] != "me.weishu.chuniansu":
        raise RuntimeError("Final APK has the wrong applicationId or unreadable badging")
    if "application-label:'ChunianSU'" not in badging:
        raise RuntimeError("Final APK has the wrong application label")
    version_code, version_name = int(match[2]), match[3]
    name_match = re.search(r"v(.+?)_(\d+)-", apk.name)
    if not apk.name.startswith("ChunianSU_") or not name_match or int(name_match[2]) != version_code:
        raise RuntimeError("APK filename does not match the update parser or manifest versionCode")
    signing = run(
        find_tool("apksigner"),
        "verify",
        "--verbose",
        "--print-certs-pem",
        apk,
    )

    pem_blocks = re.findall(
        r"-----BEGIN CERTIFICATE-----\s*(.*?)\s*-----END CERTIFICATE-----",
        signing,
        flags=re.DOTALL,
    )

    certificates = []
    for body in pem_blocks:
        der = base64.b64decode(re.sub(r"\s+", "", body), validate=True)
        certificates.append(hashlib.sha256(der).hexdigest().lower())

    certificates = list(dict.fromkeys(certificates))
    expected = expected_cert.lower()

    print(f"Expected certificate SHA-256: {expected}")
    print(
        "APK signer certificate SHA-256: "
        + (", ".join(certificates) if certificates else "<none>")
    )

    if certificates != [expected]:
        actual = ", ".join(certificates) if certificates else "<none>"
        raise RuntimeError(
            "Final APK signing identity differs from the trusted LKM certificate: "
            f"expected={expected}, actual={actual}"
        )
    with zipfile.ZipFile(apk) as archive:
        for abi in ("arm64-v8a", "x86_64"):
            entry = f"lib/{abi}/libksud.so"
            if entry not in archive.namelist() or archive.getinfo(entry).file_size == 0:
                raise RuntimeError(f"Missing rebuilt daemon: {entry}")
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    apk.with_suffix(apk.suffix + ".sha256").write_text(f"{digest}  {apk.name}\n", encoding="utf-8")
    config = json.loads((ROOT / "tools/chuniansu/config.json").read_text(encoding="utf-8"))
    report = {
        "repository": config["repository"],
        "upstream_commit": config["upstream_commit"],
        "build_commit": run("git", "-C", ROOT, "rev-parse", "HEAD").strip(),
        "apk": apk.name,
        "application_id": match[1],
        "version_code": version_code,
        "version_name": version_name,
        "apk_sha256": digest,
        "certificate_sha256": expected_cert.lower(),
        "launcher_privacy_only": True,
        "dialer_code": "*#*#888#*#*",
        "device_tested_by_this_workflow": False,
        "warning": "Installing this APK alone does not change the currently running kernel trust store.",
    }
    (apk_dir / "BUILD_INFO.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk-dir", type=Path, required=True)
    parser.add_argument("--expected-cert", required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.apk_dir, args.expected_cert), ensure_ascii=False, indent=2))
    except (OSError, RuntimeError, ValueError, subprocess.CalledProcessError, zipfile.BadZipFile) as exc:
        print(f"APK verification FAILED: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
