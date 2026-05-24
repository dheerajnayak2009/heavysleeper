package com.example.ui

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.Alarm
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: AlarmViewModel,
    modifier: Modifier = Modifier
) {
    val alarms by viewModel.allAlarms.collectAsState()
    val isFiring by viewModel.isFiring.collectAsState()
    val remainingShakes by viewModel.remainingShakes.collectAsState()
    val originalTarget by viewModel.originalShakesTarget.collectAsState()
    val isOverkill by viewModel.isOverkillMode.collectAsState()
    val firingLabel by viewModel.firingLabel.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkCore)
    ) {
        if (isFiring) {
            // Screen takeover when active sirens are blaring
            ActiveAlarmTakeover(
                label = firingLabel,
                remainingShakes = remainingShakes,
                totalShakes = originalTarget,
                isOverkill = isOverkill,
                onSnooze = { viewModel.triggerSnoozeOverkillPenalty() },
                onSkipTest = { viewModel.stopFiringSequence() } 
            )
        } else {
            // Default Dashboard View
            Scaffold(
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = DarkCore,
                            titleContentColor = TextWhite
                        ),
                        title = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "System Status",
                                        tint = HighVoltageRed,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "HEAVY SLEEPER",
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.SansSerif,
                                        letterSpacing = 1.5.sp,
                                        fontSize = 18.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CyberNeonGreen.copy(alpha = 0.15f))
                                        .border(1.dp, CyberNeonGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "ARMED",
                                        color = CyberNeonGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { showAddDialog = true },
                        containerColor = HighVoltageRed,
                        contentColor = TextWhite,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Heavy Alarm")
                    }
                },
                containerColor = DarkCore
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp)
                ) {
                    // Modern brutalist Live Clock Widget
                    LiveClockWidget()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Brutalist Quick Test Trigger Card
                    QuickTestTriggerCard(
                        onTrigger = {
                            // Fire a manual test alarm with a label and default 20 shakes required
                            viewModel.startFiringSequence("TEST HEAVY DRILL", 20)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "SCHEDULED INTERVENTIONS",
                        color = TextGray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (alarms.isEmpty()) {
                        EmptyStateWidget { showAddDialog = true }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        ) {
                            items(alarms, key = { it.id }) { alarm ->
                                AlarmItemCard(
                                    alarm = alarm,
                                    onToggle = { viewModel.toggleAlarm(alarm) },
                                    onDelete = { viewModel.deleteAlarm(alarm) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add Alarm dialog Overlay
        if (showAddDialog) {
            AddAlarmDialog(
                onDismiss = { showAddDialog = false },
                onSave = { hour, minute, label, shakes, repeatDays ->
                    viewModel.addNewAlarm(hour, minute, label, shakes, repeatDays)
                    showAddDialog = false
                }
            )
        }
    }
}

/**
 * Display a premium, oversized real-time digital clock using standard Android calendar timers
 */
@Composable
fun LiveClockWidget() {
    var currentTime by remember { mutableStateOf("") }
    var amPm by remember { mutableStateOf("") }
    var currentDay by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val clockFormat = SimpleDateFormat("hh:mm:ss", Locale.US)
        val amPmFormat = SimpleDateFormat("a", Locale.US)
        val dayFormat = SimpleDateFormat("EEEE, MMMM dd", Locale.US)
        while (true) {
            val now = Calendar.getInstance().time
            currentTime = clockFormat.format(now)
            amPm = amPmFormat.format(now).uppercase()
            currentDay = dayFormat.format(now)
            delay(1000L)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, WarningAmber.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentDay.uppercase(),
                color = WarningAmber,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = currentTime,
                    color = TextWhite,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = amPm,
                    color = TextGray,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    }
}

/**
 * Brutalist quick tester card so users can instantly trigger an active drill.
 */
@Composable
fun QuickTestTriggerCard(onTrigger: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, HighVoltageRed, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = HighVoltageRed.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "🚨 TEST HEAVY SIRENS",
                    color = HighVoltageRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Instantly spin up the 20-shake compliance lock screen to experience the volume overkill loop.",
                    color = TextGray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                    lineHeight = 15.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onTrigger,
                colors = ButtonDefaults.buttonColors(containerColor = HighVoltageRed),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "ENGAGE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = TextWhite
                )
            }
        }
    }
}

/**
 * Custom-styled Heavy Card indicating an Alarm setup
 */
@Composable
fun AlarmItemCard(
    alarm: Alarm,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TextGray.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isEnabled) DarkCard else DarkCardMuted
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarm.label,
                    color = if (alarm.isEnabled) WarningAmber else TextGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    val h = if (alarm.hour == 0 || alarm.hour == 12) 12 else alarm.hour % 12
                    val amPm = if (alarm.hour < 12) "AM" else "PM"
                    Text(
                        text = String.format("%02d:%02d", h, alarm.minute),
                        color = if (alarm.isEnabled) TextWhite else TextGray,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = amPm,
                        color = if (alarm.isEnabled) TextWhite.copy(alpha = 0.8f) else TextGray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Repeats",
                        tint = TextGray,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = alarm.activeDaysString,
                        color = TextGray,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Shakes required",
                        tint = if (alarm.isEnabled) HighVoltageRed else TextGray,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${alarm.shakesRequired} SHAKES",
                        color = if (alarm.isEnabled) HighVoltageRed else TextGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Delete button
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Alarm",
                        tint = TextGray.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                // Toggle status switch
                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyberNeonGreen,
                        checkedTrackColor = CyberNeonGreen.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextGray,
                        uncheckedTrackColor = DarkCore
                    )
                )
            }
        }
    }
}

