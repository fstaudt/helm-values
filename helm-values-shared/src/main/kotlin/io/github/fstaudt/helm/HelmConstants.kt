package io.github.fstaudt.helm

object HelmConstants {
    const val HELM_CHART_FILE = "Chart.yaml"
    const val HELM_CHARTS_DIR = "charts"
    const val HELM_VALUES_FILE = "values.yaml"
    private const val DIGITS = "0|[1-9]\\d*"
    private const val IDENTIFIERS = "[\\da-zA-Z-]+(\\.[\\da-zA-Z-]+)*"
    val SEMVER_REGEX = "^($DIGITS)\\.($DIGITS)\\.($DIGITS)(-$IDENTIFIERS)?(\\+$IDENTIFIERS)?$".toRegex()
}
