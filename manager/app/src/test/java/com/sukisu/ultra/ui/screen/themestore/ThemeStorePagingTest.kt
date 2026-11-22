package com.sukisu.ultra.ui.screen.themestore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeStorePagingTest {
    @Test
    fun windowKeepsOnePageAtTheTop() {
        assertEquals(STORE_PAGE_SIZE, themeStorePageWindow(0, totalItems = 120))
        assertEquals(STORE_PAGE_SIZE, themeStorePageWindow(9, totalItems = 120))
    }

    @Test
    fun windowCoversRestoredPositionsPastTheFirstPage() {
        assertEquals(20, themeStorePageWindow(10, totalItems = 120))
        assertEquals(20, themeStorePageWindow(19, totalItems = 120))
        assertEquals(50, themeStorePageWindow(45, totalItems = 120))
    }

    @Test
    fun windowAlwaysReachesTheRestoredIndex() {
        for (index in 0..60 step 7) {
            val window = themeStorePageWindow(index, totalItems = 200)
            assertTrue("window $window must cover index $index", window > index)
        }
    }

    @Test
    fun windowNeverExceedsAvailableItems() {
        assertEquals(12, themeStorePageWindow(11, totalItems = 12))
        assertEquals(5, themeStorePageWindow(4, totalItems = 5, pageSize = 1))
    }

    @Test
    fun windowStaysUsableForEmptyOrShortCatalogues() {
        assertEquals(STORE_PAGE_SIZE, themeStorePageWindow(0, totalItems = 0))
        assertEquals(STORE_PAGE_SIZE, themeStorePageWindow(-3, totalItems = 3))
    }
}
