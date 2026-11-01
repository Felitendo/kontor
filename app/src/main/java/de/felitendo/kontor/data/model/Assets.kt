package de.felitendo.kontor.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** All amounts are stored in EUR, rates in percent per year (e.g. 3.25 for 3,25 %). */
@Serializable
sealed interface Asset {
    val id: String
    val createdAt: Long
    val kind: AssetKind
}

enum class AssetKind(val title: String, val singular: String) {
    FIXED_DEPOSIT("Festgeld", "Festgeld"),
    SAVINGS("Tagesgeld", "Tagesgeld"),
    METAL("Edelmetalle", "Edelmetall"),
    DEPOT("Wertpapiere", "Depot"),
    PENSION("Riester", "Riester-Vertrag"),
    CASH("Bargeld", "Bargeld"),
    OTHER("Sonstiges", "Sonstiges"),
}

@Serializable
@SerialName("fixed_deposit")
data class FixedDeposit(
    override val id: String = newId(),
    override val createdAt: Long = System.currentTimeMillis(),
    val bank: BankRef,
    val amount: Double,
    val rate: Double,
    /** Epoch day the money was invested. */
    val startDay: Long,
    /** Term in months; 0 means unknown/open ended. */
    val termMonths: Int = 12,
) : Asset {
    override val kind get() = AssetKind.FIXED_DEPOSIT
}

@Serializable
@SerialName("savings")
data class SavingsAccount(
    override val id: String = newId(),
    override val createdAt: Long = System.currentTimeMillis(),
    val bank: BankRef,
    val balance: Double,
    val rate: Double,
    val monthlyDeposit: Double = 0.0,
    /** Epoch day the balance was last confirmed; later values are projected from here. */
    val balanceDay: Long,
) : Asset {
    override val kind get() = AssetKind.SAVINGS
}

enum class Metal(val title: String, val symbol: String, val yahooFuture: String) {
    GOLD("Gold", "XAU", "GC=F"),
    SILVER("Silber", "XAG", "SI=F"),
    PLATINUM("Platin", "XPT", "PL=F"),
}

@Serializable
@SerialName("metal")
data class MetalHolding(
    override val id: String = newId(),
    override val createdAt: Long = System.currentTimeMillis(),
    val metal: Metal = Metal.GOLD,
    /** Troy ounces. */
    val ounces: Double,
    /** What was paid in total, if known. */
    val purchasePrice: Double? = null,
    val label: String? = null,
) : Asset {
    override val kind get() = AssetKind.METAL
}

@Serializable
@SerialName("depot")
data class Depot(
    override val id: String = newId(),
    override val createdAt: Long = System.currentTimeMillis(),
    val name: String,
    val bank: BankRef? = null,
    val positions: List<Position> = emptyList(),
) : Asset {
    override val kind get() = AssetKind.DEPOT
}

@Serializable
data class Position(
    val id: String = newId(),
    val instrument: Instrument,
    val quantity: Double,
    /** Average buy price per unit in EUR, if known. */
    val buyPrice: Double? = null,
)

@Serializable
data class Instrument(
    val isin: String,
    val wkn: String? = null,
    val name: String,
    val type: InstrumentType = InstrumentType.STOCK,
    /** Yahoo symbol once resolved, e.g. "VGWL.DE". */
    val yahooSymbol: String? = null,
    /** Onvista identifiers as a fallback source. */
    val onvistaType: String? = null,
    val onvistaId: String? = null,
)

enum class InstrumentType(val title: String) {
    STOCK("Aktie"), ETF("ETF"), FUND("Fonds"), BOND("Anleihe"), CERTIFICATE("Zertifikat"), OTHER("Wertpapier");

    companion object {
        fun fromOnvista(entityType: String?, subType: String?): InstrumentType = when {
            subType.equals("ETF", true) -> ETF
            entityType == "STOCK" -> STOCK
            entityType == "FUND" -> FUND
            entityType == "BOND" -> BOND
            entityType == "CERTIFICATE" || entityType == "DERIVATIVE" -> CERTIFICATE
            else -> OTHER
        }
    }
}

@Serializable
@SerialName("pension")
data class PensionContract(
    override val id: String = newId(),
    override val createdAt: Long = System.currentTimeMillis(),
    val provider: BankRef,
    val balance: Double,
    val rate: Double? = null,
    val monthlyContribution: Double = 0.0,
    val balanceDay: Long,
) : Asset {
    override val kind get() = AssetKind.PENSION
}

@Serializable
@SerialName("cash")
data class CashHolding(
    override val id: String = newId(),
    override val createdAt: Long = System.currentTimeMillis(),
    val label: String = "Bargeld",
    val amount: Double,
) : Asset {
    override val kind get() = AssetKind.CASH
}

@Serializable
@SerialName("other")
data class OtherAsset(
    override val id: String = newId(),
    override val createdAt: Long = System.currentTimeMillis(),
    val label: String,
    val value: Double,
) : Asset {
    override val kind get() = AssetKind.OTHER
}

/** A bank or provider. [id] points into the bundled catalog; custom entries only carry a name. */
@Serializable
data class BankRef(val id: String? = null, val name: String)

fun newId(): String = UUID.randomUUID().toString()
