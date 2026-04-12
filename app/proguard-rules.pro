-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class com.kunalkcube.zipfont.** { *; }
-keep class com.kunalkcube.zipfont.processor.** { *; }
-keep class com.kunalkcube.zipfont.viewmodel.** { *; }

-keepclassmembers class * {
    public <init>(android.content.Context);
}

-keep class com.android.apksig.** { *; }
-keep class net.lingala.zip4j.** { *; }

-dontwarn kotlinx.coroutines.**
-dontwarn com.android.apksig.**
