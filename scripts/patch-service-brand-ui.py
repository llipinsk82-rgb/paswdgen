#!/usr/bin/env python3
from pathlib import Path
import re

path = Path("app/src/main/java/com/blackserv/passwdgen/PremiumUi.kt")
text = path.read_text(encoding="utf-8")

old_call = "ServiceMark(entry.service)"
new_call = "ServiceBrandMark(service = entry.service, website = entry.website)"
if text.count(old_call) != 1:
    raise SystemExit(f"Expected exactly one ServiceMark call, found {text.count(old_call)}")
text = text.replace(old_call, new_call, 1)

pattern = re.compile(
    r"\n@Composable\nprivate fun ServiceMark\(service: String\) \{.*?\n\}\n\n@Composable\nprivate fun CredentialLine",
    re.DOTALL,
)
text, count = pattern.subn("\n@Composable\nprivate fun CredentialLine", text, count=1)
if count != 1:
    raise SystemExit(f"Expected one legacy ServiceMark block, replaced {count}")

path.write_text(text, encoding="utf-8")
