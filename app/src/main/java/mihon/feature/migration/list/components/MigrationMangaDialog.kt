package mihon.feature.migration.list.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun MigrationMangaDialog(
    onDismissRequest: () -> Unit,
    copy: Boolean,
    migratingCount: Int,
    nonExactCount: Int,
    skippedCount: Int,
    onMigrate: (includeNonExact: Boolean) -> Unit,
) {
    var includeNonExact by rememberSaveable { mutableStateOf(false) }

    val titleCount = migratingCount + if (includeNonExact) nonExactCount else 0
    val displayedSkipped = skippedCount + if (includeNonExact) 0 else nonExactCount

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = pluralStringResource(
                    resource = if (copy) {
                        MR.plurals.migrationListScreen_migrateDialog_copyTitle
                    } else {
                        MR.plurals.migrationListScreen_migrateDialog_migrateTitle
                    },
                    count = titleCount,
                    titleCount,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (displayedSkipped > 0) {
                    Text(
                        text = pluralStringResource(
                            resource = MR.plurals.migrationListScreen_migrateDialog_skipText,
                            count = displayedSkipped,
                            displayedSkipped,
                        ),
                    )
                }
                if (nonExactCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { includeNonExact = !includeNonExact },
                    ) {
                        Checkbox(
                            checked = includeNonExact,
                            onCheckedChange = { includeNonExact = it },
                        )
                        Text(
                            text = stringResource(
                                MR.strings.migrationListScreen_migrateDialog_includeNonExact,
                                nonExactCount,
                            ),
                        )
                    }
                    Text(
                        text = stringResource(MR.strings.migrationListScreen_migrateDialog_includeNonExactHint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onMigrate(includeNonExact) }) {
                Text(
                    text = stringResource(
                        resource = if (copy) {
                            MR.strings.migrationListScreen_migrateDialog_copyLabel
                        } else {
                            MR.strings.migrationListScreen_migrateDialog_migrateLabel
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.migrationListScreen_migrateDialog_cancelLabel))
            }
        },
    )
}
