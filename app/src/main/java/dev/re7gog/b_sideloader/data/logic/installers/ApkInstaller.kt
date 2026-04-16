package dev.re7gog.b_sideloader.data.logic.installers

import kotlinx.coroutines.flow.FlowCollector
import java.io.InputStream

interface ApkInstaller {
    suspend fun installApk(stream: InputStream, lengthBytes: Long, progressCollector: FlowCollector<Float>)
}