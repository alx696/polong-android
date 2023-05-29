#picasso
-dontwarn com.squareup.okhttp.**

#joda time
-dontwarn org.joda.convert.**
-dontwarn org.joda.time.**
-keep class org.joda.time.** { *; }
-keep interface org.joda.time.** { *; }

# GRPC
-dontwarn com.google.common.**
-dontwarn okio.**
-dontwarn org.mockito.**
-dontwarn sun.reflect.**
-dontwarn sun.misc.**
-keep class io.grpc.** { *; }
-keep class com.squareup.** {*;}

# COS
-keep class com.tencent.cos.** {*;}