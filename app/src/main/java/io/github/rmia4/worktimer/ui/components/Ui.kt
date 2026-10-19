package io.github.rmia4.worktimer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Text
import io.github.rmia4.worktimer.ui.theme.LocalTheme
import kotlinx.coroutines.delay

// 크롬 확장 popup.css의 버튼·입력·대화상자 모양을 옮긴 공통 컴포넌트.

enum class Variant { Primary, Outline, Danger, DangerFill, Ghost }

@Composable
fun palette() = LocalTheme.current.palette

@Composable
fun WtButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: Variant = Variant.Primary,
    enabled: Boolean = true,
    small: Boolean = false,
    background: Color? = null,
    border: Color? = null,
    textColor: Color? = null,
) {
    val c = palette()
    val (bg, line, fg) = when (variant) {
        Variant.Primary -> Triple(c.accent, c.accent, c.bg)
        Variant.Outline -> Triple(c.bg, c.line, c.fg)
        Variant.Danger -> Triple(c.bg, c.danger, c.danger)
        Variant.DangerFill -> Triple(c.danger, c.danger, c.bg)
        Variant.Ghost -> Triple(Color.Transparent, Color.Transparent, c.muted)
    }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .alpha(if (!enabled) 0.5f else if (pressed) 0.7f else 1f)
            .defaultMinSize(minHeight = if (small) 34.dp else 44.dp)
            .background(background ?: bg, shape)
            .border(1.dp, border ?: line, shape)
            .clickable(source, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 10.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = textColor ?: fg, fontSize = if (small) 13.sp else 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    multiline: Boolean = false,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    maxLength: Int = Int.MAX_VALUE,
    minHeight: Dp = if (multiline) 110.dp else 44.dp,
    onDone: (() -> Unit)? = null,
) {
    val c = palette()
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.take(maxLength)) },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .border(1.dp, c.line, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = if (multiline) 8.dp else 11.dp),
        textStyle = TextStyle(color = c.fg, fontSize = 15.sp),
        singleLine = !multiline,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboardType,
            imeAction = if (multiline) ImeAction.Default else if (onDone != null) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        cursorBrush = SolidColor(c.accent),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, color = c.muted, fontSize = 15.sp)
                inner()
            }
        },
    )
}

@Composable
fun Label(text: String) = Text(text, color = palette().muted, fontSize = 13.sp)

@Composable
fun Hint(text: String, modifier: Modifier = Modifier, color: Color? = null, textAlign: TextAlign? = null) =
    Text(text, modifier, color = color ?: palette().muted, fontSize = 13.sp, lineHeight = 18.sp, textAlign = textAlign)

// 빈 문구면 그리지 않는다(확장의 .error:empty).
@Composable
fun ErrorText(text: String) {
    if (text.isNotEmpty()) Text(text, color = palette().danger, fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable
fun Saved(text: String) {
    if (text.isNotEmpty()) Text(text, color = palette().accent, fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable
fun Gap(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)

// 확장 .mode: 테두리 버튼 두 개 중 고른 쪽을 강조한다.
@Composable
fun <T> ModeTabs(value: T, options: List<Pair<T, String>>, onChange: (T) -> Unit) {
    val c = palette()
    Gap {
        for ((v, text) in options) {
            val on = v == value
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .border(1.dp, if (on) c.accent else c.line, RoundedCornerShape(6.dp))
                    .clickable(role = Role.Tab) { onChange(v) },
                contentAlignment = Alignment.Center,
            ) {
                Text(text, color = if (on) c.accent else c.muted, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

// 확장 .tabs: 밑줄 탭.
@Composable
fun <T> UnderlineTabs(value: T, options: List<Pair<T, String>>, onChange: (T) -> Unit) {
    val c = palette()
    Column {
        Row(Modifier.fillMaxWidth()) {
            for ((v, text) in options) {
                val on = v == value
                Column(Modifier.weight(1f).clickable(role = Role.Tab) { onChange(v) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.heightIn(min = 38.dp), contentAlignment = Alignment.Center) {
                        Text(text, color = if (on) c.accent else c.muted, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 0.dp).heightIn(min = 2.dp).background(if (on) c.accent else Color.Transparent))
                }
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(c.line))
    }
}

// 확장의 <dialog>. 바깥을 누르거나 뒤로 가기로 onClose를 부른다.
@Composable
fun WtDialog(onClose: () -> Unit, title: String? = null, closeButton: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val c = palette()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .background(c.bg, RoundedCornerShape(8.dp))
                .border(1.dp, c.line, RoundedCornerShape(8.dp)),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (title != null) Text(title, Modifier.padding(end = 28.dp, bottom = 4.dp), color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                content()
            }
            if (closeButton) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(36.dp).clickable(role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Text("×", color = c.muted, fontSize = 22.sp) }
            }
        }
    }
}

@Composable
fun WtCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = palette()
    Column(
        modifier
            .fillMaxWidth()
            .background(c.bg, RoundedCornerShape(8.dp))
            .border(1.dp, c.line, RoundedCornerShape(8.dp))
            .padding(12.dp),
        content = content,
    )
}

// intervalMs마다 바뀌는 현재 시각(서버 시각 보정 offset 포함).
@Composable
fun rememberNow(offset: Long, intervalMs: Long = 1000): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis() + offset) }
    LaunchedEffect(offset, intervalMs) {
        while (true) {
            now.longValue = System.currentTimeMillis() + offset
            delay(intervalMs)
        }
    }
    return now
}

// 설정(톱니) 아이콘. 크롬 확장과 같은 SVG 경로다.
val GearIcon: ImageVector by lazy {
    ImageVector.Builder("gear", 24.dp, 24.dp, 24f, 24f).apply {
        for (d in listOf(
            "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0z",
            "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33h0a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51h0a1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82v0a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z",
        )) {
            addPath(
                pathData = addPathNodes(d),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
}
