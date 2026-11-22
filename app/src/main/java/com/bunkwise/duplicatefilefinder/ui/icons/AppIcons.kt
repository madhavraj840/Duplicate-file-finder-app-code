package com.bunkwise.duplicatefilefinder.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Every icon the app uses, named by role rather than by Material glyph. One family (Rounded)
 * everywhere, so no screen mixes glyph styles.
 */
object AppIcons {
    val NavScan = Icons.Rounded.Search
    val NavDuplicates = Icons.Rounded.ContentCopy
    val NavSettings = Icons.Rounded.Settings

    val Back = Icons.AutoMirrored.Rounded.ArrowBack
    val ChevronRight = Icons.AutoMirrored.Rounded.KeyboardArrowRight
    val Close = Icons.Rounded.Close
    val Search = Icons.Rounded.Search
    val Sort = Icons.AutoMirrored.Rounded.Sort
    val Filter = Icons.Rounded.FilterList
    val Open = Icons.AutoMirrored.Rounded.OpenInNew
    val Undo = Icons.AutoMirrored.Rounded.Undo
    val Restore = Icons.Rounded.RestoreFromTrash
    val Delete = Icons.Rounded.DeleteOutline
    val Bin = Icons.Rounded.Delete
    val Archive = Icons.Rounded.Archive
    val Folder = Icons.Rounded.Folder
    val FolderOff = Icons.Rounded.FolderOff
    val Schedule = Icons.Rounded.Schedule
    val Theme = Icons.Rounded.Contrast
    val Language = Icons.Rounded.Language
    val Star = Icons.Rounded.Star
    val Info = Icons.Rounded.Info
    val Storage = Icons.Rounded.SdStorage
    val Lock = Icons.Rounded.Lock
    val Play = Icons.Rounded.PlayArrow
    val Unlimited = Icons.Rounded.AllInclusive
    val SelectAll = Icons.Rounded.DoneAll
    val Pro = Crown
    val Warning = Icons.Rounded.WarningAmber
    val Keep = Icons.Rounded.Verified

    val Image = Icons.Rounded.Image
    val Video = Icons.Rounded.Movie
    val Audio = Icons.Rounded.MusicNote
    val Document = Icons.Rounded.Description
    val OtherFile = Icons.AutoMirrored.Rounded.InsertDriveFile

    // Selection and status.
    val Check = Icons.Rounded.Check
    val Checked = Icons.Rounded.CheckBox
    val Unchecked = Icons.Rounded.CheckBoxOutlineBlank
    val Partial = Icons.Rounded.IndeterminateCheckBox
    val Selected = Icons.Rounded.RadioButtonChecked
    val Unselected = Icons.Rounded.RadioButtonUnchecked
    val StatusOk = Icons.Rounded.CheckCircle
    val StatusProblem = Icons.Rounded.Error
    val Expand = Icons.Rounded.ExpandMore
    val Collapse = Icons.Rounded.ExpandLess
}
/** Pro's mark: a crown (Material has none). Filled, tinted like any icon. */
private val Crown: ImageVector = ImageVector.Builder("Crown", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        // The body: three points with rounded valleys, a flat base.
        moveTo(2.6f, 8.2f)
        lineTo(7.4f, 12.2f)
        lineTo(11.1f, 6.3f)
        quadTo(12f, 5.1f, 12.9f, 6.3f)
        lineTo(16.6f, 12.2f)
        lineTo(21.4f, 8.2f)
        lineTo(19.6f, 16.6f)
        quadTo(19.4f, 17.4f, 18.6f, 17.4f)
        lineTo(5.4f, 17.4f)
        quadTo(4.6f, 17.4f, 4.4f, 16.6f)
        close()
        // The band.
        moveTo(5.6f, 18.8f)
        lineTo(18.4f, 18.8f)
        quadTo(19.2f, 18.8f, 19.2f, 19.6f)
        quadTo(19.2f, 20.4f, 18.4f, 20.4f)
        lineTo(5.6f, 20.4f)
        quadTo(4.8f, 20.4f, 4.8f, 19.6f)
        quadTo(4.8f, 18.8f, 5.6f, 18.8f)
        close()
        // A ball on each point.
        for ((cx, cy) in listOf(2.6f to 7.2f, 12f to 4.2f, 21.4f to 7.2f)) {
            moveTo(cx - 1.5f, cy)
            arcToRelative(1.5f, 1.5f, 0f, true, true, 3f, 0f)
            arcToRelative(1.5f, 1.5f, 0f, true, true, -3f, 0f)
            close()
        }
    }
}.build()
