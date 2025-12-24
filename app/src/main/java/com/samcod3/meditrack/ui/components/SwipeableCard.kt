package com.samcod3.meditrack.ui.components

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Configuration for a swipe action.
 */
data class SwipeActionConfig(
    val icon: ImageVector,
    val label: String,
    val startColor: Color,
    val endColor: Color,
    val requiresConfirmation: Boolean = true
)

/**
 * Reusable swipeable card with confirmation flow.
 * 
 * @param onSwipeRight Action when swiped right (StartToEnd). Null = disabled.
 * @param onSwipeLeft Action when swiped left (EndToStart). Null = disabled.
 * @param rightActionConfig Visual config for right swipe.
 * @param leftActionConfig Visual config for left swipe.
 * @param autoPeek If true, shows a brief "peek" animation on first load.
 * @param content The card content.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun SwipeableCard(
    onSwipeRight: (() -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    rightActionConfig: SwipeActionConfig? = null,
    leftActionConfig: SwipeActionConfig? = null,
    autoPeek: Boolean = false,
    content: @Composable () -> Unit
) {
    // Pending action state
    var pendingAction by remember { mutableStateOf<PendingSwipeAction?>(null) }
    
    // Auto-peek state
    var peekOffset by remember { mutableStateOf(0f) }
    val animatedPeekOffset by androidx.compose.animation.core.animateFloatAsState(
        targetValue = peekOffset,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 500,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "peek_animation"
    )
    
    // Trigger auto-peek animation once on composition
    LaunchedEffect(autoPeek) {
        if (autoPeek) {
            kotlinx.coroutines.delay(300) // Small delay before peek
            peekOffset = 35f // Visible peek hint
            kotlinx.coroutines.delay(600) // Hold peek
            peekOffset = 0f // Return
        }
    }
    
    AnimatedContent(
        targetState = pendingAction,
        transitionSpec = { fadeIn() with fadeOut() },
        label = "swipe_card_animation"
    ) { action ->
        if (action != null) {
            // Confirmation mode
            SwipeConfirmationRow(
                icon = action.config.icon,
                label = action.config.label,
                backgroundColor = action.config.endColor,
                onConfirm = {
                    action.onConfirm()
                    pendingAction = null
                },
                onCancel = { pendingAction = null }
            )
        } else {
            // Normal swipeable mode
            val dismissStateRef = remember { mutableStateOf<SwipeToDismissBoxState?>(null) }
            
            val dismissState = rememberSwipeToDismissBoxState(
                positionalThreshold = { totalDistance -> totalDistance * 0.5f },
                confirmValueChange = { dismissValue ->
                    val progress = dismissStateRef.value?.progress ?: 0f
                    val isPastThreshold = progress >= 0.5f
                    
                    if (!isPastThreshold && dismissValue != SwipeToDismissBoxValue.Settled) {
                        return@rememberSwipeToDismissBoxState false
                    }
                    
                    when (dismissValue) {
                        SwipeToDismissBoxValue.StartToEnd -> {
                            if (onSwipeRight != null && rightActionConfig != null) {
                                if (rightActionConfig.requiresConfirmation) {
                                    pendingAction = PendingSwipeAction(rightActionConfig, onSwipeRight)
                                } else {
                                    onSwipeRight()
                                }
                            }
                            false
                        }
                        SwipeToDismissBoxValue.EndToStart -> {
                            if (onSwipeLeft != null && leftActionConfig != null) {
                                if (leftActionConfig.requiresConfirmation) {
                                    pendingAction = PendingSwipeAction(leftActionConfig, onSwipeLeft)
                                } else {
                                    onSwipeLeft()
                                }
                            }
                            false
                        }
                        else -> false
                    }
                }
            )
            dismissStateRef.value = dismissState
            
            // Haptic feedback
            val context = LocalContext.current
            val isAboveThreshold = dismissState.progress >= 0.5f &&
                dismissState.dismissDirection != SwipeToDismissBoxValue.Settled
            
            LaunchedEffect(isAboveThreshold) {
                if (isAboveThreshold) {
                    triggerHapticFeedback(context)
                }
            }
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .heightIn(min = 72.dp)
            ) {
                // Background layer visible during peek
                if (animatedPeekOffset > 0f && rightActionConfig != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(rightActionConfig.endColor),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Icon(
                            imageVector = rightActionConfig.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(20.dp)
                        )
                    }
                }
                
                // Main SwipeToDismissBox with offset
                Box(
                    modifier = Modifier.offset(x = animatedPeekOffset.dp)
                ) {
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = onSwipeRight != null,
                        enableDismissFromEndToStart = onSwipeLeft != null,
                        backgroundContent = {
                            val direction = dismissState.dismissDirection
                            val progress = dismissState.progress
                            
                            val (config, alignment) = when (direction) {
                                SwipeToDismissBoxValue.StartToEnd -> rightActionConfig to Alignment.CenterStart
                                SwipeToDismissBoxValue.EndToStart -> leftActionConfig to Alignment.CenterEnd
                                else -> null to Alignment.Center
                            }
                            
                            if (config != null && direction != SwipeToDismissBoxValue.Settled) {
                                val fraction = (progress / 0.5f).coerceIn(0f, 1f)
                                val backgroundColor = lerp(config.startColor, config.endColor, fraction)
                                
                                // Extract action name from label (remove "¿" and "?")
                                val actionName = config.label.replace("¿", "").replace("?", "")
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(backgroundColor)
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = alignment
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = if (direction == SwipeToDismissBoxValue.StartToEnd) 
                                            Arrangement.Start else Arrangement.End
                                    ) {
                                        if (direction == SwipeToDismissBoxValue.StartToEnd) {
                                            Icon(
                                                imageVector = config.icon,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = actionName,
                                                color = Color.White,
                                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                            )
                                        } else {
                                            Text(
                                                text = actionName,
                                                color = Color.White,
                                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = config.icon,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        content = { content() }
                    )
                }
            }
        }
    }
}


@Composable
private fun SwipeConfirmationRow(
    icon: ImageVector,
    label: String,
    backgroundColor: Color,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancelar",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(32.dp))
            
            FilledIconButton(
                onClick = onConfirm,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White,
                    contentColor = backgroundColor
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Confirmar",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

private data class PendingSwipeAction(
    val config: SwipeActionConfig,
    val onConfirm: () -> Unit
)

private fun triggerHapticFeedback(context: Context) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    if (vibrator?.hasVibrator() == true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10)
        }
    }
}
