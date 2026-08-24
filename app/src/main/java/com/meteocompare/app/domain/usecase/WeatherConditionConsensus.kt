package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel

/**
 * Moteur de consensus hiérarchique des conditions météorologiques.
 *
 * Les codes WMO sont des catégories discrètes : un vote plat peut fragmenter
 * une majorité météorologiquement cohérente entre plusieurs sous-types proches
 * (`CLEAR`/`MAINLY_CLEAR`/`PARTLY_CLOUDY`/`OVERCAST`, ou
 * `DRIZZLE`/`RAIN_SHOWERS`/`RAIN`). Ce moteur vote donc du général vers le
 * particulier, avec le même équilibrage par lignée numérique que le consensus
 * V3 des autres variables.
 *
 * Hiérarchie :
 *
 * ROOT
 * ├─ NON_PRECIPITATION
 * │  ├─ SKY -> CLEAR / MAINLY_CLEAR / PARTLY_CLOUDY / OVERCAST
 * │  └─ FOG
 * └─ PRECIPITATION
 *    ├─ LIQUID -> DRIZZLE / RAIN_SHOWERS / RAIN
 *    ├─ FROZEN -> SNOW_SHOWERS / SNOW
 *    ├─ FREEZING_RAIN
 *    └─ THUNDERSTORM
 *
 * La branche SKY est la seule feuille intermédiaire résolue par une variable
 * physique continue : la nébulosité centrale du moteur V3 sélectionné. Les
 * autres branches restent décidées à partir des codes WMO des modèles.
 *
 * Important : le [ForecastConsensus.Vote.percent] retourné reste toujours le
 * pourcentage d'accord sur la feuille WMO brute exacte. La consolidation
 * hiérarchique ne peut donc jamais embellir la convergence affichée.
 */
object WeatherConditionConsensus {

    /**
     * Résout la condition centrale avec équilibrage par lignée à chaque niveau.
     *
     * @param entries conditions WMO (ou conditions inférées modèle par modèle)
     * @param cloudCoverPercent nébulosité centrale V3, utilisée uniquement si
     * la branche SKY remporte le consensus
     * @param localWeights pondérations locales optionnelles, bornées et
     * rééquilibrées par [ForecastConsensus.familyBalancedWeights]
     */
    fun resolve(
        entries: List<ForecastConsensus.Entry<WeatherCondition>>,
        cloudCoverPercent: Double?,
        localWeights: Map<WeatherModel, Double> = emptyMap()
    ): ForecastConsensus.Vote<WeatherCondition> {
        val valid = entries.filter { it.value != WeatherCondition.UNKNOWN }
        val rawVote = ForecastConsensus.vote(
            entries = valid,
            localWeights = localWeights,
            severity = WeatherCondition::severityRank
        )
        val cloudCondition = cloudCoverPercent
            ?.takeIf { it.isFinite() && it in 0.0..100.0 }
            ?.let(WeatherCondition::fromCloudCover)

        if (valid.isEmpty()) return rawVote.copy(value = cloudCondition)

        val display = resolveNode(
            node = HIERARCHY,
            entries = valid,
            cloudCondition = cloudCondition,
            localWeights = localWeights
        ) ?: rawVote.value ?: cloudCondition

        return rawVote.copy(value = display)
    }

    /**
     * Variante destinée aux helpers/tests qui ne disposent pas d'identifiants
     * de modèles. La même hiérarchie est descendue avec une voix par valeur.
     */
    internal fun resolveUnweighted(
        values: List<WeatherCondition>,
        cloudCoverPercent: Double? = null
    ): WeatherCondition? {
        val valid = values.filter { it != WeatherCondition.UNKNOWN }
        val cloudCondition = cloudCoverPercent
            ?.takeIf { it.isFinite() && it in 0.0..100.0 }
            ?.let(WeatherCondition::fromCloudCover)
        if (valid.isEmpty()) return cloudCondition
        return resolveNodeUnweighted(HIERARCHY, valid, cloudCondition)
    }

    private enum class NodeId {
        ROOT,
        NON_PRECIPITATION,
        SKY,
        CLEAR,
        MAINLY_CLEAR,
        PARTLY_CLOUDY,
        OVERCAST,
        FOG,
        PRECIPITATION,
        LIQUID,
        DRIZZLE,
        RAIN_SHOWERS,
        RAIN,
        FROZEN,
        SNOW_SHOWERS,
        SNOW,
        FREEZING_RAIN,
        THUNDERSTORM
    }

    private data class Node(
        val id: NodeId,
        val conditions: Set<WeatherCondition>,
        val children: List<Node> = emptyList(),
        val resolveSkyFromCloudCover: Boolean = false
    ) {
        val severityRank: Int = conditions.maxOfOrNull(WeatherCondition::severityRank) ?: -1
    }

    private fun leaf(id: NodeId, condition: WeatherCondition): Node =
        Node(id = id, conditions = setOf(condition))

    private val SKY_CONDITIONS = setOf(
        WeatherCondition.CLEAR,
        WeatherCondition.MAINLY_CLEAR,
        WeatherCondition.PARTLY_CLOUDY,
        WeatherCondition.OVERCAST
    )

    private val LIQUID_PRECIPITATION_CONDITIONS = setOf(
        WeatherCondition.DRIZZLE,
        WeatherCondition.RAIN_SHOWERS,
        WeatherCondition.RAIN
    )

    private val FROZEN_PRECIPITATION_CONDITIONS = setOf(
        WeatherCondition.SNOW_SHOWERS,
        WeatherCondition.SNOW
    )

    private val PRECIPITATION_CONDITIONS =
        LIQUID_PRECIPITATION_CONDITIONS +
            FROZEN_PRECIPITATION_CONDITIONS +
            WeatherCondition.FREEZING_RAIN +
            WeatherCondition.THUNDERSTORM

