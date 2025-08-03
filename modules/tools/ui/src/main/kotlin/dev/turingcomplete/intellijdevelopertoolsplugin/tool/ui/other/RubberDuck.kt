package dev.turingcomplete.intellijdevelopertoolsplugin.tool.ui.other

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindText
import com.intellij.util.ui.components.BorderLayoutPanel
import dev.turingcomplete.intellijdevelopertoolsplugin.settings.DeveloperToolConfiguration
import dev.turingcomplete.intellijdevelopertoolsplugin.tool.ui.base.DeveloperUiTool
import dev.turingcomplete.intellijdevelopertoolsplugin.tool.ui.base.DeveloperUiToolContext
import dev.turingcomplete.intellijdevelopertoolsplugin.tool.ui.base.DeveloperUiToolFactory
import dev.turingcomplete.intellijdevelopertoolsplugin.tool.ui.base.DeveloperUiToolPresentation
import java.awt.Image.SCALE_SMOOTH
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.JLabel

class RubberDuck(configuration: DeveloperToolConfiguration, parentDisposable: Disposable) : DeveloperUiTool(parentDisposable) {
  // -- Properties ---------------------------------------------------------- //

  private val duckName = configuration.register(
    key = "duckName",
    defaultValue = "Rubber Duck",
    propertyType = DeveloperToolConfiguration.PropertyType.INPUT
  )

  // -- Initialization ------------------------------------------------------ //
  // -- Exposed Methods ----------------------------------------------------- //

  override fun Panel.buildUi() {
    row {
      cell(
        JBLabel(
          """<html>
             Rubber duck debugging is a problem-solving technique where a programmer explains their code line by 
             line to a rubber duck or any other inanimate object. The act of explaining the code helps 
             the programmer to identify errors and logic mistakes in their code. This technique is widely 
             used in software development to improve code quality and debugging efficiency.
            </html>"""
            .trimMargin()
        )
      )
    }

    row("Duck Name:") {
      textField()
        .bindText(duckName)
        .align(Align.FILL)
    }

    row {
        cell(
            BorderLayoutPanel().apply {
              RubberDuck::class
                .java
                .getResourceAsStream(
                  "/dev/turingcomplete/intellijdevelopertoolsplugin/rubber-duck-yellow.png"
                )
                ?.use {
                  val read = ImageIO.read(it)
                  val scaledInstance =
                    read.getScaledInstance(read.width.div(2), read.height.div(2), SCALE_SMOOTH)

                  val clickableDuck = JLabel(ImageIcon(scaledInstance)).apply {
                    toolTipText = "Quack me!"
                    addMouseListener(object : java.awt.event.MouseAdapter() {
                      override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                        Messages.showInfoMessage(
                          "Hello, I'm ${duckName.get()} !",
                          "🐤 Quack!"
                        )
                      }
                    })
                  }

                  addToCenter(clickableDuck)
                }
            }
          )
          .align(Align.CENTER)
      }
      .resizableRow()

    row {
      comment(
        "Image by <a href='https://www.pexels.com/photo/yellow-duck-toy-beside-green-duck-toy-132464/'>Anthony</a>"
      ) {
        BrowserUtil.browse(it.url)
      }
    }
  }

  // -- Private Methods ----------------------------------------------------- //
  // -- Inner Type ---------------------------------------------------------- //

  class Factory : DeveloperUiToolFactory<RubberDuck> {

    override fun getDeveloperUiToolPresentation() =
      DeveloperUiToolPresentation(menuTitle = "Rubber Duck", contentTitle = "Rubber Duck Debugging")

    override fun getDeveloperUiToolCreator(
      project: Project?,
      parentDisposable: Disposable,
      context: DeveloperUiToolContext,
    ): ((DeveloperToolConfiguration) -> RubberDuck) = { configuration  -> RubberDuck(configuration , parentDisposable) }
  }

  // -- Companion Object ---------------------------------------------------- //
}
