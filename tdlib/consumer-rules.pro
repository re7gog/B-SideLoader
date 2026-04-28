-keep class org.drinkless.td.libcore.telegram.TdApi { *; }
-keep class org.drinkless.td.libcore.telegram.TdApi$* { *; }
-keep class org.drinkless.td.libcore.telegram.NativeClient
-keepclassmembers class org.drinkless.td.libcore.telegram.NativeClient { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class org.drinkless.tdlib.* { *; }
