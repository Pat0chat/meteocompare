"""Architectural guardrails: no forecast display may silently drop condition diagnoses.

These checks supplement, rather than replace, Android compilation/UI tests.
"""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASE = ROOT / "app/src/main/java/com/meteocompare/app"


def code(relative):
    return (BASE / relative).read_text(encoding="utf-8")


class ConditionWiringV4Contract(unittest.TestCase):
    def test_current_hour_comes_from_same_aggregate_as_first_forecast_slot(self):
        aggregate = code("domain/util/ForecastAggregates.kt")
        calculator = code("domain/usecase/ConfidenceCalculator.kt")
        self.assertRegex(aggregate, r"currentConditionDecision\([\s\S]*?nextHours\(forecast, now, true, engineContext, 1\)")
        self.assertIn(".currentConditionDecision(forecast, now, engineContext)", calculator)
        self.assertIn("ConditionDecisionEngine.hourly(series.hourly, index)", aggregate)
        self.assertNotIn("series.resolveHourlyCondition(index)", aggregate)

    def test_home_current_and_24h_strip_preserve_condition_diagnostics(self):
        vm = code("ui/citylist/CityListViewModel.kt")
        state = code("ui/citylist/CityListUiState.kt")
        screen = code("ui/citylist/CityListScreen.kt")
        strip = code("ui/citylist/MiniForecastStrip.kt")
        for token in ("currentConditionDecision", "next24hConditionDiagnostics"):
            self.assertIn(token, vm)
            self.assertIn(token, state)
            self.assertIn(token, screen)
        self.assertIn("conditionDiagnostics.getOrNull(index)", strip)
        self.assertIn("ConditionStatusIcon(", strip)
        self.assertIn("ConditionStatusLegend(", screen)

    def test_city_detail_current_uses_same_condition_and_decision(self):
        vm = code("ui/citydetail/CityDetailViewModel.kt")
        screen = code("ui/citydetail/CityDetailScreen.kt")
        state = code("ui/citydetail/CityDetailUiState.kt")
        self.assertIn("currentWeatherDecision(forecast, calculationNow, engineContext)", vm)
        self.assertIn("currentCondition = currentConditionDecision.condition", vm)
        self.assertIn("currentConditionDecision = currentConditionDecision", vm)
        self.assertIn("currentConditionDecision", state)
        self.assertIn("decision = currentConditionDecision", screen)
        self.assertIn("ConditionStatusIcon(", screen)

    def test_daily_timeline_and_engine_comparison_share_period_resolver(self):
        resolver = code("domain/util/ForecastConditionResolver.kt")
        timeline = code("ui/citydetail/SimplifiedTimelineBuilder.kt")
        comparison = code("domain/usecase/EngineComparisonBuilder.kt")
        ui = code("ui/enginecomparison/EngineComparisonScreen.kt")
        self.assertIn("ConditionDecisionEngine.period(series, date, zone, period, solar, nextSolar)", resolver)
        self.assertIn("WeatherConditionConsensus.resolveDecision(", resolver)
        self.assertGreaterEqual(timeline.count("ForecastConditionResolver.periodConsensus("), 2)
        self.assertIn("condition = fullDayDecision.condition", timeline)
        self.assertIn("conditionDiagnostic = fullDayDecision", timeline)
        self.assertIn("ForecastConditionResolver.periodConsensus(", comparison)
        self.assertIn("conditionDecision = conditionDecision", comparison)
        self.assertIn("ConditionStatusIcon(", ui)
        self.assertIn("ConditionStatusLegend(", ui)

    def test_hourly_daily_and_model_tables_render_diagnosed_icons(self):
        for relative in (
            "ui/citydetail/ChronoTimeline.kt", "ui/citydetail/SimplifiedTimeline.kt",
            "ui/citydetail/DayNightConditionIcons.kt", "ui/citydetail/WeatherByModelTable.kt",
            "ui/citydetail/HourlyWeatherByModelTable.kt"
        ):
            with self.subTest(relative=relative):
                self.assertIn("ConditionStatusIcon(", code(relative))
        chart = code("ui/graphicview/GraphicForecastScreen.kt")
        view_model = code("ui/graphicview/GraphicForecastViewModel.kt")
        self.assertIn("buildSimplifiedTimeline(", view_model)
        self.assertIn("ConditionStatusLegend(", chart)

    def test_widgets_preserve_diagnoses_in_all_display_modes(self):
        data = code("widget/WidgetData.kt")
        ui = code("widget/MeteoWidget.kt")
        value = code("widget/MeteoValueWidgets.kt")
        bitmap = code("widget/WidgetWeatherIconRenderer.kt")
        for token in ("currentConditionDecision", "next12hConditionDiagnostics", "conditionDecision"):
            self.assertIn(token, data)
        self.assertGreaterEqual(ui.count("decision = data.currentConditionDecision"), 4)
        self.assertEqual(ui.count("conditionDiagnostics = data.next12hConditionDiagnostics"), 3)
        self.assertIn("decision = data.currentConditionDecision", value)
        self.assertIn('ConditionStatus.AMBIGUOUS -> "≠"', bitmap)
        self.assertIn('ConditionStatus.UNAVAILABLE -> "—"', bitmap)
        for renderer in ("WidgetMiniForecastRenderer.kt", "WidgetHeatmapForecastRenderer.kt", "WidgetHeatmapTrendForecastRenderer.kt"):
            with self.subTest(renderer=renderer):
                self.assertIn("conditionDiagnostics.getOrNull(index)", code("widget/" + renderer))

    def test_notification_uses_shared_daily_comparison_and_weather_scenarios_use_model_engine(self):
        self.assertIn("EngineComparisonBuilder", code("domain/usecase/WeatherNotificationEvaluator.kt"))
        self.assertIn("conditionDecision = values.conditionDecision", code("domain/usecase/WeatherNotificationEvaluator.kt"))
        self.assertIn("val conditionDecision: ConditionDecision?", code("domain/model/WeatherNotification.kt"))
        notifier = code("notification/WeatherNotifier.kt")
        self.assertIn("RemoteIcon.Condition(notification.condition, notification.conditionDecision)", notifier)
        self.assertIn("decision = icon.decision", notifier)
        self.assertIn("statusLabelRes(status)", notifier)
        self.assertIn("series.resolveHourlyCondition(index)", code("domain/util/WeatherScenarioBuilder.kt"))
        self.assertIn("ConditionDecisionEngine.hourly(this, index).condition", code("domain/util/ForecastSeriesDaily.kt"))

    def test_diagnostics_have_one_explanation_and_visual_vocabulary(self):
        ui = code("ui/components/ConditionStatusIcon.kt")
        for status in ("RELIABLE", "ESTIMATED", "AMBIGUOUS", "INCOMPLETE", "UNAVAILABLE"):
            self.assertIn("ConditionStatus." + status, ui)
        self.assertIn("ConditionExplanationDialog(", ui)
        self.assertIn("ConditionStatusLegend(", ui)
        self.assertIn("semantics { contentDescription = talkback }", ui)


if __name__ == "__main__":
    unittest.main()
