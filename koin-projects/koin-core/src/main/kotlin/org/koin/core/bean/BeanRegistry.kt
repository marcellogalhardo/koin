/*
 * Copyright 2017-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.koin.core.bean

import org.koin.core.Koin
import org.koin.dsl.definition.BeanDefinition
import org.koin.dsl.path.Path
import org.koin.error.BeanOverrideException
import org.koin.error.DependencyResolutionException
import org.koin.error.NoBeanDefFoundException
import org.koin.error.NotVisibleException


/**
 * Bean registry
 * gather definitions of beans & communicate with instance factory to handle instances
 *
 * @author - Arnaud GIULIANI
 */
class BeanRegistry() {

    val definitions = hashSetOf<BeanDefinition<*>>()

    /**
     * Add/Replace an existing bean
     *
     * @param def : Bean definition
     */
    fun declare(def: BeanDefinition<*>, path: Path) {
        val definition = def.copy(path = path)
        val existingBean = definitions.firstOrNull { it == definition }

        val isOverriding = existingBean != null
        if (isOverriding && !definition.allowOverride) {
            throw BeanOverrideException("Try to override definition $existingBean with $definition, but override is not allowed. Use 'override' option in your definition or module.")
        }

        existingBean?.let {
            definitions.remove(existingBean)
        }
        definitions += definition

        val kw = if (isOverriding) "override" else "declare"
        Koin.logger.info("[module] $kw $definition")
    }

    /**
     * Search bean by its name, respectfully to requested type.
     */
    fun searchByName(name: String, clazz: kotlin.reflect.KClass<*>): List<BeanDefinition<*>> =
        searchDefinition { it.name == name && (it.clazz == clazz || it.types.contains(clazz)) }

    /**
     * Search for any bean definition
     */
    fun searchAll(clazz: kotlin.reflect.KClass<*>): List<BeanDefinition<*>> {
        val concreteTypes = searchDefinition { it.clazz == clazz }
        val extraBindTypes = searchDefinition { it.types.contains(clazz) }
        return (concreteTypes + extraBindTypes)
    }

    /**
     * Search definition with given filter function
     */
    private fun searchDefinition(filter: (BeanDefinition<*>) -> Boolean): List<BeanDefinition<*>> =
        definitions.filter(filter)

    /**
     * Get bean definitions from given path
     */
    fun getDefinitions(paths: Set<Path>): List<BeanDefinition<*>> {
        return definitions.filter { def -> definitions.first { it == def }.path in paths }
    }


    /**
     * Retrieve bean definition
     * @param clazzName - class name
     * @param modulePath - Module path
     * @param definitionResolver - function to find bean definition
     * @param lastInStack - to check visibility with last bean in stack
     */
    fun getVisibleBean(
        clazzName: String,
        modulePath: Path? = null,
        definitionResolver: () -> List<BeanDefinition<*>>,
        lastInStack: BeanDefinition<*>?
    ): BeanDefinition<*> {
        val candidates: List<BeanDefinition<*>> = (if (lastInStack != null) {
            val found = definitionResolver()
            val filteredByVisibility = found.filter { lastInStack.canSee(it) }
            if (found.isNotEmpty() && filteredByVisibility.isEmpty()) {
                throw NotVisibleException("Can't resolve '$clazzName' - Definition is not visible from last definition : $lastInStack")
            }
            filteredByVisibility
        } else {
            definitionResolver()
        }).distinct()

        return if (candidates.size == 1) {
            val candidate = candidates.first()
            if (modulePath != null && !candidate.path.isVisible(modulePath)) {
                throw NotVisibleException("Can't resolve '$clazzName' - Definition is not visible from module '$modulePath'")
            }
            candidate
        } else {
            when {
                candidates.isEmpty() -> throw NoBeanDefFoundException("No definition found to resolve type '$clazzName'. Check your module definition")
                else -> throw DependencyResolutionException(
                    "Multiple definitions found to resolve type '$clazzName' - Koin can't choose between :\n\t${candidates.joinToString(
                        "\n\t"
                    )}\n\tCheck your modules definition or use name attribute to resolve components."
                )
            }
        }
    }

    /**
     * Clear resources
     */
    fun clear() {
        definitions.clear()
    }

}