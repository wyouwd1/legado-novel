package io.legado.app

import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import org.intellij.lang.annotations.Language
import org.junit.Assert
import org.junit.Test

class AndroidJsTest {

    @Test
    fun testBookSourceJavaBridge() {
        @Language("js")
        val js = """
            Packages.java.lang.String.valueOf('42') + '|' +
                Packages.cn.hutool.core.util.StrUtil.isBlank(' ')
        """.trimIndent()
        Assert.assertEquals("42|true", RhinoScriptEngine.eval(js))
    }

    @Test
    fun testMap() {
        val map = hashMapOf("id" to "3242532321")
        val bindings = ScriptBindings()
        bindings["result"] = map
        @Language("js")
        val jsMap = "$=result;id=$.id;id"
        val result = RhinoScriptEngine.eval(jsMap, bindings)
        Assert.assertEquals("3242532321", result)
        @Language("js")
        val jsMap1 = """result.get("id")"""
        val result1 = RhinoScriptEngine.eval(jsMap1, bindings)
        Assert.assertEquals("3242532321", result1)
    }

}