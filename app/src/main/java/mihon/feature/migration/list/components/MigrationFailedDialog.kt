package mihon.feature.migration.list.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Shown when a bulk migration run finished with failures. Dismissing keeps the user on the
 * migration list with the failed entries (their found matches preserved) so "Migrate" can be
 * pressed again to retry only those.
 */
@Composable
fun MigrationFailedDialog(
    failedCount: Int,
    totalCount: Int,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(MR.strings.migrationListScreen_failedDialog_title))
        },
        text = {
            Text(
                text = stringResource(
                    MR.strings.migrationListScreen_failedDialog_message,
                    failedCount,
                    totalCount,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}
