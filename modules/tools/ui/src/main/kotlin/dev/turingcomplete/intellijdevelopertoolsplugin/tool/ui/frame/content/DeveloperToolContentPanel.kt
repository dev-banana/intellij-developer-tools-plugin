package dev.turingcomplete.intellijdevelopertoolsplugin.tool.ui.frame.content

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages.InputDialog
import com.intellij.ui.RelativeFont
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.actionsButton
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.JBTabsFactory
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.components.BorderLayoutPanel
import dev.turingcomplete.intellijdevelopertoolsplugin.settings.DeveloperToolsApplicationSettings.Companion.generalSettings
import dev.turingcomplete.intellijdevelopertoolsplugin.tool.ui.base.DeveloperUiTool
import dev.turingcomplete.intellijdevelopertoolsplugin.tool.ui.common.NotBlankInputValidator
import dev.turingcomplete.intellijdevelopertoolsplugin.tool.ui.common.UiUtils.dumbAwareAction
import dev.turingcomplete.intellijdevelopertoolsplugin.tool.ui.common.castedObject
import dev.turingcomplete.intellijdevelopertoolsplugin.tool.ui.frame.instance.handling.OpenDeveloperToolContext
import dev.turingcomplete.intellijdevelopertoolsplugin.tool.ui.frame.instance.handling.OpenDeveloperToolHandler
import dev.turingcomplete.intellijdevelopertoolsplugin.tool.ui.frame.instance.handling.OpenDeveloperToolReference
import dev.turingcomplete.intellijdevelopertoolsplugin.tool.ui.frame.menu.DeveloperToolNode
import dev.turingcomplete.intellijdevelopertoolsplugin.tool.ui.frame.menu.DeveloperToolNode.DeveloperToolContainer
import javax.swing.Icon
import javax.swing.JComponent
import kotlin.reflect.cast

