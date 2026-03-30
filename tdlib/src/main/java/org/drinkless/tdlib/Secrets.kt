package org.drinkless.tdlib

object Secrets {
    init {
        System.loadLibrary("native-lib")
    }
    external fun getApiId(): Int
    external fun getApiHash(): String
}