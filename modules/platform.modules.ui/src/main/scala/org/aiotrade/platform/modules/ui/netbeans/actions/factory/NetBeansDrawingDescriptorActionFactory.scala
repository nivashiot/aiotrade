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
package org.aiotrade.platform.modules.ui.netbeans.actions.factory

import javax.swing.Action;
import javax.swing.JOptionPane;
import org.aiotrade.lib.charting.descriptor.DrawingDescriptor;
import org.aiotrade.lib.charting.descriptor.DrawingDescriptorActionFactory;
import org.aiotrade.lib.charting.view.WithDrawingPane;
import org.aiotrade.lib.util.swing.action.DeleteAction;
import org.aiotrade.lib.util.swing.action.HideAction;
import org.aiotrade.lib.util.swing.action.SaveAction;
import org.aiotrade.lib.util.swing.action.ViewAction;
import org.aiotrade.platform.modules.ui.netbeans.windows.AnalysisChartTopComponent;
import org.openide.windows.WindowManager;

/**
 *
 * @author  Caoyuan Deng
 * @version 1.0, December 11, 2006, 10:20 PM
 * @since   1.0.4
 */
class NetBeansDrawingDescriptorActionFactory extends DrawingDescriptorActionFactory {
    
  def createActions(descriptor: DrawingDescriptor): Array[Action] = {
    Array(
      new DrawingViewAction(descriptor),
      new DrawingHideAction(descriptor),
      new DrawingDeleteAction(descriptor)
    )
  }
    
    
  private class DrawingViewAction(descriptor: DrawingDescriptor) extends ViewAction {
    putValue(Action.NAME, "Show")
        
    def execute {
      AnalysisChartTopComponent.instanceOf(descriptor.containerContents.uniSymbol) foreach {analysisWin =>
        analysisWin.lookupViewContainer(descriptor.freq) foreach {viewContainer =>
          descriptor.active = true
                    
          val masterView = viewContainer.masterView
          val withDrawingPane = masterView.asInstanceOf[WithDrawingPane];
          val drawing = withDrawingPane.descriptorToDrawing.get(descriptor) match {
            case Some(drawing) =>
              withDrawingPane.selectedDrawing = drawing
              drawing
            case None =>
              descriptor.serviceInstance(masterView) map {x =>
                withDrawingPane.addDrawing(descriptor, x);
                x
              } get
          }
                    
          viewContainer.controller.isCursorCrossLineVisible = false
          drawing.activate
                    
          /** hide other drawings */
          val descriptors = descriptor.containerContents.lookupDescriptors(
            classOf[DrawingDescriptor],
            descriptor.freq
          )
          for (_descriptor <- descriptors) {
            if (_descriptor != descriptor && _descriptor.active) {
              _descriptor.lookupAction(classOf[HideAction]).get.execute
            }
                        
          }
                    
          analysisWin.requestActive
          analysisWin.selectedViewContainer = viewContainer
        }
      }
            
    }
        
  }
    
  private class DrawingHideAction(descriptor: DrawingDescriptor) extends HideAction {
    putValue(Action.NAME, "Hide")
        
    def execute {
      descriptor.active = false
            
      AnalysisChartTopComponent.instanceOf(descriptor.containerContents.uniSymbol) foreach {analysisWin =>
        analysisWin.lookupViewContainer(descriptor.freq) foreach {viewContainer =>
          val masterView = viewContainer.masterView
                    
          masterView.asInstanceOf[WithDrawingPane].descriptorToDrawing.get(descriptor) foreach {drawing =>
            drawing.passivate
          }
                    
          viewContainer.controller.isCursorCrossLineVisible = true
          analysisWin.requestActive
          analysisWin.selectedViewContainer = viewContainer
        }
      }
    }
        
  }
    
  private class DrawingDeleteAction(descriptor: DrawingDescriptor) extends DeleteAction {
            
    putValue(Action.NAME, "Delete")
        
    def execute {
      JOptionPane.showConfirmDialog(
        WindowManager.getDefault.getMainWindow,
        "Are you sure you want to delete drawing: " + descriptor.displayName + " ?",
        "Deleting drawing ...",
        JOptionPane.YES_NO_OPTION
      ) match {
        case JOptionPane.YES_OPTION =>
          descriptor.containerContents.removeDescriptor(descriptor)
          descriptor.containerContents.lookupAction(classOf[SaveAction]) foreach {_.execute}
                
          AnalysisChartTopComponent.instanceOf(descriptor.containerContents.uniSymbol) foreach {analysisWin =>
            val masterView = analysisWin.selectedViewContainer.get.masterView
            masterView.asInstanceOf[WithDrawingPane].deleteDrawing(descriptor)
          }
        case _ =>
      }
    }
        
  }
    
    
}



