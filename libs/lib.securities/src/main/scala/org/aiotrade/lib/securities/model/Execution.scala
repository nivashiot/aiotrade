package org.aiotrade.lib.securities.model

import ru.circumflex.orm._

case class ExecutionEvent(prevClose: Double, execution: Execution)

object Executions extends Table[Execution] {
  val sec = "secs_id" BIGINT() REFERENCES(Secs)

  val time = "time" BIGINT()

  val price  = "price"  DOUBLE()
  val volume = "volume" DOUBLE()
  val amount = "amount" DOUBLE()

  val flag = "flag" TINYINT() // @Note jdbc type of TINYINT is Int

  val timeIdx = getClass.getSimpleName + "_time_idx" INDEX(time.name)

  private val ONE_DAY = 24 * 60 * 60 * 1000

  def executionsOf(sec: Sec, dailyRoundedTime: Long): Seq[Execution] = {
    SELECT (this.*) FROM (this) WHERE (
      (this.sec.field EQ Secs.idOf(sec)) AND (this.time BETWEEN (dailyRoundedTime, dailyRoundedTime + ONE_DAY - 1))
    ) ORDER_BY (this.time) list
  }
}

object Execution {
  // bit masks for flag
  val MaskNone          = 1 << 0   //    000...00000001
  val MaskIn            = 1 << 1   //    000...00000010
  val MaskOut           = 1 << 2   //    000...00000100
  val MaskSame          = 1 << 3   //    000...00001000
  val MaskUp            = 1 << 4   //    000...00010000
  val MaskDown          = 1 << 5   //    000...00100000
  private val flagbit3  = 1 << 6   //    000...01000000
  private val flagbit4  = 1 << 7   //    000...10000000
}

import Execution._
class Execution {
  var sec: Sec = _
  
  var time: Long = -1

  var price:  Double = _
  var volume: Double = _
  var amount: Double = _

  var flag: Int = _ // @Note jdbc type of TINYINT is Int

  none_!
  same_!

  def none_? : Boolean = (flag & MaskNone) == MaskNone
  def in_?   : Boolean = (flag & MaskIn) == MaskIn
  def out_?  : Boolean = (flag & MaskOut) == MaskOut
  def none_! {flag = (((flag | MaskNone) & ~MaskIn) & ~MaskOut)}
  def out_!  {flag = (((flag | MaskOut) & ~MaskIn) & ~MaskNone)}
  def in_!   {flag = (((flag | MaskIn) & ~MaskOut) & ~MaskNone)}

  def same_? : Boolean = (flag & MaskSame) == MaskSame
  def up_?   : Boolean = (flag & MaskUp) == MaskUp
  def down_? : Boolean = (flag & MaskDown) == MaskDown
  def same_! {flag = (((flag | MaskSame) & ~MaskDown) & ~MaskUp)}
  def up_!   {flag = (((flag | MaskUp) & ~MaskDown) & ~MaskSame)}
  def down_! {flag = (((flag | MaskDown) & ~MaskUp) & ~MaskSame)}
}
