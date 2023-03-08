package cosim.elaborate

import chisel3._
import chisel3.aop.Select
import chisel3.aop.injecting.InjectingAspect
import chisel3.stage.ChiselGeneratorAnnotation
import circt.stage.{CIRCTTarget, CIRCTTargetAnnotation, ChiselStage, FirtoolOption}
import firrtl.options.TargetDirAnnotation
import firrtl.{AnnotationSeq, ChirrtlEmitter, EmitAllModulesAnnotation}
import mainargs._
import org.chipsalliance.cde.config.{Config, Field}
import freechips.rocketchip.rocket.{DCacheParams, FrontendModule, ICacheModule, ICacheParams, MulDivParams, Rocket, RocketCoreParams,RegFile}
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
      ),
      InjectingAspect(
        { dut: DUT => Select.collectDeep(dut) { case core: Rocket => core } },
        {
          core: Rocket =>
            chisel3.experimental.Trace.traceName(core.rocketImpl.rf_waddr)
            chisel3.experimental.Trace.traceName(core.rocketImpl.rf_wdata)
            chisel3.experimental.Trace.traceName(core.rocketImpl.wb_reg_pc)
            chisel3.experimental.Trace.traceName(core.rocketImpl.wb_reg_inst)
            chisel3.experimental.Trace.traceName(core.rocketImpl.ex_reg_pc)
            chisel3.experimental.Trace.traceName(core.rocketImpl.wb_valid)
        }
      ),
//      InjectingAspect(
//        { dut: DUT => Select.collectDeep(dut) { case rf: Mem(31, UInt(64.W)) => rf } },
//        {
//          core: Rocket =>
//            chisel3.experimental.Trace.traceName(core.rocketImpl.rf.rf.W)
//        }
//      ),


    )))
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
