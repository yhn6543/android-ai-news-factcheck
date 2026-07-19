package com.example.fakenews.data.repository

enum class NewsLoadStatus {
    Remote,
    PartialFailure,
    Failed
}

interface NewsRepositoryStatusProvider {
    val lastLoadStatus: NewsLoadStatus?
}
