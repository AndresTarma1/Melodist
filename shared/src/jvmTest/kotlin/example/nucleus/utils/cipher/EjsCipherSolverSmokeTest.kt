package example.nucleus.utils.cipher

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class EjsCipherSolverSmokeTest {
    @Test
    fun `EJS prepare y solve con player js real`() = runBlocking {
        val (playerJs, hash) = PlayerJsFetcher.getPlayerJs()
            ?: error("[ejs] player.js no disponible")
        println("[ejs] playerJs hash=$hash len=${playerJs.length}")

        // prepare (preprocesa el player.js con meriyah/astring/yt.solver.core)
        EjsCipherSolver.prepare(playerJs, hash)
        println("[ejs] prepare OK para hash=$hash")

        // solve de un desafío n (dummy): debe devolver un valor (provee el solver está vivo)
        val dummyN = "AF-Z0tGVXJb0"
        val solved = EjsCipherSolver.solve("n", dummyN)
        println("[ejs] solve('n')=$solved")
        assertTrue(solved != null && solved.isNotEmpty(), "[ejs] solve('n') falló")
    }
}
