package com.pwc.mockpresence.model

/** Common spots relative to an office in a scenario's office list. */
object Spots {
    fun desk(o: Int = 0) = Spot(o, SpotKind.CENTRE, 15.0, bearing = 200.0)
    fun centre(o: Int = 0) = Spot(o, SpotKind.CENTRE, 0.0)
    fun outside(o: Int = 0, m: Double = 400.0) = Spot(o, SpotKind.FROM_EDGE, m)
    fun edge(o: Int = 0, m: Double = 0.0) = Spot(o, SpotKind.FROM_EDGE, m)
}
