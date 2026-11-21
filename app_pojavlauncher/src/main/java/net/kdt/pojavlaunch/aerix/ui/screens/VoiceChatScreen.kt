package net.kdt.pojavlaunch.aerix.ui.screens

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager

private val VoiceBg = Color(0xFF0E1014)
private val VoicePanel = Color(0xFF181B22)
private val VoiceTile = Color(0xFF22262F)
private val MicLive = Color(0xFF22C55E)
private val MicMuted = Color(0xFFDC1E3A)
private val TextPrimary = Color(0xFFF3F4F6)
private val TextMuted = Color(0xFF9CA3AF)

data class VoicePeer(
    val id: String,
    val name: String,
    val speaking: Boolean = false,
    val muted: Boolean = false
)

/**
 * Dedicated Voice Chat destination opened from the mic entry on the navigation rail.
 * Separate from text Socials / Friend Chats.
 */
@Composable
fun VoiceChatScreen(
    activity: Activity,
    onBack: () -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    var micOn by remember { mutableStateOf(false) }
    var inChannel by remember { mutableStateOf(false) }
    var channelName by remember { mutableStateOf("General") }
    var peers by remember {
        mutableStateOf(
            listOf(
                VoicePeer("you", "You", speaking = false, muted = true),
                VoicePeer("1", "Friend 1", speaking = false),
                VoicePeer("2", "Friend 2", speaking = true)
            )
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micOn = true
            peers = peers.map { if (it.id == "you") it.copy(muted = false) else it }
            Toast.makeText(activity, "Microphone on", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(activity, "Microphone permission required for voice chat", Toast.LENGTH_LONG).show()
        }
    }

    fun toggleMic() {
        if (micOn) {
            micOn = false
            peers = peers.map { if (it.id == "you") it.copy(muted = true, speaking = false) else it }
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            micOn = true
            peers = peers.map { if (it.id == "you") it.copy(muted = false) else it }
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun toggleChannel() {
        inChannel = !inChannel
        if (!inChannel) {
            micOn = false
            peers = peers.map { if (it.id == "you") it.copy(muted = true, speaking = false) else it }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoiceBg)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "← Back",
                color = accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            Spacer(Modifier = Modifier.weight(1f))
            Text(
                text = "Voice Chat",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier = Modifier.weight(1f))
            Box(modifier = Modifier.width(64.dp)) // balance title
        }

        Spacer(Modifier = Modifier.height(16.dp))

        // Channel card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(VoicePanel)
                .border(1.dp, VoiceTile, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_microphone),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channelName,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (inChannel) "Connected · ${peers.size} in channel" else "Not connected",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (inChannel) MicMuted.copy(alpha = 0.2f) else accent.copy(alpha = 0.2f))
                        .clickable { toggleChannel() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (inChannel) "Leave" else "Join",
                        color = if (inChannel) MicMuted else accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "In channel",
            color = TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(peers, key = { it.id }) { peer ->
                VoicePeerRow(peer = peer, accent = accent)
            }
        }

        Spacer(Modifier = Modifier.height(12.dp))

        // Big mic control
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val pulse = rememberInfiniteTransition(label = "micPulse")
            val scale by pulse.animateFloat(
                initialValue = 1f,
                targetValue = if (micOn && inChannel) 1.08f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "micScale"
            )
            val micColor by animateColorAsState(
                targetValue = when {
                    !inChannel -> VoiceTile
                    micOn -> MicLive
                    else -> MicMuted
                },
                animationSpec = tween(200),
                label = "micColor"
            )

            Box(
                modifier = Modifier
                    .size(84.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(micColor.copy(alpha = 0.35f), micColor.copy(alpha = 0.08f))
                        )
                    )
                    .border(2.dp, micColor, CircleShape)
                    .clickable(enabled = inChannel) { toggleMic() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_microphone),
                    contentDescription = if (micOn) "Mute microphone" else "Unmute microphone",
                    tint = if (inChannel) Color.White else TextMuted,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = when {
                    !inChannel -> "Join a channel to use the mic"
                    micOn -> "Mic on · tap to mute"
                    else -> "Mic muted · tap to unmute"
                },
                color = TextMuted,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun VoicePeerRow(peer: VoicePeer, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VoicePanel)
            .border(
                width = if (peer.speaking) 1.dp else 0.dp,
                color = if (peer.speaking) accent.copy(alpha = 0.6f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (peer.speaking) accent.copy(alpha = 0.25f) else VoiceTile),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = peer.name.take(1).uppercase(),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = peer.name,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when {
                    peer.muted -> "Muted"
                    peer.speaking -> "Speaking"
                    else -> "Listening"
                },
                color = when {
                    peer.muted -> MicMuted
                    peer.speaking -> MicLive
                    else -> TextMuted
                },
                fontSize = 11.sp
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_microphone),
            contentDescription = null,
            tint = if (peer.muted) MicMuted.copy(alpha = 0.7f) else if (peer.speaking) MicLive else TextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}
