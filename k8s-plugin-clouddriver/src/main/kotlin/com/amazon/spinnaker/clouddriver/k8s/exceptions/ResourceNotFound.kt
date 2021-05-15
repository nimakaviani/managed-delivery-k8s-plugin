package com.amazon.spinnaker.clouddriver.k8s.exceptions

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class ResourceNotFound : RuntimeException()