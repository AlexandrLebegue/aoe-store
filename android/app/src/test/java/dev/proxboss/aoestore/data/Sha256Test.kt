package dev.proxboss.aoestore.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Sha256Test {

    @Test
    fun `calcule une empreinte connue`() {
        val file = File.createTempFile("aoe-store-sha", ".txt")
        try {
            file.writeText("bonjour")
            assertEquals(
                "2cb4b1431b84ec15d35ed83bb927e27e8967d75f4bcd9cc4b25c8d879ae23e18",
                Sha256.digest(file),
            )
            assertTrue(
                Sha256.matches(
                    file,
                    "2CB4B1431B84EC15D35ED83BB927E27E8967D75F4BCD9CC4B25C8D879AE23E18",
                ),
            )
            assertFalse(Sha256.matches(file, "0000"))
        } finally {
            file.delete()
        }
    }
}
