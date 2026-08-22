package example.nucleus.utils.cipher

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull

class SignatureTimestampSmokeTest {
    @Test
    fun `getSignatureTimestamp desde player js`() = runBlocking {
        val sts = PlayerJsFetcher.getSignatureTimestamp()
        assertNotNull(sts, "[sts] no se pudo extraer el signatureTimestamp")
        println("[sts] playerJs sts=$sts")
    }
}
