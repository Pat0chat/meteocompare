package com.meteocompare.app.domain.util

import java.text.Normalizer

/** Résout un code département français depuis admin2 et/ou le code postal. */
object FranceDepartmentResolver {

    private val departmentByName = mapOf(
        "ain" to "01", "aisne" to "02", "allier" to "03",
        "alpes de haute provence" to "04", "hautes alpes" to "05", "alpes maritimes" to "06",
        "ardeche" to "07", "ardennes" to "08", "ariege" to "09", "aube" to "10",
        "aude" to "11", "aveyron" to "12", "bouches du rhone" to "13", "calvados" to "14",
        "cantal" to "15", "charente" to "16", "charente maritime" to "17", "cher" to "18",
        "correze" to "19", "corse du sud" to "2A", "haute corse" to "2B", "cote d or" to "21",
        "cotes d armor" to "22", "creuse" to "23", "dordogne" to "24", "doubs" to "25",
        "drome" to "26", "eure" to "27", "eure et loir" to "28", "finistere" to "29",
        "gard" to "30", "haute garonne" to "31", "gers" to "32", "gironde" to "33",
        "herault" to "34", "ille et vilaine" to "35", "indre" to "36", "indre et loire" to "37",
        "isere" to "38", "jura" to "39", "landes" to "40", "loir et cher" to "41",
        "loire" to "42", "haute loire" to "43", "loire atlantique" to "44", "loiret" to "45",
        "lot" to "46", "lot et garonne" to "47", "lozere" to "48", "maine et loire" to "49",
        "manche" to "50", "marne" to "51", "haute marne" to "52", "mayenne" to "53",
        "meurthe et moselle" to "54", "meuse" to "55", "morbihan" to "56", "moselle" to "57",
        "nievre" to "58", "nord" to "59", "oise" to "60", "orne" to "61", "pas de calais" to "62",
        "puy de dome" to "63", "pyrenees atlantiques" to "64", "hautes pyrenees" to "65",
        "pyrenees orientales" to "66", "bas rhin" to "67", "haut rhin" to "68", "rhone" to "69",
        "haute saone" to "70", "saone et loire" to "71", "sarthe" to "72", "savoie" to "73",
        "haute savoie" to "74", "paris" to "75", "seine maritime" to "76", "seine et marne" to "77",
        "yvelines" to "78", "deux sevres" to "79", "somme" to "80", "tarn" to "81",
        "tarn et garonne" to "82", "var" to "83", "vaucluse" to "84", "vendee" to "85",
        "vienne" to "86", "haute vienne" to "87", "vosges" to "88", "yonne" to "89",
        "territoire de belfort" to "90", "essonne" to "91", "hauts de seine" to "92",
        "seine saint denis" to "93", "val de marne" to "94", "val d oise" to "95",
        "guadeloupe" to "971", "martinique" to "972", "guyane" to "973",
        "la reunion" to "974", "reunion" to "974", "mayotte" to "976"
    )

    fun resolve(admin2: String?, postcodes: List<String>): String? {
        admin2?.let { departmentByName[normalize(it)] }?.let { return it }
        return postcodes.asSequence().mapNotNull(::fromPostcode).firstOrNull()
    }

    internal fun fromPostcode(raw: String): String? {
        val postcode = raw.filter(Char::isDigit)
        if (postcode.length != 5) return null
        if (postcode.startsWith("20")) return null // Corse : admin2 est nécessaire pour 2A/2B.
        if (postcode.startsWith("97")) {
            return postcode.take(3).takeIf { it in setOf("971", "972", "973", "974", "976") }
        }
        val code = postcode.take(2)
        val numeric = code.toIntOrNull() ?: return null
        return code.takeIf { numeric in 1..95 && code != "20" }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}
