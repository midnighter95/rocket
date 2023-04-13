package cosim.elaborate

import chisel3._
import chisel3.experimental.ExtModule
import chisel3.experimental.hierarchy.{Definition, instantiable, public}
import chisel3.util.{Decoupled, HasExtModuleInline}
import cosim.elaborate.TapModule
import freechips.rocketchip.tile.NMI
import org.chipsalliance.tilelink.bundle.{TLChannelA, TLChannelB, TLChannelC, TLChannelD, TLChannelE, TileLinkChannelAParameter, TileLinkChannelBParameter, TileLinkChannelCParameter, TileLinkChannelDParameter, TileLinkChannelEParameter}

class VerificationModule(dut:DUT) extends TapModule {
  val clockRate = 5

  val clock = IO(Output(Clock()))
  val reset = IO(Output(Bool()))
  val resetVector = IO(Output(UInt(32.W)))
  val nmi = IO(Output(new NMI(32)))
  val intIn = IO(Output(Bool()))

  val tlAParam = TileLinkChannelAParameter(32, 2, 64, 3)
  val tlBParam = TileLinkChannelBParameter(32, 2, 64, 3)
  val tlCParam = TileLinkChannelCParameter(32, 2, 64, 3)
  val tlDParam = TileLinkChannelDParameter(32, 2, 64, 2)
  val tlEParam = TileLinkChannelEParameter(2)

  val tlbundle_a = Flipped(Decoupled(new TLChannelA(tlAParam)))
  tlbundle_a.bits

  val tlportA = IO(Flipped(Decoupled(new TLChannelA(tlAParam))))
  val tlportB = IO(Decoupled(new TLChannelB(tlBParam)))
  val tlportC = IO(Flipped(Decoupled(new TLChannelC(tlCParam))))
  val tlportD = IO(Decoupled(new TLChannelD(tlDParam)))
  val tlportE = IO(Flipped(Decoupled(new TLChannelE(tlEParam))))

  val verbatim = Module(new ExtModule with HasExtModuleInline {
    override val desiredName = "Verbatim"
    val clock = IO(Output(Clock()))
    val reset = IO(Output(Bool()))
    setInline(
      "verbatim.sv",
      s"""module Verbatim(
         |  output clock,
         |  output reset
         |);
         |  reg _clock = 1'b0;
         |  always #($clockRate) _clock = ~_clock;
         |  reg _reset = 1'b1;
         |  initial #(${2 * clockRate + 1}) _reset = 0;
         |
         |  assign clock = _clock;
         |  assign reset = _reset;
         |
         |  import "DPI-C" function void dpiInitCosim();
         |  initial dpiInitCosim();
         |
         |  import "DPI-C" function void dpiTimeoutCheck();
         |  always #(${2 * clockRate + 1}) dpiTimeoutCheck();
         |
         |  export "DPI-C" function dpiDumpWave;
         |  function dpiDumpWave(input string file);
         |   $$dumpfile(file);
         |   $$dumpvars(0);
         |  endfunction;
         |
         |  export "DPI-C" function dpiFinish;
         |  function dpiFinish();
         |   $$finish;
         |  endfunction;
         |
         |  export "DPI-C" function dpiError;
         |  function dpiError(input string what);
         |   $$error(what);
         |  endfunction;
         |
         |endmodule
         |""".stripMargin
    )
  })
  clock := verbatim.clock
  reset := verbatim.reset

  nmi.rnmi := true.B
  nmi.rnmi_exception_vector := 0.U
  nmi.rnmi_interrupt_vector := 0.U

  intIn := false.B


  val dpiBasePoke = Module(new ExtModule with HasExtModuleInline {
    override val desiredName = "dpiBasePoke"
    val resetVector = IO(Output(UInt(32.W)))
    val clock = IO(Input(Clock()))
    setInline(
      s"$desiredName.sv",
      s"""module $desiredName(
         |  input clock,
         |  output [31:0] resetVector
         |);
         |  import "DPI-C" function void dpiBasePoke(output bit[31:0] resetVector);
         |
         |  always @ (posedge clock) $desiredName(resetVector);
         |endmodule
         |""".stripMargin
    )
  })
  dpiBasePoke.clock := clock
  resetVector := dpiBasePoke.resetVector

