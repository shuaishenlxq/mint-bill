# Room entities / DAOs
-keep class com.xl.bill.mint.data.db.** { *; }

# Services kept by the system binder (NotificationListenerService / AccessibilityService)
-keep class com.xl.bill.mint.service.** { *; }

# Keep WorkManager worker constructors
-keep class com.xl.bill.mint.receiver.** { *; }

# DataStore / kotlinx.serialization are fine with R8 defaults.

# SQLCipher (net.zetetic) — native lib + JNI binding must be kept
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
