import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

// #7 - empty window only. No scanning, no launching, no styling: those are
// #8, #9, #10 and #11.
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "HypdroidDesktop") {
    }
}
