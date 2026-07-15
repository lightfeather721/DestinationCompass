package com.destinationcompass.app.domain

import kotlin.math.atan2

/** Calculates a heading that remains usable with the phone held flat or upright. */
object OrientationHeadingCalculator {
    fun headingDegrees(rotationMatrix: FloatArray): Float {
        require(rotationMatrix.size >= 9) { "A 3x3 rotation matrix is required" }

        // The top edge of the display is the natural reference while the phone is flat.
        val topEast = rotationMatrix[1]
        val topNorth = rotationMatrix[4]
        val topHorizontalMagnitude = topEast * topEast + topNorth * topNorth

        // When the phone is upright, the top edge points upward. Use the direction
        // through the back of the phone instead, which matches the user's line of sight.
        val forwardEast = -rotationMatrix[2]
        val forwardNorth = -rotationMatrix[5]
        val forwardHorizontalMagnitude = forwardEast * forwardEast + forwardNorth * forwardNorth

        val (east, north) = if (forwardHorizontalMagnitude > topHorizontalMagnitude) {
            forwardEast to forwardNorth
        } else {
            topEast to topNorth
        }
        return BearingCalculator.normalizeDegrees(
            Math.toDegrees(atan2(east.toDouble(), north.toDouble())).toFloat()
        )
    }
}
