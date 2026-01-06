# Add project specific ProGuard rules here.

# sshj
-keep class net.schmizz.sshj.** { *; }
-keep class com.hierynomus.** { *; }

# Bouncy Castle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# EdDSA
-keep class net.i2p.crypto.eddsa.** { *; }

# SLF4J
-dontwarn org.slf4j.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
