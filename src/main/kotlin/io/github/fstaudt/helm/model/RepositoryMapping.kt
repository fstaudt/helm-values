package io.github.fstaudt.helm.model

import java.io.Serializable

/**
 * Mapping to an external JSON schema repository for validation of values in charts of a Helm repository
 * @param baseUri base URI to the repository
 *
 *                 must include protocol and host, may include port and path and should not end with "/"
 *
 *                 examples: http://my.charts.repository:1080, https://my.charts.repository/apps
 * @param username optional username for basic authentication on the repository
 * @param password optional password for basic authentication on the repository
 */
data class RepositoryMapping(
    val baseUri: String,
    val username: String? = null,
    val password: String? = null,
) : Serializable
