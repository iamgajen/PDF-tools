package android.net

import android.os.Parcel

class TestUri(private val uriString: String = "content://test/file.pdf") : Uri() {
    override fun toString(): String = uriString
    override fun isHierarchical(): Boolean = true
    override fun isOpaque(): Boolean = false
    override fun isRelative(): Boolean = false
    override fun getScheme(): String = "content"
    override fun getSchemeSpecificPart(): String = "//test"
    override fun getEncodedSchemeSpecificPart(): String = "//test"
    override fun getAuthority(): String = "test"
    override fun getEncodedAuthority(): String = "test"
    override fun getUserInfo(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getHost(): String = "test"
    override fun getPort(): Int = -1
    override fun getPath(): String = ""
    override fun getEncodedPath(): String = ""
    override fun getQuery(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getFragment(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getPathSegments(): List<String> = emptyList()
    override fun getLastPathSegment(): String? = null
    override fun equals(other: Any?): Boolean = other is TestUri && uriString == other.uriString
    override fun hashCode(): Int = uriString.hashCode()
    override fun compareTo(other: Uri?): Int = uriString.compareTo(other?.toString() ?: "")
    override fun buildUpon(): Builder? = null
    override fun writeToParcel(dest: Parcel, flags: Int) {}
    override fun describeContents(): Int = 0
}
