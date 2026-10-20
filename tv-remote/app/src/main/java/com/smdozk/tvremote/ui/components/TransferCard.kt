package com.smdozk.tvremote.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smdozk.tvremote.model.Notice
import com.smdozk.tvremote.model.Transfer
import com.smdozk.tvremote.model.TransferKind
import com.smdozk.tvremote.model.TransferStage
import com.smdozk.tvremote.ui.formatSize
import com.smdozk.tvremote.ui.theme.Cyan
import com.smdozk.tvremote.ui.theme.ErrorColor
import com.smdozk.tvremote.ui.theme.OnSurface
import com.smdozk.tvremote.ui.theme.OnSurfaceDim
import com.smdozk.tvremote.ui.theme.SuccessGreen
import com.smdozk.tvremote.ui.theme.SurfaceVariant
import kotlinx.coroutines.delay

@Composable
fun TransferCard(transfer: Transfer, modifier: Modifier = Modifier) {
    val finishing = transfer.stage == TransferStage.Finishing
    val (icon, headline) = when (transfer.kind) {
        TransferKind.Upload -> Icons.Default.FileUpload to "TV'ye gönderiliyor"
        TransferKind.Download -> Icons.Default.FileDownload to "Telefona indiriliyor"
        TransferKind.Install -> Icons.Default.Android to if (finishing) "TV'de kuruluyor…" else "APK TV'ye gönderiliyor"
    }
    val progress = transfer.progress
    val animated by animateFloatAsState(progress ?: 0f, label = "transfer")

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(38.dp).background(Cyan.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Cyan, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(headline, color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(transfer.title, color = OnSurfaceDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (progress != null && !finishing) {
                    Text("${(progress * 100).toInt()}%", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            if (progress == null || finishing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Cyan,
                    trackColor = SurfaceVariant,
                    strokeCap = StrokeCap.Round
                )
            } else {
                LinearProgressIndicator(
                    progress = { animated },
                    modifier = Modifier.fillMaxWidth(),
                    color = Cyan,
                    trackColor = SurfaceVariant,
                    strokeCap = StrokeCap.Round
                )
            }
            Text(
                text = if (transfer.total > 0) "${formatSize(transfer.bytes)} / ${formatSize(transfer.total)}" else formatSize(transfer.bytes),
                color = OnSurfaceDim,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun NoticeBanner(notice: Notice?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(4500)
            onDismiss()
        }
    }
    // Keeps the last text on screen while the banner animates out.
    val last = remember { LastNotice() }
    if (notice != null) last.value = notice
    AnimatedVisibility(
        visible = notice != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        val current = last.value ?: return@AnimatedVisibility
        val color = if (current.isError) ErrorColor else SuccessGreen
        Text(
            text = current.text,
            color = color,
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

private class LastNotice {
    var value: Notice? = null
}
