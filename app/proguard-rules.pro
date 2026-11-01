# kotlinx.serialization ships its own rules; keep our serializable models' companions just in case.
-keepclassmembers @kotlinx.serialization.Serializable class de.felitendo.kontor.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.slf4j.**

# Navigation resolves enum route arguments by class name.
-keepnames class de.felitendo.kontor.data.model.AssetKind
