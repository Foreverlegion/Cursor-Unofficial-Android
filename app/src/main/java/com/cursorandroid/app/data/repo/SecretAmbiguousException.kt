package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.ApiException
import com.cursorandroid.app.data.api.SecretVersion

class SecretAmbiguousException(
    val versions: List<SecretVersion>,
    cause: ApiException,
) : RuntimeException(cause.displayMessage(), cause)
