package io.github.fuyuz.contextmorph.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class ContextMorphExtension @Inject constructor(
    objects: ObjectFactory
) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
}
