package com.tooheavy.app.data

import java.util.Calendar
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// Adaptive nutrition coach. Estimates real energy expenditure from logged intake and the smoothed
// body-weight trend, then sets calorie and macro targets for the chosen rate of change at a weekly
// check-in. Everything here is a pure function of the logs, so it can be recomputed at any time.

/** Energy in 1 kg of body-weight change (mixed fat and lean tissue). */
const val KCAL_PER_KG = 7700.0

/** Days of intake and weight trend the expenditure estimate looks back over. */
const val EXPENDITURE_WINDOW = 21

private const val MIN_LOGGED_DAYS = 7
private const val MIN_WEIGH_INS = 3
/** Weigh-ins in the window for full confidence in the weight trend. */
private const val FULL_WEIGH_INS = 8

object DietStyle {
    const val BALANCED = "balanced"
    const val LOW_FAT = "low_fat"
    const val LOW_CARB = "low_carb"
    const val KETO = "keto"
    val all = listOf(BALANCED, LOW_FAT, LOW_CARB, KETO)
    fun label(style: String) = when (style) {
        LOW_FAT -> "Low fat"
        LOW_CARB -> "Low carb"
        KETO -> "Keto"
        else -> "Balanced"
    }
    fun hint(style: String) = when (style) {
        LOW_FAT -> "20 % fat, carbs fill the rest"
        LOW_CARB -> "20 % carbs, fat fills the rest"
        KETO -> "30 g carbs, fat fills the rest"
        else -> "30 % fat, carbs fill the rest"
    }
}

data class CoachSettings(
    /** When on, each weekly check-in replaces the daily goals with adaptive targets. */
    val enabled: Boolean = false,
    /** Target change per week as % of body weight (magnitude); the direction comes from BodyProfile.goal. */
    val weeklyRatePct: Double = 0.5,
    /** Protein in g per kg of body weight. */
    val proteinPerKg: Double = 2.0,
    val dietStyle: String = DietStyle.BALANCED,
    /** Weekday of the check-in as Calendar.DAY_OF_WEEK (Calendar.MONDAY = 2). */
    val checkInDay: Int = Calendar.MONDAY,
)

/** One weekly check-in: what the coach saw and the targets it set. */
data class CheckIn(
    val date: String = "",
    val trendKg: Double? = null,
    /** Trend change over the 7 days before the check-in. */
    val weeklyChangeKg: Double? = null,
    val expenditure: Int = 0,
    val goals: NutritionGoals = NutritionGoals(),
)

data class TrendPoint(val date: String, val scaleKg: Double, val trendKg: Double)

/**
 * Exponentially smoothed weight trend (oldest first). The daily smoothing factor is compounded over
 * gaps between weigh-ins, so a weigh-in after a week away moves the trend more than a daily one.
 */
fun weightTrend(weights: List<WeightEntry>, dailyAlpha: Double = 0.1): List<TrendPoint> {
    val out = ArrayList<TrendPoint>(weights.size)
    for (w in weights.sortedBy { it.date }) {
        val prev = out.lastOrNull()
        val trend = if (prev == null) w.kg else {
            val gap = daysBetween(prev.date, w.date).coerceAtLeast(1)
            val a = 1 - (1 - dailyAlpha).pow(gap)
            prev.trendKg + a * (w.kg - prev.trendKg)
        }
        out.add(TrendPoint(w.date, w.kg, trend))
    }
    return out
}

/** Trend on [date]: the last trend point on or before it, null if there is none. */
fun List<TrendPoint>.trendOn(date: String): Double? = lastOrNull { it.date <= date }?.trendKg

data class Expenditure(
    /** Estimate used for targets: the formula value moved toward the data by [confidence]. */
    val kcal: Int,
    /** Mifflin-St Jeor x activity, the starting point before there is data. */
    val formulaKcal: Int,
    /** Energy-balance estimate from intake and trend change; null until there is enough data. */
    val dataKcal: Int?,
    /** 0..1, how much [kcal] follows [dataKcal] rather than the formula. */
    val confidence: Double,
    val loggedDays: Int,
    val weighIns: Int,
    /** Days the estimate covers (at most EXPENDITURE_WINDOW). */
    val spanDays: Int,
)

/**
 * Daily energy expenditure as of the morning of [asOf], from the [EXPENDITURE_WINDOW] days before it.
 *
 * Intake is averaged over logged days only. Logging that is consistently incomplete still converges
 * on working targets, because the estimate is relative to what gets logged. The trend change over the
 * same days is converted to energy stored or burned off. Until there are about three weeks of logs and
 * regular weigh-ins the result is shrunk toward the formula estimate.
 */
