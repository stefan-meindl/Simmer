# Kotlin multifile facades (SequencesKt, CollectionsKt, StringsKt, ...) are a class hierarchy of
# SomeKt___Part extends SomeKt__Part. R8 repackaged the child out of kotlin.sequences while the
# parent stayed package-private, crashing at startup with IllegalAccessError
# (kotlin.sequences.d extended by yz2). Keep their names so the hierarchy stays in one package.
-keepnames class kotlin.**Kt**

# Listen: the JNI function names of whisper.cpp are bound to this class and its method names.
-keep class com.simmer.app.listen.WhisperLib { native <methods>; }
