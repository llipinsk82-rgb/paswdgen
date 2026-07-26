#!/usr/bin/env python3
from pathlib import Path
import re

path = Path("app/src/main/java/com/blackserv/passwdgen/PremiumUi.kt")
text = path.read_text(encoding="utf-8")

old_import = "import androidx.compose.runtime.Composable\n"
new_import = """import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
"""
if old_import not in text:
    raise SystemExit("Composable import marker not found")
text = text.replace(old_import, new_import, 1)

pattern = re.compile(
    r"@Composable\nprivate fun CharacterOptionsPanel\(.*?\n\}\n\n@Composable\nprivate fun PremiumPanel",
    re.DOTALL,
)
replacement = '''@Composable
private fun CharacterOptionsPanel(
    state: AppUiState,
    onOptionsChange: ((PasswordOptions) -> PasswordOptions) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val enabledCount = listOf(
        state.options.lowerCase,
        state.options.upperCase,
        state.options.digits,
        state.options.special,
        state.options.avoidAmbiguous,
    ).count { it }

    PremiumPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PgCyan.copy(alpha = 0.11f))
                    .border(1.dp, PgCyan.copy(alpha = 0.28f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Tune, contentDescription = null, tint = PgCyan, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Opcje znaków", color = PgText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("$enabledCount z 5 aktywnych", color = PgTextMuted, fontSize = 11.sp)
            }
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Zwiń opcje znaków" else "Rozwiń opcje znaków",
                tint = PgCyan,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OptionToggleRow("a", "Małe litery", state.options.lowerCase) {
                    onOptionsChange { options -> options.copy(lowerCase = it) }
                }
                OptionToggleRow("A", "Wielkie litery", state.options.upperCase) {
                    onOptionsChange { options -> options.copy(upperCase = it) }
                }
                OptionToggleRow("1", "Cyfry", state.options.digits) {
                    onOptionsChange { options -> options.copy(digits = it) }
                }
                OptionToggleRow("#", "Znaki specjalne", state.options.special) {
                    onOptionsChange { options -> options.copy(special = it) }
                }
                OptionToggleRow("Ø", "Pomiń podobne", state.options.avoidAmbiguous) {
                    onOptionsChange { options -> options.copy(avoidAmbiguous = it) }
                }
            }
        }
    }
}

@Composable
private fun PremiumPanel'''
text, count = pattern.subn(replacement, text, count=1)
if count != 1:
    raise SystemExit(f"CharacterOptionsPanel replacement count: {count}")

text = text.replace(
    "modifier = Modifier.fillMaxWidth(),\n        shape = PanelShape,",
    "modifier = Modifier.fillMaxWidth().animateContentSize(),\n        shape = PanelShape,",
    1,
)

path.write_text(text, encoding="utf-8")
