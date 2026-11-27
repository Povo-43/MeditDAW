package com.medit.daw.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medit.daw.data.ProjectSummary
import com.medit.daw.editor.MidiUiState
import com.medit.daw.midi.MidiDeviceOption
import com.medit.daw.model.MidiMeta
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.medit.daw.R

// ----------------------------------------------------------------------
// Project browser
// ----------------------------------------------------------------------

@Composable
fun ProjectBrowserDialog(
    projects: List<ProjectSummary>,
    loading: Boolean,
    activeId: String?,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onImport: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var renameTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ProjectSummary?>(null) }

    AppDialog(
        title = stringResource(R.string.browser_title),
        subtitle = stringResource(R.string.browser_open_desc),
        icon = Icons.Rounded.LibraryMusic,
        onDismiss = onDismiss,
        actions = {
            DialogSecondaryButton(stringResource(R.string.common_close), Icons.Rounded.Close, onDismiss)
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onNew, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.browser_tab_new))
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.browser_tab_import))
            }
        }
        Spacer(Modifier.height(12.dp))
        if (loading && projects.isEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(10.dp))
                Text(stringResource(R.string.common_loading), style = MaterialTheme.typography.bodyMedium)
            }
        } else if (projects.isEmpty()) {
            Text(
                stringResource(R.string.browser_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = Mist,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                projects.forEach { summary ->
                    ProjectBrowserRow(
                        summary = summary,
                        active = summary.id == activeId,
                        onOpen = { onOpen(summary.id) },
                        onRename = { renameTarget = summary },
                        onDelete = { deleteTarget = summary },
                    )
                }
            }
        }
    }

    renameTarget?.let { target ->
        RenameProjectDialog(
            initial = target.title,
            onDismiss = { renameTarget = null },
            onApply = { title ->
                onRename(target.id, title)
                renameTarget = null
            },
        )
    }
    deleteTarget?.let { target ->
        AppDialog(
            title = stringResource(R.string.browser_delete),
            subtitle = target.title,
            icon = Icons.Rounded.Delete,
            onDismiss = { deleteTarget = null },
            actions = {
                DialogSecondaryButton(stringResource(R.string.common_cancel), Icons.Rounded.Close) { deleteTarget = null }
                Spacer(Modifier.size(6.dp))
                DialogPrimaryButton(stringResource(R.string.common_delete), Icons.Rounded.Delete) {
                    onDelete(target.id)
                    deleteTarget = null
                }
            },
        ) {
            Text(
                stringResource(R.string.browser_delete_desc),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ProjectBrowserRow(
    summary: ProjectSummary,
    active: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(14.dp),
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = if (active) MaterialTheme.colorScheme.onPrimary else Paper,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    summary.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.browser_summary, summary.noteCount, formatSavedAt(summary.savedAtMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Mist,
                )
            }
            IconButton(onClick = onRename, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Rounded.DriveFileRenameOutline,
                    contentDescription = stringResource(R.string.common_rename),
                    tint = Paper,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RenameProjectDialog(
    initial: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val untitledFallback = stringResource(R.string.msg_saved_title)
    AppDialog(
        title = stringResource(R.string.common_rename),
        icon = Icons.Rounded.DriveFileRenameOutline,
        onDismiss = onDismiss,
        actions = {
            DialogSecondaryButton(stringResource(R.string.common_cancel), Icons.Rounded.Close, onDismiss)
            Spacer(Modifier.size(6.dp))
            DialogPrimaryButton(stringResource(R.string.common_change), Icons.Rounded.Check) {
                onApply(text.trim().ifBlank { untitledFallback })
            }
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(80) },
            label = { Text(stringResource(R.string.common_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun formatSavedAt(millis: Long): String {
    if (millis <= 0L) return stringResource(R.string.browser_unknown_date)
    val format = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN)
    return format.format(Date(millis))
}

// ----------------------------------------------------------------------
// MIDI META editor
// ----------------------------------------------------------------------

@Composable
fun MidiMetaDialog(
    initial: MidiMeta,
    onDismiss: () -> Unit,
    onApply: (MidiMeta) -> Unit,
) {
    var copyright by remember { mutableStateOf(initial.copyright) }
    var composer by remember { mutableStateOf(initial.composer) }
    var lyricist by remember { mutableStateOf(initial.lyricist) }
    var comment by remember { mutableStateOf(initial.comment) }
    var keySignature by remember { mutableStateOf(initial.keySignature) }
    var minor by remember { mutableStateOf(initial.minor) }

    AppDialog(
        title = stringResource(R.string.menu_meta),
        subtitle = stringResource(R.string.meta_desc),
        icon = Icons.Rounded.GraphicEq,
        onDismiss = onDismiss,
        actions = {
            DialogSecondaryButton(stringResource(R.string.common_cancel), Icons.Rounded.Close, onDismiss)
            Spacer(Modifier.size(6.dp))
            DialogPrimaryButton(stringResource(R.string.common_apply), Icons.Rounded.Check) {
                onApply(
                    MidiMeta(
                        copyright = copyright.trim(),
                        composer = composer.trim(),
                        lyricist = lyricist.trim(),
                        comment = comment.trim(),
                        keySignature = keySignature,
                        minor = minor,
                    )
                )
            }
        },
    ) {
        MetaField(stringResource(R.string.meta_composer), composer) { composer = it }
        Spacer(Modifier.height(8.dp))
        MetaField(stringResource(R.string.meta_lyricist), lyricist) { lyricist = it }
        Spacer(Modifier.height(8.dp))
        MetaField(stringResource(R.string.meta_comment), comment) { comment = it }
        Spacer(Modifier.height(8.dp))
        MetaField(stringResource(R.string.meta_copyright), copyright) { copyright = it }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.meta_key), style = MaterialTheme.typography.labelMedium, color = Mist)
            Spacer(Modifier.weight(1f))
            Text(
                if (keySignature == null) stringResource(R.string.meta_unset)
                else keySignatureName(keySignature!!, minor),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            FilterChip(
                selected = keySignature == null,
                onClick = { keySignature = null },
                label = { Text(stringResource(R.string.common_none)) },
                modifier = Modifier.heightIn(min = 40.dp),
            )
            for (sf in -7..7) {
                FilterChip(
                    selected = keySignature == sf,
                    onClick = { keySignature = sf },
                    label = { Text(keySignatureName(sf, minor)) },
                    modifier = Modifier.heightIn(min = 40.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.meta_minor), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = minor,
                onCheckedChange = { minor = it },
                enabled = keySignature != null,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.meta_export_note),
            style = MaterialTheme.typography.labelSmall,
            color = Mist,
        )
    }
}

@Composable
private fun MetaField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(240)) },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Human-readable key-signature label for -7..7 sharps/flats. */
private fun keySignatureName(sf: Int, minor: Boolean): String {
    val sharps = listOf("C", "G", "D", "A", "E", "B", "F#", "C#")
    val flats = listOf("C", "F", "B♭", "E♭", "A♭", "D♭", "G♭", "C♭")
    val root = if (sf >= 0) sharps[sf.coerceIn(0, 7)] else flats[(-sf).coerceIn(0, 7)]
    return root + if (minor) "m" else ""
}

// ----------------------------------------------------------------------
// External MIDI keyboard
// ----------------------------------------------------------------------

@Composable
fun MidiInputDialog(
    midi: MidiUiState,
    onDismiss: () -> Unit,
    onSelectDevice: (Int?) -> Unit,
    onToggleRecording: () -> Unit,
    onRefresh: () -> Unit,
) {
    AppDialog(
        title = stringResource(R.string.help_midi_section),
        subtitle = stringResource(R.string.midi_from_usb),
        icon = Icons.Rounded.Usb,
        onDismiss = onDismiss,
        actions = {
            DialogSecondaryButton(stringResource(R.string.common_close), Icons.Rounded.Close, onDismiss)
        },
    ) {
        if (!midi.supported) {
            Text(
                stringResource(R.string.midi_unsupported),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            return@AppDialog
        }

        // Recording arm switch.
        Surface(
            color = if (midi.recording) MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                1.dp,
                if (midi.recording) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.outline,
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = if (midi.recording) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (midi.recording) stringResource(R.string.midi_recording) else stringResource(R.string.midi_record_wait),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (midi.recording) MaterialTheme.colorScheme.error else Paper,
                    )
                    Text(
                        if (midi.recording) stringResource(R.string.midi_record_hint4)
                        else stringResource(R.string.midi_record_hint1),
                        style = MaterialTheme.typography.labelSmall,
                        color = Mist,
                    )
                }
                Switch(checked = midi.recording, onCheckedChange = { onToggleRecording() })
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.midi_input_device), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.midi_search), tint = Paper)
            }
        }
        Spacer(Modifier.height(4.dp))

        val receiveDevices = midi.devices.filter { it.canReceive }
        if (receiveDevices.isEmpty()) {
            Text(
                stringResource(R.string.midi_no_device),
                style = MaterialTheme.typography.bodyMedium,
                color = Mist,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                receiveDevices.forEach { device ->
                    MidiDeviceRow(
                        device = device,
                        selected = device.name == midi.connectedName,
                        onClick = { onSelectDevice(device.id) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { onSelectDevice(null) }, enabled = midi.connected) {
                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(5.dp))
                Text(stringResource(R.string.midi_disconnect))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.midi_record_hint3) +
                stringResource(R.string.midi_record_hint2),
            style = MaterialTheme.typography.labelSmall,
            color = Mist,
        )
    }
}

@Composable
private fun MidiDeviceRow(
    device: MidiDeviceOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Usb,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else Paper,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(stringResource(R.string.settings_section_input))
                        if (device.canSend) append(stringResource(R.string.midi_output_suffix))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Mist,
                )
            }
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.midi_connected),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
