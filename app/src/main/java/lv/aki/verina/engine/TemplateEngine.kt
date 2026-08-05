package lv.aki.verina.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

object TemplateEngine {

    private val VARIABLE_PATTERN = Regex("""\{\{(\w+)\}\}""")
    private val JSON_NUMBER_PATTERN = Regex("""-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?""")
    private val json = Json

    fun render(template: String, variables: Map<String, String>): String {
        return VARIABLE_PATTERN.replace(template) { match ->
            val key = match.groupValues[1]
            variables[key] ?: match.value
        }
    }

    fun renderHeaders(headersJson: String, variables: Map<String, String>): String {
        return if (headersJson.isBlank()) "{}" else renderJson(headersJson, variables)
    }

    /**
     * Parses a JSON template, replaces placeholders in the parsed tree, and
     * serializes the resulting tree with kotlinx.serialization. JSON escaping
     * is deliberately delegated to the library rather than implemented here.
     */
    fun renderJson(template: String, variables: Map<String, String>): String {
        if (template.isBlank()) {
            throw IllegalArgumentException("JSON 内容不能为空")
        }

        val placeholders = mutableListOf<Placeholder>()
        val markedTemplate = markPlaceholders(template, placeholders)
        val parsed = try {
            json.parseToJsonElement(markedTemplate)
        } catch (e: Exception) {
            throw IllegalArgumentException("JSON 模板无效: ${e.message}", e)
        }

        val resolvedValues = variables.mapValues { (_, value) -> parseVariableValue(value) }
        return replacePlaceholders(parsed, placeholders, variables, resolvedValues).toString()
    }

    /**
     * Makes placeholders parseable without touching the values that will be
     * inserted later. A placeholder embedded in a JSON string stays text;
     * one used as a standalone JSON value is temporarily represented as a
     * JSON string and can later recover its original JSON type when possible.
     */
    private fun markPlaceholders(
        template: String,
        placeholders: MutableList<Placeholder>
    ): String {
        val result = StringBuilder(template.length)
        var inString = false
        var index = 0

        while (index < template.length) {
            val current = template[index]

            if (current == '\\' && inString) {
                result.append(current)
                if (index + 1 < template.length) {
                    result.append(template[index + 1])
                    index += 2
                } else {
                    index++
                }
                continue
            }

            if (current == '"') {
                inString = !inString
                result.append(current)
                index++
                continue
            }

            val match = VARIABLE_PATTERN.matchAt(template, index)
            if (match != null) {
                val token = createToken(template, placeholders.size)
                placeholders += Placeholder(
                    token = token,
                    key = match.groupValues[1],
                    standalone = !inString
                )
                if (inString) {
                    result.append(token)
                } else {
                    // The token is generated from ASCII characters only, so
                    // this is a safe temporary JSON string literal.
                    result.append('"').append(token).append('"')
                }
                index = match.range.last + 1
                continue
            }

            result.append(current)
            index++
        }

        return result.toString()
    }

    private fun createToken(template: String, index: Int): String {
        var suffix = 0
        var token: String
        do {
            token = "__VERINA_TEMPLATE_${index}_${suffix}__"
            suffix++
        } while (template.contains(token))
        return token
    }

    private fun parseVariableValue(value: String): JsonElement {
        val trimmed = value.trim()
        val canBeJsonValue = trimmed == "null" ||
            trimmed == "true" ||
            trimmed == "false" ||
            JSON_NUMBER_PATTERN.matches(trimmed) ||
            (trimmed.startsWith('{') && trimmed.endsWith('}')) ||
            (trimmed.startsWith('[') && trimmed.endsWith(']')) ||
            (trimmed.startsWith('"') && trimmed.endsWith('"'))

        if (!canBeJsonValue) return JsonPrimitive(value)

        return try {
            val parsed = json.parseToJsonElement(trimmed)
            if (isStrictJsonValue(parsed)) parsed else JsonPrimitive(value)
        } catch (_: Exception) {
            JsonPrimitive(value)
        }
    }

    private fun isStrictJsonValue(element: JsonElement): Boolean {
        return when (element) {
            JsonNull -> true
            is JsonObject -> element.values.all(::isStrictJsonValue)
            is JsonArray -> element.all(::isStrictJsonValue)
            is JsonPrimitive -> element.isString ||
                element.content == "true" ||
                element.content == "false" ||
                JSON_NUMBER_PATTERN.matches(element.content)
        }
    }

    private fun replacePlaceholders(
        element: JsonElement,
        placeholders: List<Placeholder>,
        rawValues: Map<String, String>,
        values: Map<String, JsonElement>
    ): JsonElement {
        return when (element) {
            is JsonObject -> buildJsonObject {
                element.forEach { (key, child) ->
                    put(
                        replaceText(key, placeholders, rawValues),
                        replacePlaceholders(child, placeholders, rawValues, values)
                    )
                }
            }

            is JsonArray -> buildJsonArray {
                element.forEach { child ->
                    add(replacePlaceholders(child, placeholders, rawValues, values))
                }
            }

            is JsonPrimitive -> replacePrimitive(element, placeholders, rawValues, values)
        }
    }

    private fun replacePrimitive(
        element: JsonPrimitive,
        placeholders: List<Placeholder>,
        rawValues: Map<String, String>,
        values: Map<String, JsonElement>
    ): JsonElement {
        if (!element.isString) return element

        val exactPlaceholder = placeholders.firstOrNull {
            it.standalone && element.content == it.token
        }
        if (exactPlaceholder != null) {
            return values[exactPlaceholder.key] ?: JsonPrimitive("{{${exactPlaceholder.key}}}")
        }

        return JsonPrimitive(replaceText(element.content, placeholders, rawValues))
    }

    private fun replaceText(
        text: String,
        placeholders: List<Placeholder>,
        values: Map<String, String>
    ): String {
        val embeddedPlaceholders = placeholders.filter { !it.standalone }
        if (embeddedPlaceholders.isEmpty()) return text

        val byToken = embeddedPlaceholders.associateBy { it.token }
        val tokenPattern = Regex(byToken.keys.joinToString("|") { Regex.escape(it) })
        return tokenPattern.replace(text) { match ->
            val placeholder = byToken.getValue(match.value)
            values[placeholder.key] ?: "{{${placeholder.key}}}"
        }
    }

    private data class Placeholder(
        val token: String,
        val key: String,
        val standalone: Boolean
    )
}
