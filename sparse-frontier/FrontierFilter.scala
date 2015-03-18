package BFSSparseFrontier

import Chisel._
import AXIDefs._
import AXIStreamDefs._

class FrontierFilter(dvSizeBits: Int) extends Module {
  val io = new Bundle {
    val start = Bool(INPUT)
    val finished = Bool(OUTPUT)
    val distVecCount = UInt(INPUT, 32)
    val currentLevel = UInt(INPUT, dvSizeBits)
    val frontierSize = UInt(OUTPUT, 32)

    // distance vector in, frontier indices out
    val distVecIn = new AXIStreamSlaveIF(UInt(width = 32))
    val frontierOut = new AXIStreamMasterIF(UInt(width = 32))
  }
  io.distVecIn.renameSignals("distVecIn")
  io.frontierOut.renameSignals("frontierOut")

  val downsize = Module(new AXIStreamDownsizer(32, dvSizeBits))
  downsize.io.in <> io.distVecIn
  val distVecWord = downsize.io.out

  val regDistVecCount = Reg(init = UInt(0, 32))
  val regDistVecLeft = Reg(init = UInt(0, 32))
  val regCurrentLevel = Reg(init = UInt(0, 32))
  val regFrontierSize = Reg(init = UInt(0, 32))

  val sIdle :: sPullDV :: sPushFrontier :: sFinished :: Nil = Enum(UInt(), 4)
  val regState = Reg(init = UInt(sIdle))

  // default outputs
  io.finished := Bool(false)
  io.frontierSize := regFrontierSize

  distVecWord.ready := Bool(false)
  io.frontierOut.valid := Bool(false)
  io.frontierOut.bits := regDistVecCount

  switch(regState) {
      is(sIdle) {
        regDistVecCount := UInt(0)
        regDistVecLeft := io.distVecCount
        regCurrentLevel := io.currentLevel

        when(io.start) {
          regFrontierSize := UInt(0)
          regState := sPullDV
        }
      }

      is(sPullDV) {
        when(regDistVecLeft === UInt(0)) { regState := sFinished }
        .otherwise {
          distVecWord.ready := Bool(true)

          when(distVecWord.valid) {
            regDistVecLeft := regDistVecLeft - UInt(1)
            when(distVecWord.bits === regCurrentLevel) {
              regState := sPushFrontier
            } .otherwise {
              regDistVecCount := regDistVecCount + UInt(1)
            }
          }
        }
      }

      is(sPushFrontier) {
        io.frontierOut.valid := Bool(true)
        when(io.frontierOut.ready) {
          regFrontierSize := regFrontierSize + UInt(1)
          regDistVecCount := regDistVecCount + UInt(1)
          regState := sPullDV
        }
      }

      is(sFinished) {
        io.finished := Bool(true)
        when(!io.start) { regState := sIdle}
      }
  }
}
