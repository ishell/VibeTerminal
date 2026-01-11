# Add project specific ProGuard rules here.

# ============ Security: Strip debug logs in release builds ============
# Remove Log.d(), Log.v(), and Log.i() calls in release
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}

# ============ SSH Libraries ============

# sshj
-keep class net.schmizz.sshj.** { *; }
-keep class com.hierynomus.** { *; }

# Bouncy Castle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# EdDSA
-keep class net.i2p.crypto.eddsa.** { *; }
-dontwarn sun.security.x509.X509Key

# SLF4J
-dontwarn org.slf4j.**

# GSSAPI/Kerberos (not available on Android, used by sshj optionally)
-dontwarn javax.security.auth.login.**
-dontwarn org.ietf.jgss.**

# Google Error Prone annotations (compile-time only, used by Tink)
-dontwarn com.google.errorprone.annotations.**

# Google Crypto Tink
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# ============ Database ============

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
