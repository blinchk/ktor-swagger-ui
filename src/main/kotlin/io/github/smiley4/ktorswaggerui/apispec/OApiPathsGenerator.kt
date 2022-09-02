package io.github.smiley4.ktorswaggerui.apispec

import io.github.smiley4.ktorswaggerui.SwaggerUIPluginConfig
import io.github.smiley4.ktorswaggerui.documentation.DocumentedRouteSelector
import io.github.smiley4.ktorswaggerui.documentation.RouteDocumentation
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.plugin
import io.ktor.server.auth.AuthenticationRouteSelector
import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.RootRouteSelector
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.TrailingSlashRouteSelector
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import mu.KotlinLogging

/**
 * Generator for the OpenAPI Paths
 */
class OApiPathsGenerator {

    private val logger = KotlinLogging.logger {}


    /**
     * Generate the OpenAPI Paths from the given config and application
     */
    fun generate(config: SwaggerUIPluginConfig, application: Application): Paths {
        return Paths().apply {
            collectRoutes(application, config.getSwaggerUI().swaggerUrl, config.getSwaggerUI().forwardRoot)
                .onEach { logger.debug("Configure path: ${it.method.value} ${it.path}") }
                .map {
                    OApiPathGenerator().generate(
                        it,
                        config.getDefaultUnauthorizedResponse(),
                        config.defaultSecuritySchemeName,
                        config.automaticTagGenerator
                    )
                }
                .forEach { merge(this, it.first, it.second) }
        }
    }

    private fun merge(paths: Paths, name: String, item: PathItem) {
        paths[name]
            ?.let {
                it.get = if (item.get != null) item.get else it.get
                it.put = if (item.put != null) item.put else it.put
                it.post = if (item.post != null) item.post else it.post
                it.delete = if (item.delete != null) item.delete else it.delete
                it.options = if (item.options != null) item.options else it.options
                it.head = if (item.head != null) item.head else it.head
                it.patch = if (item.patch != null) item.patch else it.patch
                it.trace = if (item.trace != null) item.trace else it.trace
            }
            ?: paths.addPathItem(name, item)
    }

    private fun collectRoutes(application: Application, swaggerUrl: String, forwardRoot: Boolean): List<RouteMeta> {
        return allRoutes(application.plugin(Routing))
            .asSequence()
            .map { route ->
                RouteMeta(
                    route = route,
                    method = getMethod(route),
                    path = getPath(route),
                    documentation = getDocumentation(route),
                    protected = isProtected(route)
                )
            }
            .filter { removeLeadingSlash(it.path) != removeLeadingSlash(swaggerUrl) }
            .filter { removeLeadingSlash(it.path) != removeLeadingSlash("$swaggerUrl/{filename}") }
            .filter { removeLeadingSlash(it.path) != removeLeadingSlash("$swaggerUrl/schemas/{schemaname}") }
            .filter { !forwardRoot || it.path != "/" }
            .toList()
    }

    private fun removeLeadingSlash(str: String): String {
        return if (str.startsWith("/")) {
            str.substring(1)
        } else {
            str
        }
    }

    private fun getDocumentation(route: Route): RouteDocumentation {
        return when (val selector = route.selector) {
            is DocumentedRouteSelector -> selector.documentation
            else -> route.parent?.let { getDocumentation(it) } ?: RouteDocumentation()
        }
    }

    private fun getMethod(route: Route): HttpMethod {
        return (route.selector as HttpMethodRouteSelector).method
    }

    private fun getPath(route: Route): String {
        return when (route.selector) {
            is TrailingSlashRouteSelector -> "/"
            is RootRouteSelector -> ""
            is DocumentedRouteSelector -> route.parent?.let { getPath(it) } ?: ""
            is HttpMethodRouteSelector -> route.parent?.let { getPath(it) } ?: ""
            is AuthenticationRouteSelector -> route.parent?.let { getPath(it) } ?: ""
            else -> (route.parent?.let { getPath(it) } ?: "") + "/" + route.selector.toString()
        }
    }

    private fun isProtected(route: Route): Boolean {
        return when (route.selector) {
            is TrailingSlashRouteSelector -> false
            is RootRouteSelector -> false
            is DocumentedRouteSelector -> route.parent?.let { isProtected(it) } ?: false
            is HttpMethodRouteSelector -> route.parent?.let { isProtected(it) } ?: false
            is AuthenticationRouteSelector -> true
            else -> route.parent?.let { isProtected(it) } ?: false
        }
    }

    private fun allRoutes(root: Route): List<Route> {
        return (listOf(root) + root.children.flatMap { allRoutes(it) })
            .filter { it.selector is HttpMethodRouteSelector }
    }

}