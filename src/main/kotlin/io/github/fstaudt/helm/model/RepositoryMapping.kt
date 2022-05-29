package io.github.fstaudt.helm.model

import java.io.Serializable

/**
 * Mapping to an external JSON schema repository for validation of values in charts of a Helm repository
 * @param baseUri base URI to the repository
 *
 *                 must include protocol and host, may include port and path and should not end with "/"
 *
 *                 examples: http://my.charts.repository:1080, https://my.charts.repository/apps
 * @param authorizationHeader optional authorization header for the repository
 *
 *                            examples: Basic dGVzdDp0ZXN0, Bearer xxx.xxxxxx.xxx
 */
data class RepositoryMapping(val baseUri: String, val authorizationHeader: String? = null) : Serializable
