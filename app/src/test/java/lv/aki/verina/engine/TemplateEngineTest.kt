package lv.aki.verina.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateEngineTest {

    @Test
    fun renderJsonSerializesNotificationTextWithSpecialCharacters() {
        val rendered = TemplateEngine.renderJson(
            """{"text":"{{text}}"}""",
            mapOf("text" to "第一行\n第二行\"引号\"\\路径\t控制符\u0001")
        )

        assertEquals(
            """{"text":"第一行\n第二行\"引号\"\\路径\t控制符\u0001"}""",
            rendered
        )
    }

    @Test
    fun renderJsonSupportsNestedValuesAndStandaloneJsonTypes() {
        val rendered = TemplateEngine.renderJson(
            """{"payload":{"level":{{level}},"items":[{{item}}]}}""",
            mapOf("level" to "20", "item" to "短信")
        )

        assertEquals(
            """{"payload":{"level":20,"items":["短信"]}}""",
            rendered
        )
    }

    @Test
    fun renderJsonSerializesLiteralNewlineInTemplate() {
        val rendered = TemplateEngine.renderJson(
            """{"text":"第一行
            第二行"}""",
            emptyMap()
        )

        assertEquals("""{"text":"第一行\n            第二行"}""", rendered)
    }
}
