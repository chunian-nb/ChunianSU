#!/usr/bin/env python3
"""Dependency-free structural checks; NOT a Kotlin build or an Android device test."""
from __future__ import annotations

import ast
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ANDROID = "{http://schemas.android.com/apk/res/android}"
JAVA = "manager/app/src/main/java/me/weishu/kernelsu/"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def check() -> None:
    config = json.loads(read("tools/chuniansu/config.json"))
    gradle = read("manager/app/build.gradle.kts")
    require('else "me.weishu.chuniansu"' in gradle, "Wrong default applicationId")
    require('else "ChunianSU"' in gradle, "Wrong default application name")
    require('namespace = "me.weishu.kernelsu"' in gradle, "Do not blindly rename the JNI/source namespace")
    require('applicationId = managerPackageName' in gradle, "applicationId is not wired to the custom package")
    require('CHUNIAN_VERSION_NAME' in gradle, "Named version property is not wired up")
    version = re.search(r"(?m)^CHUNIAN_VERSION_NAME=(v\d+\.\d+\.\d+(?:[-.][A-Za-z0-9]+)*)$", read("manager/gradle.properties"))
    require(version is not None, "CHUNIAN_VERSION_NAME must use a v1.0.0-style version")

    manifest = ET.fromstring(read("manager/app/src/main/AndroidManifest.xml"))
    app = manifest.find("application")
    require(app is not None, "Missing application")
    main = next((a for a in app.findall("activity") if a.get(ANDROID + "name") == ".ui.MainActivity"), None)
    require(main is not None, "Missing original MainActivity")
    require(main.get(ANDROID + "enabled", "true") != "false", "Never disable the real MainActivity")
    require(main.get(ANDROID + "exported") == "true", "Explicit recovery activity must remain accessible")
    launchers = []
    for component in app:
        for intent_filter in component.findall("intent-filter"):
            actions = {a.get(ANDROID + "name") for a in intent_filter.findall("action")}
            categories = {c.get(ANDROID + "name") for c in intent_filter.findall("category")}
            if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in categories:
                launchers.append(component)
    require(len(launchers) == 1 and launchers[0].tag == "activity-alias", "Expected one launcher alias only")
    alias = launchers[0]
    require(alias.get(ANDROID + "name") == "${applicationId}.LauncherAlias", "Wrong alias name")
    require(alias.get(ANDROID + "targetActivity") == "me.weishu.kernelsu.ui.MainActivity", "Wrong alias target")
    require(alias.get(ANDROID + "enabled") == "true", "Launcher must be visible on first install")
    require(list(app).index(main) < list(app).index(alias), "Alias target must be declared first")
    receiver = next((r for r in app.findall("receiver") if r.get(ANDROID + "name", "").endswith(".SecretCodeReceiver")), None)
    require(receiver is not None, "Missing dialer receiver")
    actions = {a.get(ANDROID + "name") for a in receiver.findall("intent-filter/action")}
    require(actions == {"android.telephony.action.SECRET_CODE", "android.provider.Telephony.SECRET_CODE"}, "Wrong dialer actions")
    data = receiver.find("intent-filter/data")
    require(data is not None and data.get(ANDROID + "scheme") == "android_secret_code" and data.get(ANDROID + "host") == "888", "Wrong dialer URI")
    for path, call in [("Miuix", "true"), ("Material", "false")]:
        text = read(JAVA + f"ui/screen/settings/Settings{path}.kt")
        require(text.count(f"ChunianPrivacySettings(miuix = {call})") == 1, f"Missing {path} settings entry")
    updater = read(JAVA + "ui/util/Downloader.kt")
    require(f'https://api.github.com/repos/{config["repository"]}/releases/latest' in updater, "Wrong fork update URL")
    require('name.startsWith("ChunianSU_")' in updater, "Updater must filter for the custom APK")
    require("api.github.com/repos/tiann/KernelSU" not in updater, "Updater still points upstream")
    for locale in ("values", "values-zh-rCN"):
        tree = ET.fromstring(read(f"manager/app/src/main/res/{locale}/chunian_strings.xml"))
        require(len(tree.findall("string")) == 8, f"Incomplete {locale} strings")
    helper = read(JAVA + "ui/privacy/LauncherPrivacy.kt")
    require("DONT_KILL_APP" in helper and "setComponentEnabledSetting" in helper, "Wrong launcher-toggle implementation")
    require('cargo:rustc-env=KSU_PACKAGE_NAME=me.weishu.chuniansu' in read("userspace/ksud/build.rs"), "Wrong daemon package fallback")
    require('KSU_PACKAGE_NAME: me.weishu.chuniansu' in read(".github/workflows/ksud.yml"), "Wrong daemon build package")
    cargo_lock = read("Cargo.lock")
    ksud_cargo = read("userspace/ksud/Cargo.toml")
    ksuinit_cargo = read("userspace/ksuinit/Cargo.toml")
    mirrors = {
        "adb_client": ("https://github.com/ReSukiSU/adb_client", "d97a966435bebaa55017834869dec08150826aa7"),
        "java-properties": ("https://github.com/ReSukiSU/java-properties.git", "42a4aa941b70ded2dd3be9e9f892471023e70229"),
        "ksu_props": ("https://github.com/ReSukiSU/ksu_props", "ddb6ee7294467f7f25bad2118e9e24eee104144b"),
        "rustix": ("https://github.com/ReSukiSU/rustix.git", "4a53fbc7cb7a07cabe87125cc21dbc27db316259"),
    }
    for name, (url, commit) in mirrors.items():
        require(url in cargo_lock or url in ksud_cargo or url in ksuinit_cargo, f"Missing dependency mirror for {name}")
        require(commit in cargo_lock, f"Dependency {name} is not pinned to the audited commit")
    require("github.com/Kernel-SU/adb_client" not in cargo_lock + ksud_cargo, "Unavailable adb_client source remains")
    require("github.com/Kernel-SU/java-properties" not in cargo_lock + ksud_cargo, "Unavailable java-properties source remains")
    require("github.com/Kernel-SU/ksu_props" not in cargo_lock + ksud_cargo, "Unavailable ksu_props source remains")
    require("github.com/Kernel-SU/rustix" not in cargo_lock + ksuinit_cargo, "Unavailable rustix source remains")
    workflow = read(".github/workflows/chuniansu.yml")
    for token in ("expected_size2:", "expected_hash2:", "pack_lkm: true", "pack_ksuinit: true", "repack_apk.py repack", "verify_apk.py", "name: ChunianSU"):
        require(token in workflow, f"Workflow is missing {token}")
    require("workflow_dispatch:" in workflow and "pull_request" not in workflow, "Signing workflow must be explicitly dispatched, not run on PR code")
    require('safe.directory "$GITHUB_WORKSPACE"' in read(".github/workflows/ddk-lkm.yml"), "LKM workflow still assumes the upstream checkout name")
    for path in (ROOT / "tools/chuniansu").glob("*.py"):
        ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    print("Static customization checks passed.")
    print("This does NOT perform a Gradle build, flash a device or test its dialer.")


def main() -> int:
    try:
        check()
    except (OSError, ValueError, RuntimeError, ET.ParseError, SyntaxError) as exc:
        print(f"Static check FAILED: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
