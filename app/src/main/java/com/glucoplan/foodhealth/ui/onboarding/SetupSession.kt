package com.glucoplan.foodhealth.ui.onboarding

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Состояние первого запуска в пределах работы приложения (не сохраняется). */
@Singleton
class SetupSession @Inject constructor() {
    /** Загрузка оборвалась, выбрано «Продолжить без загрузки»: остальное докачает фоновая синхронизация. */
    val downloadSkipped = MutableStateFlow(false)
}
