package com.samcod3.meditrack.di

import com.samcod3.meditrack.data.repository.DrugRepository
import com.samcod3.meditrack.data.repository.DrugRepositoryImpl
import org.koin.dsl.module

val dataModule = module {
    single<DrugRepository> { DrugRepositoryImpl(get()) }
}
