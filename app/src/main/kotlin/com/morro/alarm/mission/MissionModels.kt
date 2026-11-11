package com.morro.alarm.mission

import android.content.Context

/**
 * Types of wake-up verification missions supported by Morro.
 */
enum class MissionType {
    VOICE_COMMITMENT,
    QR_CODE,
    PHOTO_MISSION,
    VOICE_MEMO,
    QR_BARCODE,
    PHOTO_OBJECT
}

/**
 * A target household item for the Photo Object mission.
 */
data class PhotoTargetItem(
    val id: String,
    val displayName: String,
    val mlKitSynonyms: List<String>
)

/**
 * Pool of predefined household items recognizable by ML Kit Image Labeling.
 */
object PhotoTargetPool {
    val ITEMS: List<PhotoTargetItem> = listOf(
        PhotoTargetItem("cup", "Cup / Mug", listOf("cup", "mug", "coffee cup", "tableware", "drinkware")),
        PhotoTargetItem("toothbrush", "Toothbrush", listOf("toothbrush", "brush", "personal care", "toiletry")),
        PhotoTargetItem("sink", "Sink / Faucet", listOf("sink", "faucet", "tap", "plumbing fixture", "washbasin")),
        PhotoTargetItem("chair", "Chair", listOf("chair", "seat", "furniture", "stool", "armchair")),
        PhotoTargetItem("keyboard", "Keyboard", listOf("keyboard", "computer keyboard", "space bar", "input device", "office equipment")),
        PhotoTargetItem("mouse", "Computer Mouse", listOf("mouse", "computer mouse", "peripheral", "electronic device")),
        PhotoTargetItem("shoe", "Shoe / Sneaker", listOf("shoe", "footwear", "sneaker", "boot", "sandal")),
        PhotoTargetItem("book", "Book", listOf("book", "publication", "notebook", "text", "paper", "novel")),
        PhotoTargetItem("bottle", "Water Bottle", listOf("bottle", "water bottle", "plastic bottle", "drinkware", "flask")),
        PhotoTargetItem("glasses", "Glasses / Eyewear", listOf("glasses", "eyewear", "sunglasses", "goggles", "vision care")),
        PhotoTargetItem("spoon", "Spoon", listOf("spoon", "tableware", "cutlery", "utensil", "silverware")),
        PhotoTargetItem("fork", "Fork", listOf("fork", "cutlery", "tableware", "utensil", "silverware")),
        PhotoTargetItem("plant", "Houseplant", listOf("plant", "houseplant", "flowerpot", "leaf", "botany", "vegetation")),
        PhotoTargetItem("pillow", "Pillow / Cushion", listOf("pillow", "cushion", "bedding", "linens", "throw pillow")),
        PhotoTargetItem("watch", "Wristwatch", listOf("watch", "clock", "analog watch", "strap", "timepiece")),
        PhotoTargetItem("backpack", "Backpack / Bag", listOf("bag", "backpack", "luggage", "handbag", "tote")),
        PhotoTargetItem("door", "Door / Doorhandle", listOf("door", "handle", "doorknob", "wood", "lock")),
        PhotoTargetItem("monitor", "Monitor / TV", listOf("monitor", "television", "display", "screen", "flat panel")),
        PhotoTargetItem("laptop", "Laptop", listOf("laptop", "computer", "netbook", "personal computer", "gadget")),
        PhotoTargetItem("remote", "Remote Control", listOf("remote", "remote control", "electronics", "button")),
        PhotoTargetItem("scissors", "Scissors", listOf("scissors", "tool", "blade", "shears", "office supplies")),
        PhotoTargetItem("towel", "Towel", listOf("towel", "textile", "fabric", "linens", "cloth")),
        PhotoTargetItem("desk", "Desk / Table", listOf("table", "desk", "furniture", "countertop", "wood")),
        PhotoTargetItem("lamp", "Lamp / Light", listOf("lamp", "light fixture", "lighting", "lampshade", "bulb")),
        PhotoTargetItem("mirror", "Mirror", listOf("mirror", "reflection", "glass", "frame", "bathroom")),
        PhotoTargetItem("pen", "Pen / Pencil", listOf("pen", "pencil", "writing implement", "marker", "stationery")),
        PhotoTargetItem("headphones", "Headphones / Earbuds", listOf("headphones", "audio equipment", "earbuds", "headset")),
        PhotoTargetItem("wallet", "Wallet", listOf("wallet", "leather", "coin purse", "pocketbook")),
        PhotoTargetItem("keys", "Keys / Keychain", listOf("key", "keychain", "metal", "hardware", "lock")),
        PhotoTargetItem("wall_clock", "Wall Clock", listOf("clock", "wall clock", "circle", "watch", "number"))
    )

    val ALL_ITEMS: List<String> = ITEMS.map { it.displayName }

    val COMMON_10_IDS: Set<String> = setOf(
        "cup", "toothbrush", "sink", "chair", "keyboard",
        "mouse", "shoe", "book", "bottle", "glasses"
    )

    val COMMON_10_ITEMS: List<String> = ITEMS.filter { it.id in COMMON_10_IDS }.map { it.displayName }

    fun getById(id: String): PhotoTargetItem? = ITEMS.find { it.id == id }
}

/**
 * SharedPreferences storage for alarm mission configurations.
 */
object MissionPrefs {
    private const val PREFS_NAME = "morro_prefs"
    private const val KEY_MISSION_TYPE = "mission_type"
    private const val KEY_REGISTERED_QR_CODE = "registered_qr_code"
    private const val KEY_SELECTED_PHOTO_ITEM_IDS = "selected_photo_item_ids"

    fun getMissionType(context: Context): MissionType {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_MISSION_TYPE, null) ?: return MissionType.VOICE_COMMITMENT
        return try {
            MissionType.valueOf(name)
        } catch (e: IllegalArgumentException) {
            MissionType.VOICE_COMMITMENT
        }
    }

    fun setMissionType(context: Context, type: MissionType) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MISSION_TYPE, type.name).apply()
    }

    fun getRegisteredQrCode(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_REGISTERED_QR_CODE, null)
    }

    fun setRegisteredQrCode(context: Context, code: String?) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (code != null) {
            prefs.edit().putString(KEY_REGISTERED_QR_CODE, code).apply()
        } else {
            prefs.edit().remove(KEY_REGISTERED_QR_CODE).apply()
        }
    }

    fun getSelectedPhotoItemIds(context: Context): Set<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_SELECTED_PHOTO_ITEM_IDS, null) ?: PhotoTargetPool.COMMON_10_IDS
    }

    fun setSelectedPhotoItemIds(context: Context, ids: Set<String>) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_SELECTED_PHOTO_ITEM_IDS, ids).apply()
    }

    fun saveMission(
        context: Context,
        missionType: MissionType,
        qrCode: String = "",
        photoItems: Set<String> = emptySet()
    ) {
        setMissionType(context, missionType)
        setRegisteredQrCode(context, qrCode.ifBlank { null })
        setSelectedPhotoItemIds(context, photoItems)
    }

    fun getQrCode(context: Context): String = getRegisteredQrCode(context) ?: ""

    fun getSelectedPhotoItems(context: Context): Set<String> = getSelectedPhotoItemIds(context)
}
