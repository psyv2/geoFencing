package com.geogroup.shared.alert

import com.geogroup.shared.model.BreachAlert

/**
 * Implemented on the platform side. Shared code decides whether to alert and what it
 * says; the implementation only delivers it.
 */
interface AlertPresenter {
    fun present(alert: BreachAlert)
}

/** Records alerts instead of delivering them. Used by tests. */
class RecordingAlertPresenter : AlertPresenter {

    private val _alerts: MutableList<BreachAlert> = mutableListOf()

    val alerts: List<BreachAlert> get() = _alerts.toList()

    override fun present(alert: BreachAlert) {
        _alerts += alert
    }

    fun clear() {
        _alerts.clear()
    }
}

/** Drops every alert. */
object NoOpAlertPresenter : AlertPresenter {
    override fun present(alert: BreachAlert) = Unit
}
