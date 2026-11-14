package org.litvin.ui.tabs.points

import java.awt.Component
import java.awt.Container
import javax.swing.AbstractButton

/**
 * Clicks the Scoring rules button under the points list. The editor of the test (a fake) gives the saved values
 * (B-73: the scoring mode and "Ask error kind" are in the Scoring rules dialog).
 */
internal fun PointsScoring.saveScoringRules() {
    (findByName(settingsButtons, "points-score-settings") as AbstractButton).doClick()
}

private fun findByName(root: Component, name: String): Component? {
    if (root.name == name) return root
    if (root is Container) root.components.forEach { child -> findByName(child, name)?.let { return it } }
    return null
}
