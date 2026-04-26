-keep class org.drinkless.td.libcore.telegram.TdApi { *; }
-keep class org.drinkless.td.libcore.telegram.TdApi$* { *; }
-keep class org.drinkless.td.libcore.telegram.NativeClient
-keepclassmembers class org.drinkless.td.libcore.telegram.NativeClient { *; }
-keep class org.drinkless.tdlib.Secrets { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}