    private val NON_PRECIPITATION_CONDITIONS = SKY_CONDITIONS + WeatherCondition.FOG

    private val HIERARCHY = Node(
        id = NodeId.ROOT,
        conditions = NON_PRECIPITATION_CONDITIONS + PRECIPITATION_CONDITIONS,
        children = listOf(
            Node(
                id = NodeId.NON_PRECIPITATION,
                conditions = NON_PRECIPITATION_CONDITIONS,
                children = listOf(
                    Node(
                        id = NodeId.SKY,
                        conditions = SKY_CONDITIONS,
                        children = listOf(
                            leaf(NodeId.CLEAR, WeatherCondition.CLEAR),
                            leaf(NodeId.MAINLY_CLEAR, WeatherCondition.MAINLY_CLEAR),
                            leaf(NodeId.PARTLY_CLOUDY, WeatherCondition.PARTLY_CLOUDY),
                            leaf(NodeId.OVERCAST, WeatherCondition.OVERCAST)
                        ),
                        resolveSkyFromCloudCover = true
                    ),
                    leaf(NodeId.FOG, WeatherCondition.FOG)
                )
            ),
            Node(
                id = NodeId.PRECIPITATION,
                conditions = PRECIPITATION_CONDITIONS,
                children = listOf(
                    Node(
                        id = NodeId.LIQUID,
                        conditions = LIQUID_PRECIPITATION_CONDITIONS,
                        children = listOf(
                            leaf(NodeId.DRIZZLE, WeatherCondition.DRIZZLE),
                            leaf(NodeId.RAIN_SHOWERS, WeatherCondition.RAIN_SHOWERS),
                            leaf(NodeId.RAIN, WeatherCondition.RAIN)
                        )
                    ),
                    Node(
                        id = NodeId.FROZEN,
                        conditions = FROZEN_PRECIPITATION_CONDITIONS,
                        children = listOf(
                            leaf(NodeId.SNOW_SHOWERS, WeatherCondition.SNOW_SHOWERS),
                            leaf(NodeId.SNOW, WeatherCondition.SNOW)
                        )
                    ),
                    leaf(NodeId.FREEZING_RAIN, WeatherCondition.FREEZING_RAIN),
                    leaf(NodeId.THUNDERSTORM, WeatherCondition.THUNDERSTORM)
                )
            )
        )
    ).also(::validateHierarchy)

    /**
     * Garde structurelle : toute nouvelle valeur de WeatherCondition doit être
     * explicitement positionnée dans l'arbre, sans chevauchement ni trou.
     */
    private fun validateHierarchy(node: Node) {
        require(node.conditions.isNotEmpty()) { "Condition hierarchy node ${node.id} is empty" }
        if (node.id == NodeId.ROOT) {
            val expected = WeatherCondition.entries.filter { it != WeatherCondition.UNKNOWN }.toSet()
            require(node.conditions == expected) {
                "Condition hierarchy does not cover every known condition"
            }
        }
        if (node.children.isEmpty()) {
            require(node.conditions.size == 1) {
                "Condition leaf ${node.id} must contain exactly one condition"
            }
            return
        }
        require(node.children.map(Node::id).distinct().size == node.children.size) {
            "Condition hierarchy node ${node.id} contains duplicate child ids"
        }
        val flattened = node.children.flatMap(Node::conditions)
        require(flattened.toSet().size == flattened.size) {
            "Condition hierarchy node ${node.id} contains overlapping child conditions"
        }
        require(flattened.toSet() == node.conditions) {
            "Condition hierarchy node ${node.id} children do not exactly cover the parent"
        }
        node.children.forEach(::validateHierarchy)
    }

    private fun resolveNode(
        node: Node,
        entries: List<ForecastConsensus.Entry<WeatherCondition>>,
        cloudCondition: WeatherCondition?,
        localWeights: Map<WeatherModel, Double>
    ): WeatherCondition? {
        if (entries.isEmpty()) return null
        if (node.resolveSkyFromCloudCover && cloudCondition != null && cloudCondition in node.conditions) {
            return cloudCondition
        }
        if (node.children.isEmpty()) return node.conditions.singleOrNull()

        val childById = node.children.associateBy(Node::id)
        val childEntries = entries.mapNotNull { row ->
            val child = node.children.firstOrNull { row.value in it.conditions } ?: return@mapNotNull null
            ForecastConsensus.Entry(row.model, child.id)
        }
        val selectedId = ForecastConsensus.vote(
            entries = childEntries,
            localWeights = localWeights,
            severity = { id -> childById[id]?.severityRank ?: -1 }
        ).value ?: return null
        val selected = childById[selectedId] ?: return null

        return resolveNode(
            node = selected,
            entries = entries.filter { it.value in selected.conditions },
            cloudCondition = cloudCondition,
            localWeights = localWeights
        )
    }

    private fun resolveNodeUnweighted(
        node: Node,
        values: List<WeatherCondition>,
        cloudCondition: WeatherCondition?
    ): WeatherCondition? {
        if (values.isEmpty()) return null
        if (node.resolveSkyFromCloudCover && cloudCondition != null && cloudCondition in node.conditions) {
            return cloudCondition
        }
        if (node.children.isEmpty()) return node.conditions.singleOrNull()

        val counts = node.children.associateWith { child ->
            values.count { it in child.conditions }
        }
        val top = counts.values.maxOrNull() ?: return null
        val selected = counts
            .filterValues { it == top && it > 0 }
            .keys
            .maxByOrNull(Node::severityRank)
            ?: return null

        return resolveNodeUnweighted(
            node = selected,
            values = values.filter { it in selected.conditions },
            cloudCondition = cloudCondition
        )
    }
}
