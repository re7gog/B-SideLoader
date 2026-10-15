package dev.re7gog.b_sideloader.ui.navigation

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the list-detail layout shares out its width. The widths are what is left beside a 96 dp
 * navigation rail on the windows named.
 */
class ListDetailWidthsTest {

    @Test
    fun `a medium window too narrow for both minimums shows one pane at a time`() {
        // A 650 dp window: two panes here would squeeze the details below a phone's width.
        assertFalse(ListDetailWidths.fitsTwoPanes(554.dp))
    }

    @Test
    fun `the two minimums side by side are just enough`() {
        val width = ListDetailWidths.LIST_MIN + ListDetailWidths.DIVIDER + ListDetailWidths.DETAIL_MIN

        assertTrue(ListDetailWidths.fitsTwoPanes(width))
        assertEquals(ListDetailWidths.LIST_MIN, ListDetailWidths.listPane(width))
    }

    @Test
    fun `when width is tight the list gives way and the details keep their minimum`() {
        // An unfolded fold held upright, about 750 dp.
        val width = 654.dp

        val list = ListDetailWidths.listPane(width)

        assertTrue(ListDetailWidths.fitsTwoPanes(width))
        assertEquals(ListDetailWidths.DETAIL_MIN, width - ListDetailWidths.DIVIDER - list)
        assertTrue(list < ListDetailWidths.LIST_MAX)
    }

    @Test
    fun `with width to spare the list stops at its maximum and the details take the rest`() {
        // A tablet in landscape.
        val width = 1184.dp

        val list = ListDetailWidths.listPane(width)

        assertEquals(ListDetailWidths.LIST_MAX, list)
        assertTrue(width - ListDetailWidths.DIVIDER - list > ListDetailWidths.DETAIL_MIN)
    }
}
