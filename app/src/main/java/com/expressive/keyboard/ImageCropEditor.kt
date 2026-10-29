package com.expressive.keyboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

// ============================================================================
// DUO: обрезка фоновой картинки клавиатуры — отдельный кадр для «разложено» и «сложено».
// Храним центр кадра (доли картинки) и зум; саму картинку при отрисовке вырезаем по этим числам,
// поэтому фон сам подстраивается под текущую ориентацию и состояние экрана.
// ============================================================================

/** Кадр обрезки: точка фокуса (0..1 по каждой оси) и зум (1 = вписано, больше — ближе). */
@Immutable
data class BgCrop(val cx: Float = 0.5f, val cy: Float = 0.5f, val scale: Float = 1f) {
    fun encode(): String = "$cx,$cy,$scale"

    companion object {
        val DEFAULT = BgCrop()

        fun decode(raw: String?): BgCrop {
            if (raw.isNullOrBlank()) return DEFAULT
            return runCatching {
                val p = raw.split(',')
                BgCrop(
                    cx = p[0].trim().toFloat().coerceIn(0f, 1f),
                    cy = p[1].trim().toFloat().coerceIn(0f, 1f),
                    scale = p[2].trim().toFloat().coerceIn(1f, 4f)
                )
            }.getOrDefault(DEFAULT)
        }
    }
}

/**
 * Рисует картинку так, чтобы кадр [crop] заполнил текущий размер (как ContentScale.Crop, но с фокусом и зумом).
 * Используется и в редакторе обрезки, и в самой клавиатуре — превью совпадает с результатом.
 */
internal fun DrawScope.drawCropped(image: ImageBitmap, crop: BgCrop) {
    val imgW = image.width
    val imgH = image.height
    if (imgW <= 0 || imgH <= 0 || size.width <= 0f || size.height <= 0f) return

    val viewAspect = size.width / size.height
    val imageAspect = imgW.toFloat() / imgH
    // базовое окно: вписываем картинку по «crop»-логике
    var baseW: Float
    var baseH: Float
    if (imageAspect > viewAspect) {
        baseH = imgH.toFloat()
        baseW = baseH * viewAspect
    } else {
        baseW = imgW.toFloat()
        baseH = baseW / viewAspect
    }
    val zoom = crop.scale.coerceIn(1f, 4f)
    val srcW = (baseW / zoom).coerceIn(1f, imgW.toFloat())
    val srcH = (baseH / zoom).coerceIn(1f, imgH.toFloat())

    val cx = (crop.cx * imgW).coerceIn(srcW / 2f, imgW - srcW / 2f)
    val cy = (crop.cy * imgH).coerceIn(srcH / 2f, imgH - srcH / 2f)
    val left = (cx - srcW / 2f).coerceIn(0f, imgW - srcW)
    val top = (cy - srcH / 2f).coerceIn(0f, imgH - srcH)

    drawImage(
        image = image,
        srcOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        srcSize = IntSize(srcW.roundToInt().coerceAtLeast(1), srcH.roundToInt().coerceAtLeast(1)),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1))
    )
}

/** Кадр, сохранённый для текущего состояния экрана. */
internal fun KeyboardSettings.bgCropFor(unfolded: Boolean): BgCrop =
    BgCrop.decode(if (unfolded) bgCropUnfolded else bgCropFolded)

/**
 * Полноэкранный редактор обрезки: перетаскивание двигает фокус, ползунок меняет зум.
 * Две вкладки — «Разложено» и «Сложено», чтобы фон одинаково хорошо смотрелся на обоих экранах.
 */
@Composable
internal fun BackgroundCropDialog(
    version: Long,
    aspect: Float,
    initialUnfolded: BgCrop,
    initialFolded: BgCrop,
    onDismiss: () -> Unit,
    onSave: (unfolded: BgCrop, folded: BgCrop) -> Unit
) {
    val image = rememberBackgroundBitmap(version) ?: return
    val cs = MaterialTheme.colorScheme
    var tab by remember { mutableIntStateOf(0) }
    var unfolded by remember { mutableStateOf(initialUnfolded) }
    var folded by remember { mutableStateOf(initialFolded) }
    val current = if (tab == 0) unfolded else folded
    fun update(c: BgCrop) { if (tab == 0) unfolded = c else folded = c }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = cs.surfaceContainer
        ) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Обрезка фона"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Text(
                            tr("Двигайте картинку пальцем и подберите масштаб для каждого положения экрана"),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, tr("Отмена")) }
                }

                Spacer(Modifier.height(12.dp))
                TabRow(selectedTabIndex = tab, containerColor = cs.surfaceContainer) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(tr("Разложено")) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(tr("Сложено")) })
                }
                Spacer(Modifier.height(16.dp))

                // кадр с тем же соотношением сторон, что и клавиатура
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black)
                            .pointerInput(tab) {
                                detectDragGestures { change, amount ->
                                    change.consume()
                                    val dfx = amount.x / size.width
                                    val dfy = amount.y / size.height
                                    update(
                                        current.copy(
                                            cx = (current.cx - dfx / current.scale.coerceAtLeast(1f)).coerceIn(0f, 1f),
                                            cy = (current.cy - dfy / current.scale.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                        )
                                    )
                                }
                            }
                    ) {
                        Canvas(Modifier.matchParentSize()) { drawCropped(image, current) }
                        // рамка-подсказка по краям кадра
                        Canvas(Modifier.matchParentSize()) {
                            val stroke = 1.5.dp.toPx()
                            drawRect(
                                color = Color.White.copy(alpha = 0.35f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Масштаб"), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(12.dp))
                    Slider(
                        value = current.scale,
                        onValueChange = { update(current.copy(scale = it.coerceIn(1f, 4f))) },
                        valueRange = 1f..4f,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("${(current.scale * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { if (tab == 0) unfolded = BgCrop.DEFAULT else folded = BgCrop.DEFAULT },
                        modifier = Modifier.weight(1f)
                    ) { Text(tr("Сбросить")) }
                    FilledTonalButton(
                        onClick = { unfolded = current; folded = current },
                        modifier = Modifier.weight(1f)
                    ) { Text(tr("Одинаково")) }
                    Button(
                        onClick = { onSave(unfolded, folded) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Готово"))
                    }
                }
            }
        }
    }
}
