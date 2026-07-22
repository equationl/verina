package lv.aki.verina.engine

object TemplateEngine {

    private val VARIABLE_PATTERN = Regex("""\{\{(\w+)\}\}""")

    fun render(template: String, variables: Map<String, String>): String {
        return VARIABLE_PATTERN.replace(template) { match ->
            val key = match.groupValues[1]
            variables[key] ?: match.value
        }
    }

    fun renderHeaders(headersJson: String, variables: Map<String, String>): String {
        return render(headersJson, variables)
    }
}
