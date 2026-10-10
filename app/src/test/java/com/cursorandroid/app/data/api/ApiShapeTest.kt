package com.cursorandroid.app.data.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiShapeTest {
    private val json = Json

    @Test
    fun listsKeyPathsAndModelValuesOnly() {
        val body = """{"items":[{"id":"bc-1","status":"ACTIVE","model":{"id":"gpt-5"},"modelName":"GPT"}],"nextCursor":null}"""
        val line = ApiShape.describe("GET /v1/agents -> 200", body, json)!!

        assertTrue("items[].status" in line)
        assertTrue("items[].model.id" in line)
        assertTrue("items[].modelName=GPT" in line)
        assertTrue("items[].model.id=gpt-5" in line)
    }

    @Test
    fun neverEchoesMessageText() {
        val body = """{"messages":[{"id":"m1","type":"assistant_message","text":"secret prompt body"}]}"""
        val line = ApiShape.describe("GET /v0/agents/x/conversation -> 200", body, json)!!

        assertTrue("messages[].text" in line)
        assertFalse("secret prompt body" in line)
    }

    @Test
    fun nonJsonIsIgnored() {
        assertNull(ApiShape.describe("x", "not json", json))
        assertEquals(emptyMap<String, String>(), ApiShape.modelValues(json.parseToJsonElement("{\"a\":1}")))
    }
}
