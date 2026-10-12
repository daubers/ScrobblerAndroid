package dev.daubney.scrobbled

import dev.daubney.scrobbled.ui.Destination
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTest {
    @Test
    fun routesAreUnique() {
        val routes = Destination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }
}
