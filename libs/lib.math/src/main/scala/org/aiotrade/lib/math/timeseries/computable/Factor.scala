/*
 * Copyright (c) 2006-2007, AIOTrade Computing Co. and Contributors
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 *  o Redistributions of source code must retain the above copyright notice, 
 *    this list of conditions and the following disclaimer. 
 *    
 *  o Redistributions in binary form must reproduce the above copyright notice, 
 *    this list of conditions and the following disclaimer in the documentation 
 *    and/or other materials provided with the distribution. 
 *    
 *  o Neither the name of AIOTrade Computing Co. nor the names of 
 *    its contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission. 
 *    
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, 
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR 
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR 
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR 
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, 
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.aiotrade.lib.math.timeseries.computable

import org.aiotrade.lib.util.serialization.BeansDocument
import org.w3c.dom.Element
import org.aiotrade.lib.util.actors.Publisher
import org.aiotrade.lib.util.actors.Event

/**
 * Class for defining indicator's factor
 *
 * @author Caoyuan Deng
 */
@cloneable
trait Factor extends Publisher {
    
  def name: String
  def name_=(name: String): Unit
    
  def value: Float
  def value_=(value: Number): Unit
    
  def step: Float
  def step_=(step: Number)
    
  def maxValue: Float
  def maxValue_=(maxValue: Number): Unit
    
  def minValue: Float
  def minValue_=(minValue: Number): Unit

  @inline final override def equals(a: Any): Boolean = a match {
    case x: Factor => this.value.equals(x.value)
    case _ => false
  }

  @inline final override def hashCode = value.hashCode

  /** this should not be abstract method to get scalac knowing it's a override of @cloneable instead of java.lang.Object#clone */
  override def clone: Factor = {
    try {
      val newOne = super.clone.asInstanceOf[Factor]

      newOne.name = name
      newOne.value = value
      newOne.step = step
      newOne.minValue = minValue
      newOne.maxValue = maxValue

      return newOne
    } catch {case ex: CloneNotSupportedException => throw new InternalError(ex.toString)}
  }

  def writeToBean(doc: BeansDocument): Element = {
    val bean = doc.createBean(this)

    doc.valuePropertyOfBean(bean, "name", name)
    doc.valuePropertyOfBean(bean, "value", value)
    doc.valuePropertyOfBean(bean, "step", step)
    doc.valuePropertyOfBean(bean, "minValue", minValue)
    doc.valuePropertyOfBean(bean, "maxValue", maxValue)

    bean
  }
    
    
  //    public static class Float extends AbstractOpt implements Opt {
  //        private float value;
  //        private float step;
  //        private float minValue;
  //        private float maxValue;
  //
  //        public void setValue(final Number value) {
  //            this.value = value.floatValue();
  //        }
  //
  //        public float value() {
  //            return value;
  //        }
  //
  //        public void setStep(final Number step) {
  //            this.step = step.floatValue();
  //        }
  //
  //        public float getStep() {
  //            return step;
  //        }
  //
  //        public void setMaxValue(final Number maxValue) {
  //            this.maxValue = maxValue.floatValue();
  //        }
  //
  //        public float getMaxValue() {
  //            return maxValue;
  //        }
  //
  //        public void setMinValue(final Number minValue) {
  //            this.minValue = minValue.floatValue();
  //        }
  //
  //        public float getMinValue() {
  //            return minValue;
  //        }
  //
  //    }
}

case class FactorEvent(source: Factor) extends Event
