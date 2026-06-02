package com.dermatrack.ai.data.model

import com.dermatrack.ai.integration.autoderm.AutodermPrediction
import com.dermatrack.ai.integration.autoderm.parseAutodermPredictionsJson

fun AutodermScreeningEntity.predictions(): List<AutodermPrediction> =
    parseAutodermPredictionsJson(predictionsJson)

fun AutodermScreeningEntity.isSuccess(): Boolean =
    AutodermScreeningStatus.fromStorage(status) == AutodermScreeningStatus.Success