fun estimateExpenditure(
    profile: BodyProfile,
    dailyKcal: Map<String, Double>,
    weights: List<WeightEntry>,
    asOf: String,
): Expenditure {
    val formula = profile.tdee()
    val trend = weightTrend(weights.filter { it.date <= asOf })
    val first = trend.firstOrNull()?.date
    val windowStart = shiftDate(asOf, -EXPENDITURE_WINDOW)
    val start = if (first != null && first > windowStart) first else windowStart
    val span = if (first == null) 0 else daysBetween(start, asOf)
    // Morning weight on day d reflects intake up to d - 1, so intake runs from start to asOf - 1.
    val logged = (0 until span).mapNotNull { dailyKcal[shiftDate(start, it)]?.takeIf { k -> k > 0 } }
    val weighIns = trend.count { it.date >= start }
    val startTrend = trend.trendOn(start)
    val endTrend = trend.trendOn(asOf)
    if (logged.size < MIN_LOGGED_DAYS || weighIns < MIN_WEIGH_INS || startTrend == null || endTrend == null) {
        return Expenditure(formula, formula, null, 0.0, logged.size, weighIns, span)
    }
    val bmr = profile.bmr()
    val raw = (logged.average() - (endTrend - startTrend) * KCAL_PER_KG / span).coerceIn(bmr, bmr * 2.5)
    val confidence = min(1.0, logged.size / EXPENDITURE_WINDOW.toDouble()) * min(1.0, weighIns / FULL_WEIGH_INS.toDouble())
    val blended = formula + confidence * (raw - formula)
    return Expenditure(blended.roundToInt(), formula, raw.roundToInt(), confidence, logged.size, weighIns, span)
}

/** Signed target change in kg per week for the profile's goal. */
fun CoachSettings.weeklyChangeKg(profile: BodyProfile, weightKg: Double): Double = when (profile.goal) {
    "lose" -> -weeklyRatePct / 100 * weightKg
    "gain" -> weeklyRatePct / 100 * weightKg
    else -> 0.0
}

/** Calories for the target rate of change on top of [expenditure], split into macros by the diet style. */
fun coachGoals(expenditure: Int, profile: BodyProfile, settings: CoachSettings, weightKg: Double): NutritionGoals {
    val floor = if (profile.sex == "female") 1200 else 1500
    val kcal = (expenditure + settings.weeklyChangeKg(profile, weightKg) * KCAL_PER_KG / 7).roundToInt().coerceAtLeast(floor)
    // Protein follows body weight, capped at the weight for BMI 27 so it doesn't balloon at high body fat
    // and at 40 % of calories so very low targets keep room for carbs and fat.
    val heightM = profile.heightCm / 100
    val proteinBasis = min(weightKg, 27 * heightM * heightM)
    val protein = min(settings.proteinPerKg * proteinBasis, kcal * 0.4 / 4).roundToInt()
    val remaining = kcal - protein * 4
    val fatFloor = 0.5 * weightKg
    val fat: Int
    val carbs: Int
    when (settings.dietStyle) {
        DietStyle.LOW_CARB, DietStyle.KETO -> {
            carbs = if (settings.dietStyle == DietStyle.KETO) 30 else (kcal * 0.2 / 4).roundToInt()
            fat = ((remaining - carbs * 4) / 9.0).roundToInt()
        }
        else -> {
            val share = if (settings.dietStyle == DietStyle.LOW_FAT) 0.2 else 0.3
            fat = max(fatFloor, kcal * share / 9).roundToInt()
            carbs = ((remaining - fat * 9) / 4.0).roundToInt()
        }
    }
    return NutritionGoals(kcal, protein, carbs.coerceAtLeast(0), fat.coerceAtLeast(0))
}

/** Most recent date on or before [date] that falls on [weekday] (Calendar.DAY_OF_WEEK). */
fun lastWeekdayOnOrBefore(date: String, weekday: Int): String =
    shiftDate(date, -((dayOfWeek(date) - weekday + 7) % 7))

/** A check-in is due once the scheduled weekday has passed since the last one (missed weeks stay due). */
fun isCheckInDue(settings: CoachSettings, lastCheckIn: String?, today: String): Boolean =
    settings.enabled && (lastCheckIn == null || lastCheckIn < lastWeekdayOnOrBefore(today, settings.checkInDay))

/** Date of the next check-in: today if one is due, otherwise the next scheduled weekday. */
fun nextCheckInDate(settings: CoachSettings, lastCheckIn: String?, today: String): String =
    if (isCheckInDue(settings, lastCheckIn, today)) today
    else shiftDate(lastWeekdayOnOrBefore(today, settings.checkInDay), 7)

/** What a check-in on [date] would set, before the user applies it. */
data class CheckInProposal(
    val date: String,
    val expenditure: Expenditure,
    val trendKg: Double?,
    val weeklyChangeKg: Double?,
    val goals: NutritionGoals,
) {
    fun toCheckIn() = CheckIn(date, trendKg, weeklyChangeKg, expenditure.kcal, goals)
}

fun buildCheckIn(
    profile: BodyProfile,
    settings: CoachSettings,
    dailyKcal: Map<String, Double>,
    weights: List<WeightEntry>,
    date: String,
): CheckInProposal {
    val expenditure = estimateExpenditure(profile, dailyKcal, weights, date)
    val trend = weightTrend(weights.filter { it.date <= date })
    val now = trend.trendOn(date)
    val weekAgo = trend.trendOn(shiftDate(date, -7))
    val goals = coachGoals(expenditure.kcal, profile, settings, now ?: profile.weightKg)
    return CheckInProposal(date, expenditure, now, if (now != null && weekAgo != null) now - weekAgo else null, goals)
}
