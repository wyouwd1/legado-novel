package io.legado.app.constant

@Suppress("ConstPropertyName")
object IntentAction {
    const val start = "start"
    const val play = "play"
    const val stop = "stop"
    const val resume = "resume"
    /** 仅清除全局暂停并继续调度，不解冻各书已单章暂停的章节 */
    const val continueDownload = "continueDownload"
    const val pause = "pause"
    const val remove = "remove"
}
