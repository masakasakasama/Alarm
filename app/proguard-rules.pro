# Room エンティティ・DAO はリフレクション経由で使われるので保持。
-keep class com.galaxyalarm.data.** { *; }

# WorkManager Worker はリフレクションでインスタンス化される。
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Kotlin enum の name()/values() を保持。
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public final java.lang.String name();
}

# デバッグ用スタックトレースを読める状態にする。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
