package io.github.fstaudt.helm.exceptions

class PublicationException(uri: String, httpCode: Int, cause: Throwable? = null) :
    RuntimeException("Publication of $uri failed with HTTP code $httpCode.", cause)