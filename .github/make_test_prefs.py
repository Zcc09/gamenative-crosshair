#!/usr/bin/env python3
"""Generate the SharedPreferences XML used by the E2E test.

One target app (com.android.settings) with a large magenta center dot so the
screenshot pixel check is unambiguous.
"""
import json

# 0xFFFF00FF as a signed 32-bit int (Android ARGB)
MAGENTA = (0xFFFF00FF - (1 << 32))

spec = {
    "shape": "DOT",
    "color": MAGENTA,
    "size": 12.0,
    "thickness": 3.0,
    "gap": 0.0,
    "dot": 14.0,
    "opacity": 100,
    "outline": False,
    "outlineColor": -16777216,
    "outlineWidth": 1.0,
    "offsetX": 0.0,
    "offsetY": 0.0,
}
profiles = [{"id": "p-test", "name": "E2E Magenta Dot", "spec": spec}]
rules = [
    {"id": "r-1", "label": "Settings", "pkg": "com.android.settings",
     "kind": "APP", "enabled": True, "profileId": "p-test"},
    {"id": "r-2", "label": "GameNative", "pkg": "app.gamenative",
     "kind": "APP", "enabled": True, "profileId": None},
]


def esc(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


lines = [
    "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>",
    "<map>",
    '    <boolean name="master_enabled" value="true" />',
    '    <boolean name="only_selected" value="true" />',
    '    <boolean name="seeded" value="true" />',
    '    <string name="active_profile">p-test</string>',
    '    <string name="profiles">' + esc(json.dumps(profiles)) + "</string>",
    '    <string name="rules">' + esc(json.dumps(rules)) + "</string>",
    "</map>",
]
print("\n".join(lines))
