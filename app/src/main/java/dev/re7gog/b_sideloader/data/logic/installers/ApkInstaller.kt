package dev.re7gog.b_sideloader.data.logic.installers

import okhttp3.ResponseBody

interface ApkInstaller {
    fun installApkFromDownload(download: ResponseBody, onProgress: (Float) -> Unit)
}