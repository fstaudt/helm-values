package io.github.fstaudt.helm.test.assertions

fun String.escaped() = replace(".", "\\.")
