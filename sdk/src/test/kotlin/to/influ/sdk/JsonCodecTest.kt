package to.influ.sdk

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import to.influ.sdk.internal.JsonCodec

/** JVM unit tests for the wire ↔ model mapping (the contract fixtures, replayed). */
class JsonCodecTest {

    @Test
    fun validateCode_mapsNotFound() {
        val j = JSONObject(
            """{"valid":false,"error":"Code not found or inactive","error_code":"CODE_NOT_FOUND"}""",
        )
        val r = JsonCodec.toCodeValidationResult(j)
        assertEquals(false, r.valid)
        assertEquals(CodeErrorCode.CODE_NOT_FOUND, r.errorCode)
    }

    @Test
    fun validateCode_mapsValidWithCampaignAndInfluencer() {
        val j = JSONObject(
            """{"valid":true,"code":"FITGURU30",
                "campaign":{"id":"c1","name":"Summer","commission_percentage":30.0,"campaign_type":"public"},
                "influencer":{"name":"Fit Guru","social_handle":"@fitguru","follower_count":120000}}""",
        )
        val r = JsonCodec.toCodeValidationResult(j)
        assertTrue(r.valid)
        assertEquals("Summer", r.campaign?.name)
        assertEquals(30.0, r.campaign?.commissionPercentage!!, 0.001)
        assertEquals("@fitguru", r.influencer?.socialHandle)
        assertEquals(120000, r.influencer?.followerCount)
    }

    @Test
    fun campaigns_mapsBareArray() {
        val arr = JSONArray("""[{"id":"1","name":"A","commission_percentage":25.0}]""")
        val list = JsonCodec.toCampaignList(arr)
        assertEquals(1, list.size)
        assertEquals("A", list[0].name)
        assertEquals(25.0, list[0].commissionPercentage!!, 0.001)
    }

    @Test
    fun purchase_mapsValidatedAsString() {
        val j = JSONObject(
            """{"success":true,"validated":"google","environment":"PRODUCTION","event_type":"INITIAL_PURCHASE"}""",
        )
        val r = JsonCodec.toPurchaseResult(j)
        assertTrue(r.success)
        assertEquals("google", r.validated) // STRING, not boolean
        assertEquals("INITIAL_PURCHASE", r.eventType)
    }

    @Test
    fun attributionBlob_roundTripsCamelCase() {
        val a = AttributionResult(
            attributed = true, referralCode = "X", attributionMethod = "manual_entry",
            clickedAt = "2026-06-14T00:00:00.000Z", message = "m",
        )
        val json = JsonCodec.attributionToJson(a)
        // camelCase keys — byte-compatible with the RN/Flutter stored blob
        assertEquals("X", json.getString("referralCode"))
        assertEquals("manual_entry", json.getString("attributionMethod"))
        val back = JsonCodec.attributionFromJson(json)
        assertTrue(back.attributed)
        assertEquals("X", back.referralCode)
        assertNull(JsonCodec.attributionFromJson(JSONObject("""{"attributed":false}""")).referralCode)
    }
}