open class DeveloperToolContentPanel(protected val developerToolNode: DeveloperToolNode) :
  BorderLayoutPanel() {
  // -- Properties ---------------------------------------------------------- //

  private lateinit var tabs: JBTabs
  private lateinit var selectedDeveloperToolInstance:
    ObservableMutableProperty<DeveloperToolContainer>

  // -- Initialization ------------------------------------------------------ //

  init {
    addToTop(createTitleBar())
    addToCenter(createMainContent())
  }

  // -- Exposed Methods ----------------------------------------------------- //

  fun selected() {
    selectedDeveloperToolInstance.get().instance.activated()
  }

  fun deselected() {
    selectedDeveloperToolInstance.get().instance.deactivated()
  }

  fun <T : OpenDeveloperToolContext> openTool(
    context: T,
    reference: OpenDeveloperToolReference<out T>,
  ) {
    val developerUiToolInstance = selectedDeveloperToolInstance.get().instance
    assert(developerUiToolInstance is OpenDeveloperToolHandler<*>)
    @Suppress("UNCHECKED_CAST")
    (developerUiToolInstance as OpenDeveloperToolHandler<T>).applyOpenDeveloperToolContext(
      reference.contextClass.cast(context)
    )
  }

  @Suppress("DialogTitleCapitalization")
  protected open fun Row.buildTitle(): JComponent {
    return label(developerToolNode.developerUiToolPresentation.contentTitle)
      .applyToComponent { formatTitle() }
      .component
  }

  protected fun JComponent.formatTitle() {
    RelativeFont.BOLD.install(this)
    RelativeFont.LARGE.install(this)
  }

  // -- Private Methods ----------------------------------------------------- //

  private fun createTitleBar(): JComponent =
    panel {
        row {
            val titleComponent = buildTitle()

            val actions =
              mutableListOf(
                dumbAwareAction("Reset") {
                  selectedDeveloperToolInstance.get().apply {
                    configuration.reset()
                    instance.reset()
                  }
                },
                createNewWorkbenchAction(),
              )
            developerToolNode.developerUiToolPresentation.description?.let { description ->
              actions.add(
                dumbAwareAction("Show Tool Description") { description.show(titleComponent) }
              )
            }
            actionsButton(actions = actions.toTypedArray(), icon = AllIcons.General.GearPlain)
              .align(AlignX.RIGHT)
              .resizableColumn()
              .gap(RightGap.SMALL)
          }
          .resizableRow()
      }
      .apply { border = JBEmptyBorder(0, 8, 0, 8) }

  private fun createMainContent(): JComponent {
    tabs = JBTabsFactory.createTabs(developerToolNode.project, developerToolNode.parentDisposable)

    restoreTabOrder()

    developerToolNode.developerTools.forEach { addWorkbench(it) }
    selectedDeveloperToolInstance = AtomicProperty(tabs.selectedInfo!!.castedObject())

    tabs.addListener(createTabsChangedListener(), developerToolNode.parentDisposable)
    syncTabsSelectionVisibility()

    return tabs.component
  }

  private fun syncTabsSelectionVisibility() {
    tabs.presentation.isHideTabs =
      generalSettings.hideWorkbenchTabsOnSingleTab.get() && tabs.tabCount == 1
  }

  private fun createTabsChangedListener() =
    object : TabsListener {
      override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
        oldSelection?.castedObject<DeveloperToolContainer>()?.instance?.deactivated()

        if (newSelection != null) {
          val newDeveloperToolInstance = newSelection.castedObject<DeveloperToolContainer>()
          selectedDeveloperToolInstance.set(newDeveloperToolInstance)
          newDeveloperToolInstance.instance.activated()
        }
      }
    }

  private fun addWorkbench(developerToolContainer: DeveloperToolContainer) {
    val developerToolComponent = developerToolContainer.instance.createComponent()
    val tabInfo =
      TabInfo(developerToolComponent).apply {
        setText(developerToolContainer.configuration.name)
        setObject(developerToolContainer)

        val destroyAction = createDestroyWorkbenchAction(developerToolContainer.instance, this)
        setTabLabelActions(
          DefaultActionGroup(destroyAction),
          DeveloperToolContentPanel::class.java.name,
        )

        val newWorkbenchAction = createNewWorkbenchAction()
        setTabPaneActions(DefaultActionGroup(newWorkbenchAction))
      }
    tabs.addTab(tabInfo)
    tabs.select(tabInfo, false)
    tabs.setPopupGroup(
      DefaultActionGroup(
        createRenameWorkbenchAction(),
        createMoveTabAction(true),
        createMoveTabAction(false),
      ),
      DeveloperToolContentPanel::class.java.name,
      true,
    )
  }

  private fun createRenameWorkbenchAction() =
    object : DumbAwareAction("Rename", null, AllIcons.Actions.Edit) {

      override fun actionPerformed(e: AnActionEvent) {
        val (_, developerToolConfiguration) = selectedDeveloperToolInstance.get()

        val inputDialog =
          InputDialog(
            developerToolNode.project,
            "New name:",
            "Rename",
            null,
            developerToolConfiguration.name,
            NotBlankInputValidator(),
          )
        inputDialog.show()
        inputDialog.inputString?.let { newName ->
          developerToolConfiguration.name = newName
          tabs.selectedInfo?.setText(newName)
        }
      }

      override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }

  private fun createMoveTabAction(left: Boolean) =
    object : DumbAwareAction(if (left) "Move Left" else "Move Right") {
      override fun actionPerformed(e: AnActionEvent) {
        val selectedTab = tabs.selectedInfo ?: return
        val selectedIndex = tabs.getIndexOf(selectedTab)

        if (selectedIndex == -1) return

        val newIndex = if (left) {
          (selectedIndex - 1).coerceAtLeast(0)
        } else {
          (selectedIndex + 1).coerceAtMost(tabs.tabCount - 1)
        }

        if (newIndex != selectedIndex) {
          moveTab(selectedIndex, newIndex)
          saveTabOrder()
        }
      }

      override fun update(e: AnActionEvent) {
        val selectedTab = tabs.selectedInfo
        val selectedIndex = if (selectedTab != null) tabs.getIndexOf(selectedTab) else -1

        e.presentation.isEnabled = when {
          selectedTab == null -> false
          left -> selectedIndex > 0
          else -> selectedIndex < (tabs.tabCount - 1)
        }
      }

      override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }

  private fun moveTab(fromIndex: Int, toIndex: Int) {
    if (fromIndex == toIndex) return

    val allTabs = tabs.tabs.toList()
    val tabToMove = allTabs[fromIndex]

    // Remove the tab at fromIndex
    tabs.removeTab(tabToMove)

    // Get the updated list after removal
    val remainingTabs = tabs.tabs.toList()

    // Calculate the correct insertion position
    val insertAtIndex = when {
      toIndex == 0 -> 0
      toIndex >= remainingTabs.size -> remainingTabs.size
      fromIndex < toIndex -> toIndex - 1 // Moving right, adjust for removal
      else -> toIndex // Moving left, no adjustment needed
    }

    // Insert at the new position
    if (insertAtIndex >= remainingTabs.size) {
      tabs.addTab(tabToMove)
    } else {
      tabs.addTab(tabToMove, insertAtIndex)
    }

    // Reselect the moved tab
    tabs.select(tabToMove, false)
  }

  private fun saveTabOrder() {
    val tabOrder = tabs.tabs.map { it.text }
    developerToolNode.settings.setWorkbenchTabOrder(tabOrder)
  }

  private fun restoreTabOrder() {
    val savedOrder = developerToolNode.settings.getWorkbenchTabOrder()
    val developerToolsMap = developerToolNode.developerTools.associateBy { it.configuration.name }

    if (savedOrder.isNotEmpty()) {
      // Add tabs in saved order
      savedOrder.forEach { tabName ->
        developerToolsMap[tabName]?.let { developerToolContainer ->
          addWorkbench(developerToolContainer)
        }
      }

      // Add any new developer tools that weren't in the saved order
      developerToolNode.developerTools.forEach { developerToolContainer ->
        if (!savedOrder.contains(developerToolContainer.configuration.name)) {
          addWorkbench(developerToolContainer)
        }
      }
    } else {
      // No saved order, add in default order
      developerToolNode.developerTools.forEach { addWorkbench(it) }
    }
  }

  private fun createDestroyWorkbenchAction(developerUiTool: DeveloperUiTool, tabInfo: TabInfo) =
    DestroyWorkbenchAction(
      {
        tabs.removeTab(tabInfo)
        developerToolNode.destroyDeveloperToolInstance(developerUiTool)
        syncTabsSelectionVisibility()
      },
      { tabs.tabs.size > 1 },
    )

  private fun createNewWorkbenchAction() =
    object : DumbAwareAction("New Workbench", null, AllIcons.General.Add) {

      override fun actionPerformed(e: AnActionEvent) {
        addWorkbench(developerToolNode.createNewDeveloperToolInstance())
        syncTabsSelectionVisibility()
      }

      override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }

  // -- Inner Type ---------------------------------------------------------- //

  private class DestroyWorkbenchAction(
    private val removeTab: () -> Unit,
    private val visible: () -> Boolean,
  ) : DumbAwareAction("Close Workbench") {

    override fun update(e: AnActionEvent) {
      e.presentation.apply {
        isVisible = visible()
        icon = CLOSE_ICON
        hoveredIcon = CLOSE_HOVERED_ICON
      }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
      removeTab()
    }
  }

  // -- Companion Object ---------------------------------------------------- //

  companion object {

    private val CLOSE_ICON: Icon = AllIcons.Actions.Close
    private val CLOSE_HOVERED_ICON: Icon = AllIcons.Actions.CloseHovered
  }
}
