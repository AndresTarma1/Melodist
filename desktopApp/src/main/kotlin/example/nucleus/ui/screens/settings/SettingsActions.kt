package example.nucleus.ui.screens.settings

import example.nucleus.platform.NativeDesktop
import example.nucleus.viewmodels.AppViewModel
import java.net.URI
import java.net.URLEncoder

internal fun openReportBugPage() {
    val os = "${System.getProperty("os.name")} ${System.getProperty("os.version")}"
    val body = "**Versión:** ${AppViewModel.CURRENT_VERSION}\n**Sistema operativo:** $os\n\nDescribe el problema:\n"
    val url = "https://github.com/AndresTarma1/PaltaSound/issues/new" +
        "?title=${URLEncoder.encode("[Bug] ", "UTF-8")}" +
        "&body=${URLEncoder.encode(body, "UTF-8")}"
    NativeDesktop.browse(URI(url))
}
