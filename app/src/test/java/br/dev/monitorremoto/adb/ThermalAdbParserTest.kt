package br.dev.monitorremoto.adb

import org.junit.Assert.*
import org.junit.Test

class ThermalAdbParserTest {
    @Test fun `prefers HAL to cached and does not duplicate entries`() {
        val text = """Cached temperatures:
            Temperature{mValue=35.0, mType=0, mName=CPU, mStatus=0}
            HAL Ready: true
            Current temperatures from HAL:
            Temperature{mValue=56.2, mType=0, mName=CPU, mStatus=0}
            Temperature{mValue=34.8, mType=2, mName=BATTERY, mStatus=0}
            Current cooling devices from HAL:
            CoolingDevice{mValue=0, mType=2, mName=cpu}
        """.trimIndent()
        val sensors = ThermalAdbParser.parse(text)
        assertEquals(2, sensors.size)
        assertEquals(56.2, sensors[0].celsius, 0.001)
        assertEquals("cpu", sensors[0].type)
    }
    @Test fun `rejects absent sensor sentinels and malicious labels`() {
        val text = """Cached temperatures:
            Temperature{mValue=-99.9, mType=6, mName=MISSING, mStatus=0}
            Temperature{mValue=0.0, mType=0, mName=CPU, mStatus=0}
            Temperature{mValue=54.5, mType=1, mName=GPU/test, mStatus=0}
            HAL Ready: false
        """.trimIndent()
        assertEquals(listOf("GPU/test"), ThermalAdbParser.parse(text).map { it.name })
    }
    @Test fun `unknown labels remain identified as unknown type`() {
        val text = "Cached temperatures: Temperature{mValue=49.1, mType=52, mName=thermal_zone11, mStatus=1}"
        assertEquals("other", ThermalAdbParser.parse(text).single().type)
    }
}
