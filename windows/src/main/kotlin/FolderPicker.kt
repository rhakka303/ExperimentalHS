import java.awt.EventQueue
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

/**
 * #108 - a folder chooser for the Game Folder page. Compose Desktop has no
 * folder picker of its own, and java.awt.FileDialog cannot pick folders on
 * Windows, so this wraps Swing's JFileChooser in directories-only mode,
 * using the system look and feel so it reads as a normal Windows dialog.
 *
 * Blocks until the user picks or cancels. Callers must ignore input that
 * could fire meanwhile (the gamepad collector keeps running underneath a
 * modal dialog). Returns null on cancel.
 */
fun chooseFolder(startIn: File?, title: String): File? {
    var chosen: File? = null
    val show = {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (e: Exception) {
            // keep Swing's default look; the dialog still works
        }
        val chooser = JFileChooser(startIn?.takeIf { it.isDirectory }).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = title
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chosen = chooser.selectedFile
        }
    }
    if (EventQueue.isDispatchThread()) show() else EventQueue.invokeAndWait(show)
    return chosen
}
