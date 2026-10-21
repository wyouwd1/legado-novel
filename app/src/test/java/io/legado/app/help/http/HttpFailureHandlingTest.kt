package io.legado.app.help.http

import java.io.IOException
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.net.SocketTimeoutException
import java.util.zip.GZIPOutputStream
import kotlin.io.path.createTempDirectory
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

/**
 * 网络失败不是“异常分支里的注释”，而是书源/图源每天都会走的主路径：
 * - 底层 IOException 必须原样上抛，让调用方按超时/断连分类重试；
 * - 非 IO 异常必须包成 IOException，不能漏出 RuntimeException 把下载协程直接打死；
 * - gzip/deflate 响应被截断时必须抛 IOException，而不是返回半个正文或 OOM。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class HttpFailureHandlingTest {

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
    }

    @Test
    fun `io failure is rethrown unchanged for retry classification`() {
        val failure = SocketTimeoutException("read timed out")
        val chain = fakeChain { throw failure }
        try {
            OkHttpExceptionInterceptor.intercept(chain)
            fail("expected SocketTimeoutException")
        } catch (actual: IOException) {
            assertSame(failure, actual)
        }
    }

    @Test
    fun `non io failure is wrapped as io failure with original cause`() {
        val failure = IllegalStateException("unexpected")
        val chain = fakeChain { throw failure }
        try {
            OkHttpExceptionInterceptor.intercept(chain)
            fail("expected IOException")
        } catch (actual: IOException) {
            assertSame(failure, actual.cause)
        }
    }

    @Test
    fun `valid gzip body is transparently decompressed and length header removed`() {
        val plain = "第一章正文".repeat(32).toByteArray()
        val gzip = gzip(plain)
        val response = Response.Builder()
            .request(request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("Content-Encoding", "gzip")
            .header("Content-Length", gzip.size.toString())
            .body(gzip.toResponseBody("text/plain".toMediaType()))
            .build()

        val decompressed = DecompressInterceptor.intercept(fakeChain { response })

        assertEquals(plain.decodeToString(), decompressed.body.string())
        assertEquals(null, decompressed.header("Content-Encoding"))
        assertEquals(null, decompressed.header("Content-Length"))
    }

    @Test
    fun `truncated gzip body fails as io failure instead of yielding half text`() {
        val plain = ByteArray(4096) { 'a'.code.toByte() }
        val gzip = gzip(plain)
        val truncated = gzip.copyOfRange(0, (gzip.size / 2).coerceAtLeast(1))
        val response = Response.Builder()
            .request(request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("Content-Encoding", "gzip")
            .body(truncated.toResponseBody("text/plain".toMediaType()))
            .build()

        val decompressed = DecompressInterceptor.intercept(fakeChain { response })
        try {
            decompressed.body.string()
            fail("expected truncated gzip to fail")
        } catch (_: IOException) {
            // IOException is the contract; callers classify it and retry.
        }
    }

    @Test
    fun `empty gzip body fails as io failure instead of empty success`() {
        val response = Response.Builder()
            .request(request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("Content-Encoding", "gzip")
            .body(ByteArray(0).toResponseBody("text/plain".toMediaType()))
            .build()

        try {
            val decompressed = DecompressInterceptor.intercept(fakeChain { response })
            decompressed.body.string()
            fail("expected empty gzip body to fail")
        } catch (_: IOException) {
            // Empty gzip is malformed, not a valid zero-length text response.
        }
    }

    private fun request(): Request = Request.Builder().url("https://example.com/chapter/1").build()

    private fun gzip(plain: ByteArray): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(plain) }
        return output.toByteArray()
    }

    private fun fakeChain(proceed: (Request) -> Response): Interceptor.Chain {
        val request = request()
        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "request" -> request
                "proceed" -> proceed(args!![0] as Request)
                "toString" -> "FakeChain"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args!![0]
                else -> defaultValue(method)
            }
        }
        return Proxy.newProxyInstance(
            Interceptor.Chain::class.java.classLoader,
            arrayOf(Interceptor.Chain::class.java),
            handler,
        ) as Interceptor.Chain
    }

    private fun defaultValue(method: Method): Any? = when (method.returnType) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        else -> null
    }
}
