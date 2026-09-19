package top.dingfengbo.mylibrary.data.net

import java.lang.reflect.Type
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.PUT
import top.dingfengbo.mylibrary.api.infrastructure.Serializer

/**
 * Create and update bodies disagree about nulls, and the generated client has a single Json for both.
 *
 * The create models declare non-optional fields with defaults, so an explicit null for one of them
 * comes back as a 422 ("Input should be a valid string") — nulls have to be dropped. The update
 * routers read `model_dump(exclude_unset=True)` and `setattr` the result, so an absent key means
 * "leave unchanged" while an explicit null clears the column: every field the edit form leaves empty
 * has to reach the server as a null, otherwise clearing it is silently ignored. The web front end
 * does the same — it strips nulls for POST only.
 *
 * Both shapes are built here rather than inherited from the app's shared Json, so neither depends on
 * whether the app has started up yet.
 */
private val createJson = Json(Serializer.kotlinxSerializationJson) { explicitNulls = false }

private val updateJson = Json(Serializer.kotlinxSerializationJson) { explicitNulls = true }

/**
 * Response-shaped fields the update models carry but no form ever sets. `updated_at` would blank the
 * column (NOT NULL in a freshly created user database), so the key is dropped instead of encoded.
 */
private val serverManagedFields = setOf("updated_at")

private val updateBody =
    object : StringFormat {
        override val serializersModule = updateJson.serializersModule

        override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {
            val body = updateJson.encodeToJsonElement(serializer, value)
            if (body !is JsonObject) return body.toString()
            return JsonObject(body.filterKeys { it !in serverManagedFields }).toString()
        }

        override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T =
            updateJson.decodeFromString(deserializer, string)
    }

/** Retrofit passes the method's annotations here, which is what tells a PUT body from a POST one. */
private object RequestBodyFactory : Converter.Factory() {
    private val contentType = "application/json".toMediaType()
    private val create = createJson.asConverterFactory(contentType)
    private val update = updateBody.asConverterFactory(contentType)

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<Annotation>,
        methodAnnotations: Array<Annotation>,
        retrofit: Retrofit,
    ): Converter<*, RequestBody>? =
        (if (methodAnnotations.any { it is PUT }) update else create)
            .requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit)
}

/** What the generated ApiClient has to be built with for the bodies above to reach the wire. */
val apiConverterFactories: List<Converter.Factory> =
    listOf(
        ScalarsConverterFactory.create(),
        RequestBodyFactory,
        Serializer.kotlinxSerializationJson.asConverterFactory("application/json".toMediaType()),
    )