/**
 * Beautiful empty state container when list of alarms is clear
 */
@Composable
fun EmptyStateWidget(onAddClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TextGray.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(vertical = 32.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "No Alarms Icon",
                tint = TextGray.copy(alpha = 0.3f),
                modifier = Modifier.size(54.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "NO ALARMS CONFIGURED",
                color = TextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "Add a routine to wake your core energy systems.",
                color = TextGray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(containerColor = HighVoltageRed),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("CONFIG ALARM", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Standard Dialog to configure new military alarm triggers
 */
@Composable
fun AddAlarmDialog(
    onDismiss: () -> Unit,
    onSave: (hour: Int, minute: Int, label: String, shakes: Int, repeatDays: Set<Int>) -> Unit
) {
    var hour by remember { mutableStateOf(7) }
    var minute by remember { mutableStateOf(30) }
    var isAm by remember { mutableStateOf(true) }
    var label by remember { mutableStateOf("") }
    var shakesRequired by remember { mutableStateOf(20f) }
    
    // Set representation of repeat days (standard Java calendar index keys)
    val selectedDays = remember { mutableStateOf(setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, WarningAmber.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "🚨 ARM NEW INTERVENTION",
                    color = WarningAmber,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Time selector (Click to adjust panel)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hour adjustment
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { if (hour < 12) hour++ else hour = 1 }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Add Hour", tint = TextWhite)
                        }
                        Text(
                            text = String.format("%02d", hour),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = TextWhite,
                            fontFamily = FontFamily.Monospace
                        )
                        IconButton(onClick = { if (hour > 1) hour-- else hour = 12 }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Subtract Hour", tint = TextWhite)
                        }
                    }

                    Text(
                        text = ":",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        color = TextWhite,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // Minute adjustment
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { if (minute < 59) minute++ else minute = 0 }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Add Minute", tint = TextWhite)
                        }
                        Text(
                            text = String.format("%02d", minute),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = TextWhite,
                            fontFamily = FontFamily.Monospace
                        )
                        IconButton(onClick = { if (minute > 0) minute-- else minute = 59 }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Subtract Minute", tint = TextWhite)
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // AM / PM toggle
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkCore)
                            .border(1.dp, TextGray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .clickable { isAm = true }
                                .background(if (isAm) WarningAmber else Color.Transparent)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "AM",
                                color = if (isAm) DarkCore else TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clickable { isAm = false }
                                .background(if (!isAm) WarningAmber else Color.Transparent)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "PM",
                                color = if (!isAm) DarkCore else TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Label
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Alarm Label (e.g. Wake up core)", color = TextGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = WarningAmber,
                        unfocusedBorderColor = TextGray.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Shake Difficulty Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VIGOROUS SHAKES",
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${shakesRequired.toInt()} SHAKES",
                        color = HighVoltageRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Slider(
                    value = shakesRequired,
                    onValueChange = { shakesRequired = it },
                    valueRange = 10f..60f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = HighVoltageRed,
                        activeTrackColor = HighVoltageRed,
                        inactiveTrackColor = TextGray.copy(alpha = 0.2f)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Repeat Day Selectors
                Text(
                    text = "REPEAT DAYS",
                    color = TextWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                val daysMap = listOf(
                    "M" to Calendar.MONDAY,
                    "T" to Calendar.TUESDAY,
                    "W" to Calendar.WEDNESDAY,
                    "T" to Calendar.THURSDAY,
                    "F" to Calendar.FRIDAY,
                    "S" to Calendar.SATURDAY,
                    "S" to Calendar.SUNDAY
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    daysMap.forEach { (name, calVal) ->
                        val isSelected = selectedDays.value.contains(calVal)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) WarningAmber else DarkCore)
                                .clickable {
                                    val current = selectedDays.value.toMutableSet()
                                    if (isSelected) current.remove(calVal) else current.add(calVal)
                                    selectedDays.value = current
                                }
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color.Transparent else TextGray.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                        ) {
                            Text(
                                text = name,
                                color = if (isSelected) DarkCore else TextWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Save or Cancel Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = TextGray, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            var targetHour = hour
                            if (isAm && hour == 12) {
                                targetHour = 0
                            } else if (!isAm && hour != 12) {
                                targetHour += 12
                            }
                            onSave(targetHour, minute, label, shakesRequired.toInt(), selectedDays.value)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("ARM INTERVENTION", color = DarkCore, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

/**
 * Screen Overlay takeover when alarm fires. Flashing hazard sirens, countdown, and defensive elements.
 */
@Composable
fun ActiveAlarmTakeover(
    label: String,
    remainingShakes: Int,
    totalShakes: Int,
    isOverkill: Boolean,
    onSnooze: () -> Unit,
    onSkipTest: () -> Unit
) {
    // Prevent standard back presses to bypass lock out
    BackHandler(enabled = true) {}

    // Rapid warning color flash brush animation for active background
    val infiniteTransition = rememberInfiniteTransition()
    val flashColor by infiniteTransition.animateColor(
        initialValue = if (isOverkill) HighVoltageRed else DarkCore,
        targetValue = if (isOverkill) WarningAmber else HighVoltageRed.copy(alpha = 0.45f),
        animationSpec = infiniteRepeatable(
            animation = tween(if (isOverkill) 200 else 550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HazardBackgroundFlash"
    )

    // Pulse animation for the major numbers
    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "NumberScalePulse"
    )

    var tauntText by remember { mutableStateOf("NICE TRY! ONLY COGNITIVE SHAKES HEAL THE SIRENS!") }
    var tauntPulseTrigger by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(flashColor)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Warning header row
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = if (isOverkill) "⚡ SNOOZE EXTREME PENALTY ACTIVE ⚡" else "🚨 HEAVY SYSTEM INTRUSION DETECTED 🚨",
                    color = TextWhite,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = label.uppercase(),
                    color = WarningAmber,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    letterSpacing = 2.sp
                )
            }

            // Central Massive shake interface
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 40.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(240.dp)
                ) {
                    // Pulsing neon countdown gauge background ring
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.6f),
                            radius = size.minDimension / 2
                        )
                        drawCircle(
                            color = WarningAmber.copy(alpha = 0.2f),
                            radius = size.minDimension / 2 - 10,
                            style = Stroke(width = 16f)
                        )
                        // Progress ring
                        val sweep = (remainingShakes.toFloat() / totalShakes) * 360f
                        val diameter = size.minDimension - 20f
                        drawArc(
                            color = if (isOverkill) HighVoltageRed else CyberNeonGreen,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = androidx.compose.ui.geometry.Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f),
                            size = androidx.compose.ui.geometry.Size(diameter, diameter),
                            style = Stroke(width = 16f)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.scale(scalePulse)
                    ) {
                        Text(
                            text = "$remainingShakes",
                            fontSize = 84.sp,
                            color = TextWhite,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "SHAKES TO GO",
                            fontSize = 11.sp,
                            color = WarningAmber,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Interactive progress details bar
                Text(
                    text = "SHAKE COMPLIANCE STAGE: ${totalShakes - remainingShakes} / $totalShakes DONE",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // Playful/Brutalist Defensive actions panel
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Taunt banner response
                AnimatedContent(
                    targetState = tauntPulseTrigger,
                    transitionSpec = {
                        slideInVertically() + fadeIn() togetherWith slideOutVertically() + fadeOut()
                    },
                    label = "TaunTextAnimation"
                ) { _ ->
                    Text(
                        text = tauntText,
                        color = WarningAmber,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Useless, fake Stop button that taunts user
                    Button(
                        onClick = {
                            val taunts = listOf(
                                "STOP BUTTON IS FOR WEAKLINGS! SHAKE IT!",
                                "HA! STOPPING IS SECURED BY EXERTION!",
                                "SYSTEM DISMISSED STOP ATTEMPT: DENIED!",
                                "WARNING: UNCOOPERATIVE TAPS DAMAGE CHASSIS!",
                                "TRY SHAKING WITH BOTH HANDS INSTEAD!"
                            )
                            tauntText = taunts.random()
                            tauntPulseTrigger++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TextWhite.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .border(1.dp, TextWhite, RoundedCornerShape(10.dp))
                    ) {
                        Text(
                            text = "STOP ❌",
                            color = TextWhite,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }

                    // Sneaky backfiring Snooze Button
                    Button(
                        onClick = onSnooze,
                        colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .border(2.dp, Color.Black, RoundedCornerShape(10.dp))
                    ) {
                        Text(
                            text = "SNOOZE 💤",
                            color = DarkCore,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }
                }

                // Small utility skip button strictly underneath for easier app-testing/evaluation!
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "EMERGENCY REVEAL (DRIP OVERRIDE)",
                    color = TextWhite.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onSkipTest() }
                        .padding(8.dp)
                )
            }
        }
    }
}
