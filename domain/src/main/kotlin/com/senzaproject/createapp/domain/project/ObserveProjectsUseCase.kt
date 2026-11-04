package com.senzaproject.createapp.domain.project

import kotlinx.coroutines.flow.Flow

class ObserveProjectsUseCase(
    private val repository: ProjectRepository,
) {
    operator fun invoke(): Flow<List<Project>> = repository.observeProjects()
}
