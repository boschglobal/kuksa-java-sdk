package org.eclipse.kuksa.testapp

import javafx.application.Application
import javafx.scene.Scene
import javafx.stage.Stage
import org.eclipse.kuksa.testapp.ui.MainController
import org.eclipse.kuksa.testapp.viewmodel.DataBrokerViewModel

private const val SCENE_WIDTH = 1050.0
private const val SCENE_HEIGHT = 720.0

// JavaFX Application.launch requires a vararg; spreading args here is unavoidable.
@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    Application.launch(TestApp::class.java, *args)
}

class TestApp : Application() {
    private val viewModel = DataBrokerViewModel()

    override fun start(stage: Stage) {
        val mainController = MainController(viewModel, stage)
        val scene = Scene(mainController.root, SCENE_WIDTH, SCENE_HEIGHT)

        stage.title = "Kuksa Java TestApp - [DISCONNECTED]"
        stage.scene = scene
        stage.setOnCloseRequest {
            viewModel.disconnect()
        }
        stage.show()
    }
}
