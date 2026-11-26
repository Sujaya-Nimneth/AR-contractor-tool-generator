package com.arcontractor.estimator

import android.app.Application
import com.arcontractor.estimator.data.local.AppDatabase
import com.arcontractor.estimator.data.pdf.AndroidQuotePdfExporter
import com.arcontractor.estimator.data.pdf.QuotePdfExporter
import com.arcontractor.estimator.data.repository.ProjectRepository
import com.arcontractor.estimator.data.repository.RoomMaterialCatalogRepository
import com.arcontractor.estimator.data.repository.RoomProjectRepository
import com.arcontractor.estimator.data.repository.MaterialCatalogRepository
import com.arcontractor.estimator.data.update.UpdateCoordinator

class EstimatorApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    private val database = AppDatabase.getInstance(application)

    val materialCatalogRepository: MaterialCatalogRepository =
        RoomMaterialCatalogRepository(
            materialDao = database.materialCatalogDao(),
            surfaceDao = database.surfaceDao()
        )

    val projectRepository: ProjectRepository =
        RoomProjectRepository(
            projectDao = database.projectDao(),
            surfaceDao = database.surfaceDao(),
            materialDao = database.materialCatalogDao()
        )

    val quotePdfExporter: QuotePdfExporter =
        AndroidQuotePdfExporter(application)

    val updateCoordinator: UpdateCoordinator = UpdateCoordinator(application)
}
