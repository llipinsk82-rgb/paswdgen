#!/usr/bin/env python3
from pathlib import Path
import re

path = Path("app/src/main/java/com/blackserv/passwdgen/PremiumUi.kt")
text = path.read_text(encoding="utf-8")

text = text.replace("import androidx.compose.foundation.layout.offset\n", "")
text = text.replace("import androidx.compose.ui.draw.blur\n", "")
text = text.replace(
    "import androidx.compose.ui.draw.clip\n",
    "import androidx.compose.ui.draw.clip\nimport androidx.compose.ui.draw.drawWithCache\n",
    1,
)
text = text.replace(
    "import androidx.compose.ui.graphics.Brush\n",
    "import androidx.compose.ui.geometry.Offset\nimport androidx.compose.ui.graphics.Brush\n",
    1,
)

pattern = re.compile(
    r"@Composable\ninternal fun PremiumAppBackground\(content: @Composable BoxScope\.\(\) -> Unit\) \{.*?\n\}\n\n@Composable\ninternal fun PremiumTopBar",
    re.DOTALL,
)
replacement = '''@Composable
internal fun PremiumAppBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF06121A), PgBackground, Color(0xFF02070B)),
                ),
            )
            .drawWithCache {
                val cyanGlow = Brush.radialGradient(
                    colors = listOf(PgCyan.copy(alpha = 0.07f), Color.Transparent),
                    center = Offset(size.width * 0.04f, size.height * 0.27f),
                    radius = size.minDimension * 0.92f,
                )
                val blueGlow = Brush.radialGradient(
                    colors = listOf(Color(0xFF2565D9).copy(alpha = 0.055f), Color.Transparent),
                    center = Offset(size.width * 0.96f, size.height * 0.84f),
                    radius = size.minDimension * 0.78f,
                )
                onDrawBehind {
                    drawRect(cyanGlow)
                    drawRect(blueGlow)
                }
            },
    ) {
        content()
    }
}

@Composable
internal fun PremiumTopBar'''
text, count = pattern.subn(replacement, text, count=1)
if count != 1:
    raise SystemExit(f"PremiumAppBackground replacement count: {count}")

path.write_text(text, encoding="utf-8")
