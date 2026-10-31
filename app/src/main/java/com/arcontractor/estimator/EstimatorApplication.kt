package com.arcontractor.estimator

import android.app.Application
import com.arcontractor.estimator.data.local.AppDatabase
import com.arcontractor.estimator.data.pdf.AndroidQuotePdfExporter
import com.arcontractor.estimator.data.pdf.QuotePdfExporter
import com.arcontractor.estimator.data.repository.ProjectRepository
import com.arcontractor.estimator.data.repository.RoomMaterialCatalogRepository
import com.arcontractor.estimator.data.repository.RoomProjectRepository
import com.arcontractor.estimator.data.repository.MaterialCatalogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EstimatorApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this).also(AppContainer::initialize)
    }
}

class AppContainer(application: Application) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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

    fun initialize() {
        applicationScope.launch {
            materialCatalogRepository.ensureSeeded()
            projectRepository.ensureDefaultProject()
        }
    }
}
