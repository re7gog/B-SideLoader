package dev.re7gog.b_sideloader.data.logic.installers

import kotlinx.coroutines.flow.FlowCollector
import okhttp3.ResponseBody

interface ApkInstaller {
    suspend fun installApkFromDownload(download: ResponseBody, progressCollector: FlowCollector<Int>)
}