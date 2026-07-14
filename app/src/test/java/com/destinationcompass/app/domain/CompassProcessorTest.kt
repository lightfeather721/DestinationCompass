package com.destinationcompass.app.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class CompassProcessorTest {
    @Test
    fun northCrossingDoesNotJumpThroughSouth() {
        val processor = CompassProcessor()
        processor.process(359f)
        val filtered = processor.process(1f)
        assertTrue(filtered > 350f || filtered < 10f)
    }

    @Test
    fun smallNoiseIsSmoothed() {
        val processor = CompassProcessor()
        processor.process(10f)
        val filtered = processor.process(11f)
        assertTrue(filtered in 10f..10.2f)
    }

    @Test
    fun largeTurnRemainsResponsive() {
        val processor = CompassProcessor()
        processor.process(10f)
        val filtered = processor.process(100f)
        assertTrue(filtered > 60f)
    }
}