  val dpiBasePeek = Module(new ExtModule with HasExtModuleInline {
    override val desiredName = "dpiBasePeek"
    val clock = IO(Input(Clock()))
    val address = IO(Input(UInt(32.W)))
    setInline(
      s"$desiredName.sv",
      s"""module $desiredName(
         |  input clock,
         |  input [31:0] address
         |);
         |  import "DPI-C" function void dpiBasePeek(input bit[31:0] address);
         |
         |  always @ (negedge clock) $desiredName(address);
         |endmodule
         |""".stripMargin
    )
  })
  dpiBasePeek.address := tlportA.bits.address
  dpiBasePeek.clock := clock

  @instantiable
  class PeekTL(param_a: TileLinkChannelAParameter) extends ExtModule with HasExtModuleInline {
    override val desiredName = "dpiPeekTL"
    @public val clock = IO(Input(Clock()))
    @public val aBits: TLChannelA = IO(Input(new TLChannelA(param_a)))
    @public val aValid: Bool = IO(Input(Bool()))
    @public val dReady: Bool = IO(Input(Bool()))
    setInline(
      "dpiPeekTL.sv",
      s"""module $desiredName(
         |  input clock,
         |  input bit[${aBits.opcode.getWidth - 1}:0] aBits_opcode,
         |  input bit[${aBits.param.getWidth - 1}:0] aBits_param,
         |  input bit[${aBits.size.getWidth - 1}:0] aBits_size,
         |  input bit[${aBits.source.getWidth - 1}:0] aBits_source,
         |  input bit[${aBits.address.getWidth - 1}:0] aBits_address,
         |  input bit[${aBits.mask.getWidth - 1}:0] aBits_mask,
         |  input bit[${aBits.data.getWidth - 1}:0] aBits_data,
         |  input bit aBits_corrupt,
         |  input bit aValid,
         |  input bit dReady
         |);
         |import "DPI-C" function void $desiredName(
         |  input bit[${aBits.opcode.getWidth - 1}:0] a_opcode,
         |  input bit[${aBits.param.getWidth - 1}:0] a_param,
         |  input bit[${aBits.size.getWidth - 1}:0] a_size,
         |  input bit[${aBits.source.getWidth - 1}:0] a_source,
         |  input bit[${aBits.address.getWidth - 1}:0] a_address,
         |  input bit[${aBits.mask.getWidth - 1}:0] a_mask,
         |  input bit[${aBits.data.getWidth - 1}:0] a_data,
         |  input bit a_corrupt,
         |  input bit a_valid,
         |  input bit d_ready
         |);
         |always @ (posedge clock) $desiredName(
         |  aBits_opcode,
         |  aBits_param,
         |  aBits_size,
         |  aBits_source,
         |  aBits_address,
         |  aBits_mask,
         |  aBits_data,
         |  aBits_corrupt,
         |  aValid,
         |  dReady
         |);
         |endmodule
         |""".stripMargin
    )
  }

  val dpiPeekTL = Module(new PeekTL(tlAParam))
  dpiPeekTL.clock := clock
  dpiPeekTL.aBits := tlportA.bits
  dpiPeekTL.aValid := tlportA.valid
  dpiPeekTL.dReady := tlportD.ready




  tlportA.ready := true.B
  tlportC.ready := true.B
  tlportE.ready := true.B

  tlportB.valid := false.B
  tlportB.bits.opcode := 0.U
  tlportB.bits.param := 0.U
  tlportB.bits.size := 0.U
  tlportB.bits.source := 0.U
  tlportB.bits.address := 0.U
  tlportB.bits.mask := 0.U
  tlportB.bits.data := 0.U
  tlportB.bits.corrupt := 0.U

  tlportD.valid := false.B
  tlportD.bits.opcode := 0.U
  tlportD.bits.param := 0.U
  tlportD.bits.size := 0.U
  tlportD.bits.source := 0.U
  tlportD.bits.data := 0.U
  tlportD.bits.corrupt := 0.U
  tlportD.bits.denied := 0.U
  tlportD.bits.sink := 0.U
}
