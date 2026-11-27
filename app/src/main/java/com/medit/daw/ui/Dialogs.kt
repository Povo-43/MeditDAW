package com.medit.daw.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.medit.daw.BuildConfig
import com.medit.daw.audio.TrackAudioStatus
import com.medit.daw.editor.EditorUiState
import com.medit.daw.editor.EditorViewModel
import com.medit.daw.model.GridUnit
import com.medit.daw.model.MeterMap
import com.medit.daw.model.Note
import com.medit.daw.model.Project
import com.medit.daw.model.TimeSignature
import com.medit.daw.model.noteName
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.medit.daw.R

@Composable
internal fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val scrimInteraction = remember { MutableInteractionSource() }
        val surfaceInteraction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                val maxDialogHeight = maxHeight.coerceAtMost(700.dp)
                val maxScrollHeight = (maxDialogHeight - 150.dp).coerceAtLeast(140.dp)
                Surface(
                    modifier = Modifier
                        .widthIn(max = 620.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxDialogHeight)
                        .clickable(
                            interactionSource = surfaceInteraction,
                            indication = null,
                            onClick = {},
                        ),
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shadowElevation = 24.dp,
                ) {
                    Column(Modifier.padding(22.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (icon != null) {
                                Surface(
                                    modifier = Modifier.size(44.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.titleLarge)
                                if (subtitle != null) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Mist,
                                    )
                                }
                            }
                            IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_close))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Column(
                            Modifier
                                .heightIn(max = maxScrollHeight)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            content()
                        }
                        if (actions != null) {
                            Spacer(Modifier.height(14.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                                content = actions,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Tempo and meter
// ----------------------------------------------------------------------

@Composable
fun BpmDialog(
    initial: Double,
    onDismiss: () -> Unit,
    onApply: (Double) -> Unit,
) {
    var text by remember {
        mutableStateOf(
            if (abs(initial - initial.roundToInt()) < 0.05) initial.roundToInt().toString()
            else String.format(java.util.Locale.JAPAN, "%.1f", initial)
        )
    }
    val parsed = text.toDoubleOrNull()
    val valid = parsed != null && parsed in 20.0..400.0
    AppDialog(
        title = stringResource(R.string.settings_tempo_section),
        subtitle = "20〜400 BPM",
        icon = Icons.Rounded.Speed,
        onDismiss = onDismiss,
        actions = {
            DialogSecondaryButton(stringResource(R.string.common_cancel), Icons.Rounded.Close, onDismiss)
            Spacer(Modifier.size(6.dp))
            DialogPrimaryButton(
                text = stringResource(R.string.common_apply),
                icon = Icons.Rounded.Check,
                enabled = valid,
                onClick = { onApply(parsed!!.coerceIn(20.0, 400.0)) },
            )
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { value ->
                text = value.filter { it.isDigit() || it == '.' }.take(6)
            },
            label = { Text("BPM") },
            singleLine = true,
            isError = !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(60.0, 80.0, 100.0, 120.0, 140.0, 160.0, 200.0).forEach { bpm ->
                FilterChip(
                    selected = abs((parsed ?: -1.0) - bpm) < 0.05,
                    onClick = { text = bpm.roundToInt().toString() },
                    label = { Text(bpm.roundToInt().toString()) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.settings_tempo_export_note),
            style = MaterialTheme.typography.labelSmall,
            color = Mist,
        )
    }
}

@Composable
fun MeterDialog(
    initial: TimeSignature,
    onDismiss: () -> Unit,
    onApply: (numerator: Int, denominator: Int) -> Unit,
) {
    var numerator by remember { mutableIntStateOf(initial.numerator) }
    var denominator by remember { mutableIntStateOf(initial.denominator) }
    val valid = numerator in 1..32 && denominator in listOf(1, 2, 4, 8, 16, 32)
    AppDialog(
        title = stringResource(R.string.meter_title),
        subtitle = stringResource(R.string.meter_desc),
        icon = Icons.Rounded.Timer,
        onDismiss = onDismiss,
        actions = {
            DialogSecondaryButton(stringResource(R.string.common_cancel), Icons.Rounded.Close, onDismiss)
            Spacer(Modifier.size(6.dp))
            DialogPrimaryButton(
                text = stringResource(R.string.common_apply),
                icon = Icons.Rounded.Check,
                enabled = valid,
                onClick = { onApply(numerator, denominator) },
            )
        },
    ) {
        Text(stringResource(R.string.meter_numerator), style = MaterialTheme.typography.labelMedium, color = Mist)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepSquare("−") { numerator = (numerator - 1).coerceIn(1, 32) }
            Text(
                numerator.toString(),
                modifier = Modifier.widthIn(min = 74.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
            )
            StepSquare("+") { numerator = (numerator + 1).coerceIn(1, 32) }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.meter_denominator), style = MaterialTheme.typography.labelMedium, color = Mist)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(1, 2, 4, 8, 16, 32).forEach { d ->
                FilterChip(
                    selected = denominator == d,
                    onClick = { denominator = d },
                    label = { Text(d.toString()) },
                    modifier = Modifier.heightIn(min = 42.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                stringResource(R.string.meter_label, numerator, denominator),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Paper,
            )
        }
    }
}

// ----------------------------------------------------------------------
// Track picker
// ----------------------------------------------------------------------

@Composable
fun TrackPickerDialog(
    project: Project,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AppDialog(
        title = stringResource(R.string.track_edit_target),
        subtitle = stringResource(R.string.track_edit_others),
        icon = Icons.Rounded.LibraryMusic,
        onDismiss = onDismiss,
        actions = {
            DialogSecondaryButton(stringResource(R.string.common_close), Icons.Rounded.Close, onDismiss)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            project.tracks.forEachIndexed { index, track ->
                val active = index == project.activeTrack
                Surface(
                    onClick = { onSelect(index) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 62.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else Paper,
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stringResource(R.string.browser_track_info, track.noteCount, track.channel + 1) +
                                    if (track.isDrum) stringResource(R.string.drum_suffix) else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = Mist,
                            )
                        }
                        if (active) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.common_selected),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Note inspector
// ----------------------------------------------------------------------

@Composable
fun NoteInspectorDialog(
    note: Note,
    project: Project,
    onDismiss: () -> Unit,
    onUpdate: (pitch: Int?, startTick: Int?, durationTicks: Int?, velocity: Int?) -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onPreview: () -> Unit,
) {
    val meter = remember(project.ppq, project.timeSignature, project.timeSignatures) {
        MeterMap(project.ppq, project.timeSignatures, project.timeSignature)
    }
    val step = remember(project.ppq, project.track.channel) {
        val grid = GridUnit.entries.firstOrNull { it != GridUnit.OFF } ?: GridUnit.SIXTEENTH
        grid.ticks(project.ppq).coerceAtLeast(1)
    }
    var velocity by remember(note.id, note.velocity) {
        mutableFloatStateOf(note.velocity.toFloat())
    }
    val barLabel = meter.positionAt(note.startTick).label()

    AppDialog(
        title = stringResource(R.string.settings_notes_edit),
        subtitle = stringResource(R.string.note_edit_selected),
        icon = Icons.Rounded.Edit,
        onDismiss = onDismiss,
        actions = {
            TextButton(
                onClick = onDelete,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.size(5.dp))
                Text(stringResource(R.string.common_delete))
            }
            TextButton(onClick = onDuplicate) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.size(5.dp))
                Text(stringResource(R.string.dock_duplicate))
            }
            Spacer(Modifier.size(6.dp))
            DialogPrimaryButton(stringResource(R.string.common_done), Icons.Rounded.Check, onClick = onDismiss)
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    noteName(note.pitch),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column {
                Text(stringResource(R.string.note_position, barLabel), style = MaterialTheme.typography.labelMedium, color = Mist)
                Text(
                    stringResource(R.string.note_tick_len, note.startTick, note.durationTicks),
                    style = MaterialTheme.typography.labelSmall,
                    color = Mist,
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onPreview, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = stringResource(R.string.common_preview),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(10.dp))

        Text(stringResource(R.string.note_pitch), style = MaterialTheme.typography.labelMedium, color = Mist)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepSquare("−12") { onUpdate((note.pitch - 12).coerceIn(0, 127), null, null, null) }
            Spacer(Modifier.size(5.dp))
            StepSquare("−1") { onUpdate((note.pitch - 1).coerceIn(0, 127), null, null, null) }
            Text(
                noteName(note.pitch),
                modifier = Modifier.widthIn(min = 86.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
            )
            StepSquare("+1") { onUpdate((note.pitch + 1).coerceIn(0, 127), null, null, null) }
            Spacer(Modifier.size(5.dp))
            StepSquare("+12") { onUpdate((note.pitch + 12).coerceIn(0, 127), null, null, null) }
        }

        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.note_start), style = MaterialTheme.typography.labelMedium, color = Mist)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepSquare("−") {
                onUpdate(null, (note.startTick - step).coerceAtLeast(0), null, null)
            }
            Text(
                "$barLabel",
                modifier = Modifier.widthIn(min = 96.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
            )
            StepSquare("+") { onUpdate(null, note.startTick + step, null, null) }
            Spacer(Modifier.weight(1f))
            Text(
                "${note.startTick} tick",
                style = MaterialTheme.typography.labelSmall,
                color = Mist,
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.dock_length_menu), style = MaterialTheme.typography.labelMedium, color = Mist)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepSquare("−") {
                onUpdate(null, null, (note.durationTicks - step).coerceAtLeast(1), null)
            }
            Text(
                "${note.durationTicks} tick",
                modifier = Modifier.widthIn(min = 96.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
            )
            StepSquare("+") { onUpdate(null, null, note.durationTicks + step, null) }
            Spacer(Modifier.size(8.dp))
            SmallAction("½") {
                onUpdate(null, null, (note.durationTicks / 2).coerceAtLeast(1), null)
            }
            Spacer(Modifier.size(5.dp))
            SmallAction("×2") { onUpdate(null, null, note.durationTicks * 2, null) }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.vel_dialog_title), style = MaterialTheme.typography.labelMedium, color = Mist)
            Spacer(Modifier.weight(1f))
            Text(
                velocity.roundToInt().toString(),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = velocity,
            onValueChange = { velocity = it },
            valueRange = 1f..127f,
            onValueChangeFinished = {
                onUpdate(null, null, null, velocity.roundToInt().coerceIn(1, 127))
            },
        )
        Text(
            stringResource(R.string.settings_autosave_undo),
            style = MaterialTheme.typography.labelSmall,
            color = Mist,
        )
    }
}

// ----------------------------------------------------------------------
// Selection value dialogs
// ----------------------------------------------------------------------

@Composable
fun SelectionVelocityDialog(
    ui: EditorUiState,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
) {
    val notes = ui.project?.notes?.filter { it.id in ui.selection } ?: emptyList()
    val initial = if (notes.isEmpty()) ui.prefs.inputVelocity
    else notes.sumOf { it.velocity } / notes.size
    var value by remember { mutableFloatStateOf(initial.toFloat()) }
    AppDialog(
        title = stringResource(R.string.vel_dialog_title),
        subtitle = stringResource(R.string.meta_notes_count, notes.size),
        icon = Icons.AutoMirrored.Rounded.VolumeUp,
        onDismiss = onDismiss,
        actions = {
            DialogSecondaryButton(stringResource(R.string.common_cancel), Icons.Rounded.Close, onDismiss)
            Spacer(Modifier.size(6.dp))
            DialogPrimaryButton(
                stringResource(R.string.common_apply),
                Icons.Rounded.Check,
                onClick = { onApply(value.roundToInt()) },
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.vel_strength), style = MaterialTheme.typography.labelMedium, color = Mist)
            Spacer(Modifier.weight(1f))
            Text(
                value.roundToInt().toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
            )
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            valueRange = 1f..127f,
        )
        Text(
            stringResource(R.string.vel_align_desc),
            style = MaterialTheme.typography.labelSmall,
            color = Mist,
        )
    }
}

@Composable
fun SelectionLengthDialog(
    ui: EditorUiState,
    onDismiss: () -> Unit,
    onApplyTicks: (Int) -> Unit,
    onApplyUnit: (GridUnit) -> Unit,
) {
    val project = ui.project
    val notes = project?.notes?.filter { it.id in ui.selection } ?: emptyList()
    val initial = notes.firstOrNull()?.durationTicks ?: (project?.ppq ?: 480)
    var text by remember { mutableStateOf(initial.toString()) }
    val parsed = text.toIntOrNull()
    AppDialog(
        title = stringResource(R.string.dock_length_menu),
        subtitle = stringResource(R.string.vel_align_count, notes.size),
        icon = Icons.Rounded.Straighten,
        onDismiss = onDismiss,
        actions = {
            DialogSecondaryButton(stringResource(R.string.common_cancel), Icons.Rounded.Close, onDismiss)
            Spacer(Modifier.size(6.dp))
            DialogPrimaryButton(
                text = stringResource(R.string.meter_apply_tick),
                icon = Icons.Rounded.Check,
                enabled = parsed != null && parsed > 0,
                onClick = { parsed?.let { onApplyTicks(it.coerceAtLeast(1)) } },
            )
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { value -> text = value.filter { it.isDigit() }.take(7) },
            label = { Text(stringResource(R.string.note_length)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.vel_length_pick),
            style = MaterialTheme.typography.labelMedium,
            color = Mist,
        )
        Spacer(Modifier.height(6.dp))
        GridUnitChips(
            selected = null,
            includeOff = false,
            onSelect = onApplyUnit,
        )
    }
}

@Composable
fun InputVelocityDialog(
    initial: Int,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
) {
    var value by remember { mutableFloatStateOf(initial.toFloat()) }
    AppDialog(
        title = stringResource(R.string.settings_input_velocity),
        subtitle = stringResource(R.string.vel_input_desc),
        icon = Icons.AutoMirrored.Rounded.VolumeUp,
        onDismiss = onDismiss,
        actions = {
            DialogSecondaryButton(stringResource(R.string.common_cancel), Icons.Rounded.Close, onDismiss)
            Spacer(Modifier.size(6.dp))
            DialogPrimaryButton(
                stringResource(R.string.common_apply),
                Icons.Rounded.Check,
                onClick = { onApply(value.roundToInt()) },
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(8.dp))
            Text("v${value.roundToInt()}", style = MaterialTheme.typography.headlineSmall)
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            valueRange = 1f..127f,
        )
        Text(
            stringResource(R.string.settings_input_velocity_desc),
            style = MaterialTheme.typography.labelSmall,
            color = Mist,
        )
    }
}

// ----------------------------------------------------------------------
// Settings and help
// ----------------------------------------------------------------------

@Composable
fun SettingsDialog(
    ui: EditorUiState,
    vm: EditorViewModel,
    onDismiss: () -> Unit,
    onOpenLibrary: () -> Unit,
    onHelp: () -> Unit,
) {
    AppDialog(
        title = stringResource(R.string.settings_title),
        icon = Icons.Rounded.Settings,
        onDismiss = onDismiss,
        actions = {
            DialogSecondaryButton(stringResource(R.string.help_title), Icons.AutoMirrored.Rounded.HelpOutline, onHelp)
            Spacer(Modifier.size(6.dp))
            DialogPrimaryButton(stringResource(R.string.common_close), Icons.Rounded.Check, onClick = onDismiss)
        },
    ) {
        SectionTitle(stringResource(R.string.settings_section_input), Icons.Rounded.Tune)
        SettingLabel(stringResource(R.string.settings_grid_snap))
        GridUnitChips(selected = ui.prefs.grid, includeOff = true, onSelect = vm::setGrid)
        Spacer(Modifier.height(10.dp))
        SettingLabel(stringResource(R.string.settings_note_length))
        GridUnitChips(selected = ui.prefs.inputDuration, includeOff = false, onSelect = vm::setInputDuration)
        Spacer(Modifier.height(8.dp))
        ToggleRow(
            title = stringResource(R.string.settings_snap),
            description = stringResource(R.string.settings_snap_desc),
            icon = Icons.Rounded.GridOn,
            checked = ui.prefs.snapEnabled,
            onCheckedChange = vm::setSnapEnabled,
        )
        ToggleRow(
            title = stringResource(R.string.settings_finger_edit),
            description = stringResource(R.string.settings_finger_edit_desc),
            icon = Icons.Rounded.TouchApp,
            checked = ui.prefs.fingerEditing,
            onCheckedChange = vm::setFingerEditing,
        )
        ToggleRow(
            title = stringResource(R.string.settings_autoscroll),
            description = stringResource(R.string.settings_autoscroll_desc),
            icon = Icons.Rounded.Swipe,
            checked = ui.prefs.autoScroll,
            onCheckedChange = vm::setAutoScroll,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.settings_input_velocity), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(
                ui.prefs.inputVelocity.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
            )
        }
        Slider(
            value = ui.prefs.inputVelocity.toFloat(),
            onValueChange = { vm.setInputVelocity(it.roundToInt()) },
            valueRange = 1f..127f,
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        SectionTitle(stringResource(R.string.settings_sound_section), Icons.Rounded.GraphicEq)
        AudioStatusCard(ui, vm, onOpenLibrary)

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        SectionTitle(stringResource(R.string.settings_save_section), Icons.Rounded.Save)
        Text(
            stringResource(R.string.settings_autosave_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_footer, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelSmall,
            color = Mist,
        )
    }
}

@Composable
private fun AudioStatusCard(
    ui: EditorUiState,
    vm: EditorViewModel,
    onOpenLibrary: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Audiotrack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                // The active track's engine status (multi-track: one synth per
                // track, each with its own SoundFont and preset).
                val audio = ui.audio[vm.activeTrackIndex()] ?: TrackAudioStatus.Loading
                when (audio) {
                    is TrackAudioStatus.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.library_loading_sound), style = MaterialTheme.typography.bodyMedium)
                    }
                    is TrackAudioStatus.Ready -> {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    audio.presetName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (ui.soundFontCustom) {
                                    Spacer(Modifier.size(8.dp))
                                    SourceBadge()
                                }
                            }
                            Text(
                                stringResource(R.string.settings_sound_zone, audio.fontName, audio.regionCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val fileName = ui.soundFontFileName
                            if (ui.soundFontCustom && !fileName.isNullOrBlank()) {
                                Text(
                                    stringResource(R.string.settings_file, fileName),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    is TrackAudioStatus.Failed -> {
                        Text(
                            audio.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onOpenLibrary,
                enabled = !ui.busy.soundFont,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.size(6.dp))
                Text(if (ui.busy.soundFont) stringResource(R.string.library_loading_busy) else stringResource(R.string.menu_sound_library))
            }
            if (ui.soundFontCustom) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.settings_sound_volume), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${(ui.prefs.soundFontVolume * 100).roundToInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Slider(
                    value = ui.prefs.soundFontVolume,
                    onValueChange = { vm.setSoundFontVolume(it) },
                    valueRange = 0f..2f,
                )
                Text(
                    stringResource(R.string.settings_sound_volume_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_sound_pick_desc),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SourceBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            stringResource(R.string.settings_external_file),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
internal fun SectionTitle(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(7.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Mist)
    Spacer(Modifier.height(6.dp))
}

@Composable
internal fun GridUnitChips(
    selected: GridUnit?,
    includeOff: Boolean,
    onSelect: (GridUnit) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        GridUnit.entries
            .filter { includeOff || it != GridUnit.OFF }
            .forEach { unit ->
                FilterChip(
                    selected = selected == unit,
                    onClick = { onSelect(unit) },
                    label = { Text(unit.label) },
                    modifier = Modifier.heightIn(min = 40.dp),
                )
            }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = RoundedCornerShape(10.dp),
            color = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (checked) MaterialTheme.colorScheme.primary else Mist,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.labelSmall, color = Mist)
        }
        Spacer(Modifier.size(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AppDialog(
        title = stringResource(R.string.help_title),
        subtitle = stringResource(R.string.help_subtitle),
        icon = Icons.AutoMirrored.Rounded.HelpOutline,
        onDismiss = onDismiss,
        actions = {
            DialogPrimaryButton(stringResource(R.string.common_close), Icons.Rounded.Check, onClick = onDismiss)
        },
    ) {
        HelpSection(
            stringResource(R.string.help_pen_section),
            Icons.Rounded.Edit,
            listOf(
                stringResource(R.string.help_pen_hover),
                stringResource(R.string.help_pen_drag_empty),
                stringResource(R.string.help_pen_drag_note_center),
                stringResource(R.string.help_pen_drag_note_edge),
                stringResource(R.string.help_pen_double_tap),
                stringResource(R.string.help_pen_numeric),
                stringResource(R.string.help_pen_eraser),
            ),
        )
        Spacer(Modifier.height(12.dp))
        HelpSection(
            stringResource(R.string.help_finger_section),
            Icons.Rounded.TouchApp,
            listOf(
                stringResource(R.string.help_finger_tap),
                stringResource(R.string.help_finger_drag),
                stringResource(R.string.help_finger_pinch),
                stringResource(R.string.help_finger_long_empty),
                stringResource(R.string.help_finger_long_note),
                stringResource(R.string.help_finger_note_drag),
                stringResource(R.string.help_ruler),
                stringResource(R.string.help_loop_band),
            ),
        )
        Spacer(Modifier.height(12.dp))
        HelpSection(
            stringResource(R.string.help_vel_section),
            Icons.Rounded.BarChart,
            listOf(
                stringResource(R.string.help_vel_open),
                stringResource(R.string.help_vel_drag),
                stringResource(R.string.help_vel_multi),
                stringResource(R.string.help_multiselect_on),
                stringResource(R.string.help_multiselect_pause),
            ),
        )
        Spacer(Modifier.height(12.dp))
        HelpSection(
            stringResource(R.string.help_toolbar_section),
            Icons.Rounded.Tune,
            listOf(
                stringResource(R.string.help_toolbar_selected),
                stringResource(R.string.help_toolbar_unselected),
                stringResource(R.string.help_toolbar_zoom),
                stringResource(R.string.help_align_v),
            ),
        )
        Spacer(Modifier.height(12.dp))
        HelpSection(
            stringResource(R.string.help_save_section),
            Icons.Rounded.Save,
            listOf(
                stringResource(R.string.help_autosave_tabs),
                stringResource(R.string.help_browser),
                stringResource(R.string.help_export_saf),
                stringResource(R.string.help_share),
                stringResource(R.string.help_track_preserved),
                stringResource(R.string.help_meta),
                stringResource(R.string.help_soundfont_settings),
            ),
        )
        Spacer(Modifier.height(12.dp))
        HelpSection(
            stringResource(R.string.help_kb_section),
            Icons.Rounded.Piano,
            listOf(
                stringResource(R.string.help_kb_row1),
                stringResource(R.string.help_kb_row2),
                stringResource(R.string.help_kb_record),
                stringResource(R.string.help_kb_stopped),
            ),
        )
        Spacer(Modifier.height(12.dp))
        HelpSection(
            stringResource(R.string.help_sc_section),
            Icons.Rounded.Keyboard,
            listOf(
                stringResource(R.string.help_sc_space),
                stringResource(R.string.help_sc_undo),
                stringResource(R.string.help_sc_misc),
                stringResource(R.string.help_sc_arrows),
                stringResource(R.string.help_sc_zoom),
            ),
        )
        Spacer(Modifier.height(12.dp))
        HelpSection(
            stringResource(R.string.help_midi_section),
            Icons.Rounded.Piano,
            listOf(
                stringResource(R.string.help_midi_auto),
                stringResource(R.string.help_midi_arm),
                stringResource(R.string.help_midi_pedal),
            ),
        )
    }
}

@Composable
private fun HelpSection(title: String, icon: ImageVector, lines: List<String>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(7.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(5.dp))
    lines.forEach { line ->
        Row(Modifier.padding(vertical = 3.dp)) {
            Text(
                "•",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ----------------------------------------------------------------------
// Other dialogs
// ----------------------------------------------------------------------

@Composable
fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
    title: String = stringResource(R.string.rename_song_title),
    label: String = stringResource(R.string.common_title),
    hint: String = stringResource(R.string.rename_song_hint),
    maxLength: Int = 80,
) {
    var text by remember { mutableStateOf(initial) }
    val untitledFallback = stringResource(R.string.msg_saved_title)
    AppDialog(
        title = title,
        icon = Icons.Rounded.DriveFileRenameOutline,
        onDismiss = onDismiss,
        actions = {
            DialogSecondaryButton(stringResource(R.string.common_cancel), Icons.Rounded.Close, onDismiss)
            Spacer(Modifier.size(6.dp))
            DialogPrimaryButton(
                text = stringResource(R.string.common_change),
                icon = Icons.Rounded.Check,
                onClick = { onApply(text.trim().ifBlank { initial.ifBlank { untitledFallback } }) },
            )
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(maxLength) },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            hint,
            style = MaterialTheme.typography.labelSmall,
            color = Mist,
        )
    }
}

@Composable
fun ConfirmNewDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AppDialog(
        title = stringResource(R.string.menu_new_project),
        icon = Icons.AutoMirrored.Rounded.NoteAdd,
        onDismiss = onDismiss,
        actions = {
            DialogSecondaryButton(stringResource(R.string.common_cancel), Icons.Rounded.Close, onDismiss)
            Spacer(Modifier.size(6.dp))
            DialogPrimaryButton(stringResource(R.string.common_create), Icons.Rounded.Add, onClick = onConfirm)
        },
    ) {
        Text(
            stringResource(R.string.help_new_project_confirm),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ----------------------------------------------------------------------
// Small shared controls
// ----------------------------------------------------------------------

@Composable
internal fun DialogPrimaryButton(
    text: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(text)
    }
}

@Composable
internal fun DialogSecondaryButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.size(5.dp))
        Text(text)
    }
}

@Composable
internal fun StepSquare(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = Paper,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = if (label.length > 1) 13.sp else 21.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(40.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = Paper,
    ) {
        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
