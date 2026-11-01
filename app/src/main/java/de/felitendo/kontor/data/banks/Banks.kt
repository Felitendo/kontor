package de.felitendo.kontor.data.banks

import androidx.annotation.DrawableRes
import de.felitendo.kontor.R
import de.felitendo.kontor.data.model.BankRef

enum class Role { BANK, BROKER, PENSION }

data class Bank(
    val id: String,
    val name: String,
    @DrawableRes val logo: Int?,
    val roles: Set<Role>,
    /** Background for the monogram when there is no logo. */
    val color: Long = 0xFF2A2A2A,
    val monogram: String = name.take(2),
    val aliases: List<String> = emptyList(),
    /** Groups of local banks: any typed name containing one of these words belongs here. */
    val family: List<String> = emptyList(),
) {
    fun ref() = BankRef(id, name)
}

private val B = setOf(Role.BANK)
private val BB = setOf(Role.BANK, Role.BROKER)
private val BBP = setOf(Role.BANK, Role.BROKER, Role.PENSION)
private val P = setOf(Role.PENSION)
private val BP = setOf(Role.BROKER, Role.PENSION)

/** Logos are trademarks of their respective owners and only used to identify the institution. */
object Banks {
    val all: List<Bank> = listOf(
        Bank("sparkasse", "Sparkasse", R.drawable.bank_sparkasse, BBP, aliases = listOf("Kreissparkasse", "Stadtsparkasse", "KSK", "Haspa"), family = listOf("sparkasse")),
        Bank("volksbank", "Volksbank Raiffeisenbank", R.drawable.bank_volksbank, BBP, aliases = listOf("VR Bank", "Raiffeisen", "Volksbank"), family = listOf("volksbank", "raiffeisen", "vr bank", "vr-bank")),
        Bank("ing", "ING", R.drawable.bank_ing, BB, aliases = listOf("ING-DiBa", "Diba")),
        Bank("traderepublic", "Trade Republic", R.drawable.bank_traderepublic, BB),
        Bank("dkb", "DKB", R.drawable.bank_dkb, BB, aliases = listOf("Deutsche Kreditbank")),
        Bank("comdirect", "comdirect", R.drawable.bank_comdirect, BB),
        Bank("deutschebank", "Deutsche Bank", R.drawable.bank_deutschebank, BBP, aliases = listOf("maxblue")),
        Bank("commerzbank", "Commerzbank", R.drawable.bank_commerzbank, BBP),
        Bank("postbank", "Postbank", R.drawable.bank_postbank, BBP),
        Bank("consorsbank", "Consorsbank", R.drawable.bank_consorsbank, BB, aliases = listOf("Cortal Consors", "BNP")),
        Bank("scalable", "Scalable Capital", R.drawable.bank_scalable, BB),
        Bank("hvb", "HypoVereinsbank", R.drawable.bank_hvb, BB, aliases = listOf("HVB", "UniCredit")),
        Bank("targobank", "Targobank", R.drawable.bank_targobank, BB),
        Bank("santander", "Santander", R.drawable.bank_santander, BB),
        Bank("norisbank", "norisbank", R.drawable.bank_norisbank, BB),
        Bank("direkt1822", "1822direkt", R.drawable.bank_direkt1822, BB, aliases = listOf("Frankfurter Sparkasse")),
        Bank("c24", "C24 Bank", R.drawable.bank_c24, B, aliases = listOf("Check24")),
        Bank("chase", "Chase", R.drawable.bank_chase, B, aliases = listOf("Chase Bank", "J.P. Morgan", "JPMorgan")),
        Bank("n26", "N26", R.drawable.bank_n26, BB),
        Bank("sparda", "Sparda-Bank", R.drawable.bank_sparda, B, family = listOf("sparda")),
        Bank("psd", "PSD Bank", R.drawable.bank_psd, B, family = listOf("psd bank", "psd-bank")),
        Bank("olb", "Oldenburgische Landesbank", R.drawable.bank_olb, BB, aliases = listOf("OLB")),
        Bank("openbank", "Openbank", R.drawable.bank_openbank, B),
        Bank("raisin", "WeltSparen (Raisin)", null, B, color = 0xFF1E73BE, monogram = "WS", aliases = listOf("Raisin", "Zinspilot")),
        Bank("renault", "Renault Bank direkt", R.drawable.bank_renault, B),
        Bank("vwbank", "Volkswagen Bank", R.drawable.bank_vwbank, B, aliases = listOf("VW Bank", "VW FS")),
        Bank("bmwbank", "BMW Bank", R.drawable.bank_bmwbank, B),
        Bank("mercedes", "Mercedes-Benz Bank", R.drawable.bank_mercedes, B),
        Bank("bankofscotland", "Bank of Scotland", R.drawable.bank_bankofscotland, B),
        Bank("hanseatic", "Hanseatic Bank", R.drawable.bank_hanseatic, B),
        Bank("barclays", "Barclays", R.drawable.bank_barclays, B),
        Bank("klarna", "Klarna", R.drawable.bank_klarna, B),
        Bank("revolut", "Revolut", R.drawable.bank_revolut, BB),
        Bank("bunq", "bunq", R.drawable.bank_bunq, B),
        Bank("flatex", "flatex", R.drawable.bank_flatex, BB),
        Bank("smartbroker", "Smartbroker+", R.drawable.bank_smartbroker, setOf(Role.BROKER)),
        Bank("deka", "Deka", R.drawable.bank_deka, BP, aliases = listOf("DekaBank")),
        Bank("unioninvestment", "Union Investment", R.drawable.bank_unioninvestment, BP, aliases = listOf("UniProfiRente")),
        Bank("dws", "DWS", null, BP, color = 0xFF0A1E3C, monogram = "DWS", aliases = listOf("TopRente")),
        Bank("allianz", "Allianz", R.drawable.bank_allianz, P),
        Bank("ruv", "R+V", R.drawable.bank_ruv, P, aliases = listOf("RV")),
        Bank("debeka", "Debeka", R.drawable.bank_debeka, P),
        Bank("ergo", "ERGO", R.drawable.bank_ergo, P),
        Bank("generali", "Generali", R.drawable.bank_generali, P),
        Bank("axa", "AXA", R.drawable.bank_axa, P),
        Bank("huk", "HUK-Coburg", R.drawable.bank_huk, P),
        Bank("schwaebischhall", "Schwäbisch Hall", null, P, color = 0xFFFFD500, monogram = "SH", aliases = listOf("Bausparkasse")),
        Bank("lbs", "LBS", R.drawable.bank_lbs, P, aliases = listOf("Bausparkasse")),
        Bank("wuestenrot", "Wüstenrot", R.drawable.bank_wuestenrot, P, aliases = listOf("Bausparkasse")),
        Bank("wuerttembergische", "Württembergische", R.drawable.bank_wuerttembergische, P),
        Bank("signaliduna", "Signal Iduna", R.drawable.bank_signaliduna, P),
        Bank("zurich", "Zurich", null, P, color = 0xFF2167AE, monogram = "Z"),
        Bank("provinzial", "Provinzial", R.drawable.bank_provinzial, P),
        Bank("alteleipziger", "Alte Leipziger", null, P, color = 0xFF003C78, monogram = "AL"),
        Bank("lv1871", "LV 1871", R.drawable.bank_lv1871, P),
        Bank("degussa", "Degussa", R.drawable.bank_degussa, emptySet()),
        Bank("proaurum", "pro aurum", R.drawable.bank_proaurum, emptySet()),
    )

    private val byId = all.associateBy { it.id }

    fun find(id: String?): Bank? = id?.let { byId[it] }

    fun forRole(role: Role) = all.filter { role in it.roles }

    fun search(query: String, role: Role?): List<Bank> {
        val q = query.trim().lowercase()
        val pool = role?.let { forRole(it) } ?: all
        if (q.isEmpty()) return pool
        return pool.filter { b ->
            b.name.lowercase().contains(q) || b.aliases.any { it.lowercase().contains(q) }
        }.sortedBy { if (it.name.lowercase().startsWith(q)) 0 else 1 }
    }

    /**
     * Matches a typed name against the catalog so custom entries still get a logo:
     * exact names and aliases, "<Bank> <Zusatz>" and local banks such as "Kreissparkasse Ludwigsburg".
     */
    fun match(name: String): Bank? {
        val q = name.trim().lowercase()
        if (q.length < 2) return null
        all.firstOrNull { b -> b.name.lowercase() == q || b.aliases.any { it.lowercase() == q } }?.let { return it }
        all.firstOrNull { b -> (listOf(b.name) + b.aliases).any { q.startsWith(it.lowercase() + " ") } }?.let { return it }
        return all.firstOrNull { b -> b.family.any { q.contains(it) } }
    }
}
