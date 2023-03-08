package cosim.elaborate

import chisel3.aop.Select
import chisel3.aop.injecting.InjectingAspect
import chisel3.stage.ChiselGeneratorAnnotation
import circt.stage.{CIRCTTarget, CIRCTTargetAnnotation, ChiselStage, FirtoolOption}
import firrtl.options.TargetDirAnnotation
import firrtl.{AnnotationSeq, ChirrtlEmitter, EmitAllModulesAnnotation}
import freechips.rocketchip.devices.debug.DebugModuleKey
import freechips.rocketchip.diplomacy.MonitorsEnabled
import freechips.rocketchip.subsystem.{CacheBlockBytes, SystemBusKey, SystemBusParams}
import mainargs._
import org.chipsalliance.cde.config.{Config, Field}
import freechips.rocketchip.rocket.{DCacheParams, FrontendModule, ICacheModule, ICacheParams, MulDivParams, Rocket, RocketCoreParams}
import freechips.rocketchip.tile.RocketTileParams

object Main {
  @main
  def elaborate(
                 @arg(name = "dir") dir: String,
               ): Unit = {
    (new ChiselStage).transform(AnnotationSeq(Seq(
      TargetDirAnnotation(dir),
      new ChiselGeneratorAnnotation(() => {
        new DUT(
          new cosimConfig
        )
      }),
      firrtl.passes.memlib.InferReadWriteAnnotation,
      CIRCTTargetAnnotation(CIRCTTarget.Verilog),
      EmitAllModulesAnnotation(classOf[ChirrtlEmitter]),
      FirtoolOption("--disable-annotation-unknown"),
      InjectingAspect(
        { dut: DUT => Select.collectDeep(dut){ case icache : ICacheModule => icache } },
        {
          icache : ICacheModule =>
            chisel3.experimental.Trace.traceName(icache.s2_miss)
        }
      )
    )))
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
