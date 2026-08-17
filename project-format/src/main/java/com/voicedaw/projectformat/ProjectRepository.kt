package com.voicedaw.projectformat

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ProjectRepository(private val context: Context) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val manifestAdapter = moshi.adapter(ProjectManifest::class.java).indent("  ")

    private val projectsRoot: File
        get() = File(context.filesDir, "projects").also { it.mkdirs() }

    suspend fun createProject(name: String): Result<ProjectManifest> = withContext(Dispatchers.IO) {
        runCatching {
            val manifest = ProjectManifest(name = name)
            val projectDir = projectDir(manifest.projectId)
            projectDir.mkdirs()
            File(projectDir, "audio").mkdirs()
            File(projectDir, "midi").mkdirs()
            writeManifestAtomic(projectDir, manifest)
            manifest
        }
    }

    suspend fun loadProject(projectId: String): Result<ProjectManifest> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(projectDir(projectId), ProjectManifest.MANIFEST_FILE)
            val json = file.readText()
            manifestAdapter.fromJson(json) ?: error("Failed to parse manifest for $projectId")
        }
    }

    suspend fun saveProject(manifest: ProjectManifest): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val projectDir = projectDir(manifest.projectId)
            val updated = manifest.copy(modifiedAt = System.currentTimeMillis())
            writeManifestAtomic(projectDir, updated)
        }
    }

    suspend fun listProjects(): List<String> = withContext(Dispatchers.IO) {
        projectsRoot.listFiles()
            ?.filter { it.isDirectory && File(it, ProjectManifest.MANIFEST_FILE).exists() }
            ?.map { it.name }
            ?: emptyList()
    }

    suspend fun deleteProject(projectId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            projectDir(projectId).deleteRecursively()
            Unit
        }
    }

    // Private helpers

    private fun projectDir(projectId: String): File =
        File(projectsRoot, projectId)

    private fun writeManifestAtomic(projectDir: File, manifest: ProjectManifest) {
        val target = File(projectDir, ProjectManifest.MANIFEST_FILE)
        val tmp    = File(projectDir, "${ProjectManifest.MANIFEST_FILE}.tmp")
        tmp.writeText(manifestAdapter.toJson(manifest))
        tmp.renameTo(target)
    }
}
