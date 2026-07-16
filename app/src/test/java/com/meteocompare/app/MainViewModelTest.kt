package com.meteocompare.app

import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val themes = MutableStateFlow(ThemePreference.SYSTEM)
    private val preferences: UserPreferencesRepository = mockk(relaxed = true) {
        every { observeThemePreference() } returns themes
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `theme preference follows repository updates eagerly`() = runTest(dispatcher) {
        val viewModel = MainViewModel(preferences)
        assertEquals(ThemePreference.SYSTEM, viewModel.themePreference.value)

        themes.value = ThemePreference.DARK
        assertEquals(ThemePreference.DARK, viewModel.themePreference.value)

        themes.value = ThemePreference.LIGHT
        assertEquals(ThemePreference.LIGHT, viewModel.themePreference.value)
    }
}
